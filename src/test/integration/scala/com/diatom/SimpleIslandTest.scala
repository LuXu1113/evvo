package com.diatom

import com.diatom.island.{SingleIslandEvvo, TIsland, TerminationCriteria}
import com.diatom.professormatching.ProfessorMatching.Sol
import com.diatom.tags.{Performance, Slow}
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._

/**
  * Tests a single island cluster.
  *
  * The behavior under test is that an Island can sort a list given the proper mutator and fitness
  * function, and terminate successfully returning a set of lists.
  */
class SimpleIslandTest extends WordSpec with Matchers {

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
  def getEvvo(listLength: Int): TIsland[Solution] = {
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

    val deleteFunc: DeletorFunctionType[Solution] = s => {
      if (s.isEmpty) {
        s
      } else {
        val sums = s.map(_.score.values.sum).toVector.sorted
        val cutoff = sums(sums.size / 2)
        s.filter(_.score.values.sum > cutoff)
      }
    }

    val numInversions: FitnessFunctionType[Solution] = (s: Solution) => {
      (for ((elem, index) <- s.zipWithIndex) yield {
        s.drop(index).count(_ < elem)
      }).sum
    }


    SingleIslandEvvo.builder[Solution]()
      .addCreator(createFunc)
      .addMutator(mutateFunc)
      .addMutator(mutateFunc)
      .addMutator(mutateFunc)
      .addMutator(mutateFunc)
      .addDeletor(deleteFunc)
      .addDeletor(deleteFunc)
      .addDeletor(deleteFunc)
      .addDeletor(deleteFunc)
      .addDeletor(deleteFunc)
      .addFitness(numInversions)
      .build()
  }

  "Single Island Evvo" should {
    // at this point, the second test replaces this one, but we're leaving it in as something
    //    we were proud of once
    "be able to sort a list of length 5 within 1 second" taggedAs (Performance, Slow) in {
      val listLength = 5
      val timeout = 1
      val terminate = TerminationCriteria(timeout.seconds)


      val pareto: Set[Solution] = getEvvo(listLength)
        .run(terminate)
        .solutions
        .map(_.solution)
      pareto should contain(1 to listLength toList)
    }

     val timeout = 1
    f"be able to sort a list of length 10 within $timeout seconds" taggedAs (Performance, Slow) in {
      val listLength = 10
      val terminate = TerminationCriteria(timeout.seconds)


      val pareto: Set[Solution] = getEvvo(listLength)
        .run(terminate)
        .solutions
        .map(_.solution)
      pareto should contain(1 to listLength toList)
      pareto.size shouldBe 1
    }
  }
}
