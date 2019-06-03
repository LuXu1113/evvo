package com.evvo.integration

import com.evvo.agent.{DeletorFunc, DeletorFunction}
import com.evvo.agent.defaults.DeleteWorstHalfByRandomObjective
import com.evvo.island.{EvolutionaryProcess, EvvoIslandBuilder, StopAfter}
import com.evvo.tags.{Performance, Slow}
import com.evvo.{CreatorFunctionType, MutatorFunctionType, NullLogger, ObjectiveFunctionType}
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

/**
  * Tests a single island cluster.
  *
  * The behavior under test is that an Island can sort a list given the proper mutator and fitness
  * function, and terminate successfully returning a set of lists.
  */
class LocalEvvoTest extends WordSpec with Matchers {

  /** High level concept for the test:
    *
    * Create an island
    * - Supply mutators, deletors, creators
    * - Supply a termination condition
    * - Supply a starting population
    *
    * Start the evolutionary process
    *
    * Wait for the process to terminate, and see if result is sorted.
  */

  type Solution = List[Int]

  /**
    * Creates a test Evvo instance running locally, that will use basic swapping
    * to mutate lists, starting in reverse order, scoring them on the number of inversions.
    *
    * @param listLength the length of the lists to sort.
    * @return
    */
  def getEvvo(listLength: Int): EvolutionaryProcess[Solution] = {
    val createFunc: CreatorFunctionType[Solution] = () => {
      Vector((listLength to 1 by -1).toList)
    }

    def mutate(sol: Solution): Solution = {
      val i = util.Random.nextInt(sol.length)
      val j = util.Random.nextInt(sol.length)
      val tmp = sol(j)
      sol.updated(j, sol(i)).updated(i, tmp)
    }

    val mutateFunc: MutatorFunctionType[Solution] = s => {
      s.map(scoredSol => {
        val sol = scoredSol.solution
        val out = mutate(sol)
        out
      })
    }

    val deleteFunc: DeletorFunction[Solution] = DeleteWorstHalfByRandomObjective[Solution]()

    val numInversions: ObjectiveFunctionType[Solution] = (s: Solution) => {
      (for ((elem, index) <- s.zipWithIndex) yield {
        s.drop(index).count(_ < elem)
      }).sum
    }

    // TODO add convenience constructor for adding multiple duplicate mutators/creators/deletors
    val islandBuilder = EvvoIslandBuilder[Solution]()
      .addCreatorFromFunction(createFunc)
      .addMutatorFromFunction(mutateFunc)
      .addMutatorFromFunction(mutateFunc)
      .addMutatorFromFunction(mutateFunc)
      .addMutatorFromFunction(mutateFunc)
      .addDeletor(deleteFunc)
      .addDeletor(deleteFunc)
      .addDeletor(deleteFunc)
      .addDeletor(deleteFunc)
      .addDeletor(deleteFunc)
      .addObjective(numInversions)

    islandBuilder.buildLocalEvvo()
  }

  implicit val log = NullLogger

  "Local Evvo" should {
    val timeout = 1
    f"be able to sort a list of length 10 within $timeout seconds" taggedAs(Performance, Slow) in {
      val listLength = 10
      val terminate = StopAfter(timeout.seconds)

      val evvo = getEvvo(listLength)
      evvo.runBlocking(terminate)

      val pareto: Set[Solution] = evvo
        .currentParetoFrontier()
        .solutions
        .map(_.solution)
      pareto should contain(1 to listLength toList)
      pareto.size shouldBe 1
    }
  }
}
