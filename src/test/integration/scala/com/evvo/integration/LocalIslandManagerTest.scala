package com.evvo.integration

import com.evvo.agent.defaults.DeleteDominated
import com.evvo.agent.{CreatorFunction, MutatorFunction}
import com.evvo.island.population.{Maximize, Objective, Scored}
import com.evvo.island.{EvvoIslandBuilder, LocalIslandManager, StopAfter}
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

class LocalIslandManagerTest extends WordSpec with Matchers {

  "LocalIslandManager" should {
    "Be able to optimize problems" in {

      type Solution = String

      val startV = new Objective[Solution]("startV", Maximize) {
        override protected def objective(sol: Solution): Double = {
          sol.drop(1).dropRight(1).takeWhile(_ == 'v').length
        }
      }

      val endV = new Objective[Solution]("endV", Maximize) {
        override protected def objective(sol: Solution): Double = {
          sol.drop(1).dropRight(1).reverse.takeWhile(_ == 'v').length
        }
      }

      val creator = new CreatorFunction[Solution]("create") {
        override def create(): TraversableOnce[Solution] = Vector("evvo")
      }

      val mutator = new MutatorFunction[Solution]("mutate") {
        override def mutate(sols: IndexedSeq[Scored[Solution]]): TraversableOnce[Solution] = {
          sols.map(s => {
            val (e, rest) = s.solution.splitAt(1)
            e + util.Random.alphanumeric.head.toString + rest
          })
        }
      }

      val builder = EvvoIslandBuilder[Solution]()
        .addObjective(startV)
        .addObjective(endV)
        .addCreator(creator)
        .addMutator(mutator)
        .addDeletor(DeleteDominated[Solution]())

      val manager = new LocalIslandManager(7, builder)

      manager.runBlocking(StopAfter(1.second))

      val pareto = manager.currentParetoFrontier()
      assert(pareto.solutions.exists(s =>
        s.score(("startV", Maximize)) > 10 && s.score(("endV", Maximize)) > 10))
    }
  }

}
