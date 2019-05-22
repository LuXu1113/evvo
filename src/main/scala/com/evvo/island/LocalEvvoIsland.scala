package com.evvo.island

import akka.event.LoggingAdapter
import com.evvo.agent.{TCreatorFunc, TDeletorFunc, TMutatorFunc}
import com.evvo.island.population.{TObjective, TParetoFrontier}
import org.slf4j.LoggerFactory

import scala.concurrent.Future


/**
  * An island that can be run locally. Does not connect to any other networked island, but is good
  * for testing agent functions without having to spin up a cluster.
  */
class LocalEvvoIsland[Sol]
(
  creators: Vector[TCreatorFunc[Sol]],
  mutators: Vector[TMutatorFunc[Sol]],
  deletors: Vector[TDeletorFunc[Sol]],
  fitnesses: Vector[TObjective[Sol]]
)(
  implicit val log: LoggingAdapter = LocalLogger
) extends TEvolutionaryProcess[Sol] {
  private val island = new EvvoIsland(creators, mutators, deletors, fitnesses)

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
    island.poisonPill()
  }
}


object LocalLogger extends LoggingAdapter {
  private val logger = LoggerFactory.getLogger("LocalEvvoIsland")

  override def isErrorEnabled: Boolean = true

  override def isWarningEnabled: Boolean = true

  override def isInfoEnabled: Boolean = true

  override def isDebugEnabled: Boolean = true

  override protected def notifyError(message: String): Unit = {
    logger.error(message)
  }

  override protected def notifyError(cause: Throwable, message: String): Unit = {
    logger.error(message, cause)
  }

  override protected def notifyWarning(message: String): Unit = {
    logger.warn(message)
  }

  override protected def notifyInfo(message: String): Unit = {
    logger.info(message)
  }

  override protected def notifyDebug(message: String): Unit = {
    logger.debug(message)
  }
}
