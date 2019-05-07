package com.diatom.agent

import com.diatom.island.population.TPopulation
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._

trait TDeletorAgent[Sol] extends TAgent[Sol]

case class DeletorAgent[Sol](delete: TDeletorFunc[Sol],
                             pop: TPopulation[Sol],
                             strat: TAgentStrategy = DeletorAgentDefaultStrategy())
  extends AAgent[Sol](strat, pop, delete.name) with TDeletorAgent[Sol] {

  override protected def step(): Unit = {
    val in = pop.getSolutions(delete.numInputs)
    // TODO configure whether to allow running without
    if (in.length == delete.numInputs) {
      val toDelete = delete.delete(in)
      pop.deleteSolutions(toDelete)
    } else {
      log.warn(s"not enough solutions in population: got ${in.length}, wanted ${delete.numInputs}")
    }
  }
}

case class DeletorAgentDefaultStrategy() extends TAgentStrategy {
  override def waitTime(populationInformation: TPopulationInformation): Duration = {
    if (populationInformation.numSolutions < 20) {
      30.millis // give creators a chance!
    } else if (populationInformation.numSolutions > 300) {
      0.millis
    }
    else {
      // min of 1 and fourth root of num solutions. No particular reason why.
      math.max(1, math.sqrt(math.sqrt(populationInformation.numSolutions))).millis
    }
  }
}