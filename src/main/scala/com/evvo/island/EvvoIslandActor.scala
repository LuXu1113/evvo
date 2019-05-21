package com.evvo.island

import java.util.Calendar

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, PoisonPill, Props}
import akka.event.{LoggingAdapter, LoggingReceive}
import akka.pattern.ask
import akka.util.Timeout
import com.evvo._
import com.evvo.agent._
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberUp
import com.evvo.island.EvvoIslandActor._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import com.evvo.island.population.{Maximize, Minimize, Objective, Population, TObjective, TParetoFrontier}


// for messages
import com.evvo.island.EvvoIslandActor._

/**
  * A single-island evolutionary system, which will run on one computer (although on multiple
  * CPU cores). Because it is an Akka actor, generally people will use SingleIslandEvvo.Wrapped
  * to use it in a type-safe way, instead of throwing messages.
  */
class EvvoIslandActor[Sol]
(
  creators: Vector[TCreatorFunc[Sol]],
  mutators: Vector[TMutatorFunc[Sol]],
  deletors: Vector[TDeletorFunc[Sol]],
  fitnesses: Vector[TObjective[Sol]]
)
  extends Actor with TEvolutionaryProcess[Sol] with ActorLogging {
  implicit val logger: LoggingAdapter = log

  val island = new EvvoIsland[Sol](creators, mutators, deletors, fitnesses)

  override def receive: Receive = LoggingReceive({
    case Run(t) => sender ! this.runBlocking(t)
    case GetParetoFrontier => sender ! this.currentParetoFrontier()
    case Emigrate(solutions: Seq[Sol]) => this.emigrate(solutions)
  })


  override def runBlocking(terminationCriteria: TTerminationCriteria): Unit = {
    island.runBlocking(terminationCriteria)
  }

  override def runAsync(terminationCriteria: TTerminationCriteria): Future[Unit] = {
    island.runAsync(terminationCriteria)
  }

  override def currentParetoFrontier(): TParetoFrontier[Sol] = {
    island.currentParetoFrontier()
  }

  override def emigrate(solutions: Seq[Sol]): Unit = {
    island.emigrate(solutions)
  }

  override def poisonPill(): Unit = {
    self ! PoisonPill
  }


}

object EvvoIslandActor {
  /**
    * @param creators  the functions to be used for creating new solutions.
    * @param mutators  the functions to be used for creating new solutions from current solutions.
    * @param deletors  the functions to be used for deciding which solutions to delete.
    * @param fitnesses the objective functions to maximize.
    */
  def from[Sol](creators: TraversableOnce[TCreatorFunc[Sol]],
                mutators: TraversableOnce[TMutatorFunc[Sol]],
                deletors: TraversableOnce[TDeletorFunc[Sol]],
                fitnesses: TraversableOnce[TObjective[Sol]])
               (implicit system: ActorSystem)
  : TEvolutionaryProcess[Sol] = {
    // TODO validate that there is at least one of each creator/mutator/deletors/fitness

    val props = Props(new EvvoIslandActor[Sol](
      creators.toVector,
      mutators.toVector,
      deletors.toVector,
      fitnesses.toVector))
    EvvoIslandActor.Wrapper[Sol](system.actorOf(props, s"EvvoIsland_${java.util.UUID.randomUUID()}"))
  }

  def builder[Sol](): EvvoIslandBuilder[Sol] = EvvoIslandBuilder[Sol]()

  /**
    * This is a wrapper for ActorRefs containing SingleIslandEvvo actors, serving as an
    * adapter to the TIsland interface
    *
    * @param ref the reference to wrap
    */
  case class Wrapper[Sol](ref: ActorRef) extends TEvolutionaryProcess[Sol] {
    implicit val timeout: Timeout = Timeout(5.days)

    override def runBlocking(terminationCriteria: TTerminationCriteria): Unit = {
      Await.result(this.runAsync(terminationCriteria), Duration.Inf)
    }

    override def runAsync(terminationCriteria: TTerminationCriteria): Future[Unit] = {
      (ref ? Run(terminationCriteria)).asInstanceOf[Future[Unit]]
    }

    override def currentParetoFrontier(): TParetoFrontier[Sol] = {
      Await.result(ref ? GetParetoFrontier, Duration.Inf).asInstanceOf[TParetoFrontier[Sol]]
    }

    override def emigrate(solutions: Seq[Sol]): Unit = {
      ref ! Emigrate(solutions)
    }

    override def poisonPill(): Unit = {
      ref ! PoisonPill
    }
  }

  case class Run(terminationCriteria: TTerminationCriteria)

  case object GetParetoFrontier

  case class Emigrate[Sol](solutions: Seq[Sol])

}

/**
  * @param creators   the functions to be used for creating new solutions.
  * @param mutators   the functions to be used for creating new solutions from current solutions.
  * @param deletors   the functions to be used for deciding which solutions to delete.
  * @param objectives the objective functions to maximize.
  */
case class EvvoIslandBuilder[Sol]
(
  creators: Set[TCreatorFunc[Sol]] = Set[TCreatorFunc[Sol]](),
  mutators: Set[TMutatorFunc[Sol]] = Set[TMutatorFunc[Sol]](),
  deletors: Set[TDeletorFunc[Sol]] = Set[TDeletorFunc[Sol]](),
  objectives: Set[TObjective[Sol]] = Set[TObjective[Sol]]()
) {
  def addCreatorFromFunction(creatorFunc: CreatorFunctionType[Sol]): EvvoIslandBuilder[Sol] = {
    this.copy(creators = creators + CreatorFunc(creatorFunc, creatorFunc.toString))
  }

  def addCreator(creatorFunc: TCreatorFunc[Sol]): EvvoIslandBuilder[Sol] = {
    this.copy(creators = creators + creatorFunc)
  }

  def addMutatorFromFunction(mutatorFunc: MutatorFunctionType[Sol]): EvvoIslandBuilder[Sol] = {
    this.copy(mutators = mutators + MutatorFunc(mutatorFunc, mutatorFunc.toString))
  }

  def addMutator(mutatorFunc: TMutatorFunc[Sol]): EvvoIslandBuilder[Sol] = {
    this.copy(mutators = mutators + mutatorFunc)
  }

  def addDeletorFromFunction(deletorFunc: DeletorFunctionType[Sol]): EvvoIslandBuilder[Sol] = {
    this.copy(deletors = deletors + DeletorFunc(deletorFunc, deletorFunc.toString))
  }

  def addDeletor(deletorFunc: TDeletorFunc[Sol]): EvvoIslandBuilder[Sol] = {
    this.copy(deletors = deletors + deletorFunc)
  }

  // TODO this calling API is dangerous because it assumes minimization. it should be removed,
  //      but this will require refactoring across examples
  def addObjective(objective: ObjectiveFunctionType[Sol], name: String = "")
  : EvvoIslandBuilder[Sol] = {
    val realName = if (name == "") objective.toString() else name
    this.copy(objectives = objectives + Objective(objective, realName, Minimize))
  }

  def addObjective(objective: Objective[Sol])
  : EvvoIslandBuilder[Sol] = {
    this.copy(objectives = objectives + objective)
  }

  def toProps()(implicit system: ActorSystem): Props = {
    Props(new EvvoIslandActor[Sol](
      creators.toVector,
      mutators.toVector,
      deletors.toVector,
      objectives.toVector))
  }

  def buildLocalEvvo(): TEvolutionaryProcess[Sol] = {
    new LocalEvvoIsland[Sol](
      creators.toVector,
      mutators.toVector,
      deletors.toVector,
      objectives.toVector)
  }
}
