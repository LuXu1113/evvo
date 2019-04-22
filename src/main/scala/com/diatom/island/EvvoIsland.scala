package com.diatom.island

import java.util.Calendar

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import com.diatom.{Population, _}
import com.diatom.agent._
import com.diatom.agent.func._
import com.typesafe.config.ConfigFactory
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * A single-island evolutionary system, which will run on one computer (although on multiple
  * CPU cores). Because it is an Akka actor, generally people will use SingleIslandEvvo.Wrapped
  * to use it in a type-safe way, instead of throwing messages.
  */
class EvvoIsland[Sol]
(
  creators: Vector[TCreatorFunc[Sol]],
  mutators: Vector[TMutatorFunc[Sol]],
  deletors: Vector[TDeletorFunc[Sol]],
  fitnesses: Vector[TFitnessFunc[Sol]]
) extends Actor with TEvolutionaryProcess[Sol] with ActorLogging {
  // TODO rename this to Island? No need to have special case for 1-node system

  // FIXME use akka ActorLogging logger in everywhere
  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  private val pop = Population(fitnesses)
  private val creatorAgents = creators.map(c => CreatorAgent(c, pop))
  private val mutatorAgents = mutators.map(m => MutatorAgent(m, pop))
  private val deletorAgents = deletors.map(d => DeletorAgent(d, pop))


  //   TODO should be able to pass configurations, have multiple logging environments
  private val config = ConfigFactory.parseString(
    """
      |akka {
      |  loggers = ["akka.event.slf4j.Slf4jLogger"]
      |  loglevel = "INFO"
      |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
      |  actor {
      |    debug {
      |      receive = true
      |    }
      |  }
      |}
    """.stripMargin)
  implicit val system: ActorSystem = ActorSystem("evvo", config)

  // for messages
  import com.diatom.island.EvvoIsland._

  override def receive: Receive = LoggingReceive({
    case Run(t) => sender ! this.run(t)
    case GetParetoFrontier => sender ! this.currentParetoFrontier()
  })

  def run(terminationCriteria: TTerminationCriteria): TEvolutionaryProcess[Sol] = {
    log.info(s"Island running with terminationCriteria=${terminationCriteria}")

    // TODO can we put all of these in some combined pool? don't like having to manage each
    creatorAgents.foreach(_.start())
    mutatorAgents.foreach(_.start())
    deletorAgents.foreach(_.start())

    // TODO this is not ideal. fix wait time/add features to termination criteria
    val startTime = Calendar.getInstance().toInstant.toEpochMilli

    while (startTime + terminationCriteria.time.toMillis >
      Calendar.getInstance().toInstant.toEpochMilli) {
      Thread.sleep(500)
      val pareto = pop.getParetoFrontier()
      log.info(f"pareto = ${pareto}")
    }

    log.info(f"startTime=${startTime}, now=${Calendar.getInstance().toInstant.toEpochMilli}")

    creatorAgents.foreach(_.stop())
    mutatorAgents.foreach(_.stop())
    deletorAgents.foreach(_.stop())

    this
  }

  override def currentParetoFrontier(): TParetoFrontier[Sol] = pop.getParetoFrontier()
}

object EvvoIsland {
  /**
    * @param creators  the functions to be used for creating new solutions.
    * @param mutators  the functions to be used for creating new solutions from current solutions.
    * @param deletors  the functions to be used for deciding which solutions to delete.
    * @param fitnesses the objective functions to maximize.
    */
  def from[Sol](creators: TraversableOnce[TCreatorFunc[Sol]],
                mutators: TraversableOnce[TMutatorFunc[Sol]],
                deletors: TraversableOnce[TDeletorFunc[Sol]],
                fitnesses: TraversableOnce[TFitnessFunc[Sol]])
               (implicit system: ActorSystem)
  : TEvolutionaryProcess[Sol] = {
    // TODO validate that there is at least one of each creator/mutator/deletors/fitness

    val props = Props(new EvvoIsland[Sol](
      creators.toVector,
      mutators.toVector,
      deletors.toVector,
      fitnesses.toVector))
    EvvoIsland.Wrapper[Sol](system.actorOf(props, s"EvvoIsland_${java.util.UUID.randomUUID()}"))
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

    override def run(terminationCriteria: TTerminationCriteria): TEvolutionaryProcess[Sol] = {
      // Block forever, `run` is meant to be a blocking call.
      Await.result(ref ? Run(terminationCriteria), Duration.Inf)
      this
    }

    override def currentParetoFrontier(): TParetoFrontier[Sol] = {
      Await.result(ref ? GetParetoFrontier, Duration.Inf).asInstanceOf[TParetoFrontier[Sol]]
    }
  }

  case class Run(terminationCriteria: TTerminationCriteria)
  case object GetParetoFrontier
}

/**
  * @param creators  the functions to be used for creating new solutions.
  * @param mutators  the functions to be used for creating new solutions from current solutions.
  * @param deletors  the functions to be used for deciding which solutions to delete.
  * @param fitnesses the objective functions to maximize.
  */
case class EvvoIslandBuilder[Sol]
(
  creators: Set[TCreatorFunc[Sol]] = Set[TCreatorFunc[Sol]](),
  mutators: Set[TMutatorFunc[Sol]] = Set[TMutatorFunc[Sol]](),
  deletors: Set[TDeletorFunc[Sol]] = Set[TDeletorFunc[Sol]](),
  fitnesses: Set[TFitnessFunc[Sol]] = Set[TFitnessFunc[Sol]]()
) {
  def addCreator(creatorFunc: CreatorFunctionType[Sol]): EvvoIslandBuilder[Sol] = {
    this.copy(creators = creators + CreatorFunc(creatorFunc, creatorFunc.toString))
  }

  def addCreator(creatorFunc: TCreatorFunc[Sol]): EvvoIslandBuilder[Sol] = {
    this.copy(creators = creators + creatorFunc)
  }

  def addMutator(mutatorFunc: MutatorFunctionType[Sol]): EvvoIslandBuilder[Sol] = {
    this.copy(mutators = mutators + MutatorFunc(mutatorFunc, mutatorFunc.toString))
  }

  def addMutator(mutatorFunc: TMutatorFunc[Sol]): EvvoIslandBuilder[Sol] = {
    this.copy(mutators = mutators + mutatorFunc)
  }

  def addDeletor(deletorFunc: DeletorFunctionType[Sol]): EvvoIslandBuilder[Sol] = {
    this.copy(deletors = deletors + DeletorFunc(deletorFunc, deletorFunc.toString))
  }

  def addDeletor(deletorFunc: TDeletorFunc[Sol]): EvvoIslandBuilder[Sol] = {
    this.copy(deletors = deletors + deletorFunc)
  }

  def addFitness(fitnessFunc: FitnessFunctionType[Sol], name: String = null)
  : EvvoIslandBuilder[Sol] = {
    val realName = if (name == null) fitnessFunc.toString() else name
    this.copy(fitnesses = fitnesses + FitnessFunc(fitnessFunc, realName))
  }

  def build()(implicit system: ActorSystem): TEvolutionaryProcess[Sol] = {
    EvvoIsland.from[Sol](creators, mutators, deletors, fitnesses)
  }

}