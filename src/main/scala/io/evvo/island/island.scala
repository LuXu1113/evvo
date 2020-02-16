package io.evvo.island

import java.util.TimerTask
import java.util.concurrent.{Executors, TimeUnit}

import io.evvo.agent._
import io.evvo.island.population._
import io.evvo.migration.{Emigrator, Immigrator}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

/** This component is used to do all the actual work of managing the island, without managing
  * or being tied to where the island is deployed to.
  */
class EvvoIsland[Sol: Manifest](
    creators: Vector[CreatorFunction[Sol]],
    mutators: Vector[ModifierFunction[Sol]],
    deletors: Vector[DeletorFunction[Sol]],
    fitnesses: Vector[Objective[Sol]],
    immigrator: Immigrator[Sol],
    immigrationStrategy: ImmigrationStrategy,
    emigrator: Emigrator[Sol],
    emigrationStrategy: EmigrationStrategy,
    loggingStrategy: LoggingStrategy
) extends EvolutionaryProcess[Sol] {

  private val pop: Population[Sol] = StandardPopulation(fitnesses)
  private val creatorAgents = creators.map(c => CreatorAgent(c, pop))
  private val mutatorAgents = mutators.map(m => ModifierAgent(m, pop))
  private val deletorAgents = deletors.map(d => DeletorAgent(d, pop))
  private val allAgents: Seq[AAgent[Sol]] = creatorAgents ++ mutatorAgents ++ deletorAgents

  override def runAsync(stopAfter: StopAfter): Future[Unit] = {
    allAgents.foreach(_.start())

    val immigrationExecutor = Executors.newSingleThreadScheduledExecutor()
    immigrationExecutor.scheduleAtFixedRate(
      () => this.immigrate(),
      this.immigrationStrategy.durationBetweenRuns.toMillis,
      this.immigrationStrategy.durationBetweenRuns.toMillis,
      TimeUnit.MILLISECONDS
    )

    val emigrationExecutor = Executors.newSingleThreadScheduledExecutor()
    emigrationExecutor.scheduleAtFixedRate(
      () => this.emigrate(),
      this.emigrationStrategy.durationBetweenRuns.toMillis,
      this.emigrationStrategy.durationBetweenRuns.toMillis,
      TimeUnit.MILLISECONDS)

    val done = Promise[Unit]()
    val timer = new java.util.Timer()
    val shutDown = new TimerTask {
      override def run(): Unit = {
        immigrationExecutor.shutdown()
        emigrationExecutor.shutdown()
        done.success(())
      }
    }
    timer.schedule(shutDown, stopAfter.time.toMillis)

    done.future
  }

  override def immigrate(): Unit = {
    val immigrants = this.immigrator.immigrate(this.immigrationStrategy.numberOfImmigrantsPerBatch)
    this.immigrationStrategy.addImmigrants(immigrants, this.pop)
  }

  override def emigrate(): Unit = {
    this.emigrator.emigrate(this.emigrationStrategy.chooseSolutions(this.pop))
  }

  def runBlocking(stopAfter: StopAfter): Unit = {
    Await.result(this.runAsync(stopAfter), Duration.Inf)
  }

  override def addSolutions(solutions: Seq[Sol]): Unit = {
    pop.addSolutions(solutions)
  }

  override def currentParetoFrontier(): ParetoFrontier[Sol] = {
    pop.getParetoFrontier()
  }

  override def poisonPill(): Unit = {
    stop()
  }

  private def stop(): Unit = {
    allAgents.foreach(_.stop())
  }

  override def agentStatuses(): Seq[AgentStatus] = allAgents.map(_.status())
}

//object EvvoIsland {
// TODO builder
//  /** @tparam Sol the type of solutions processed by this island.
//    * @return A builder for an EvvoIsland.
//    */
//  def builder[Sol](): UnfinishedEvvoIslandBuilder[Sol, _, _, _] = EvvoIslandBuilder[Sol]()
//}
