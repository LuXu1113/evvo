package integration

import com.diatom.{TIsland, TScored}
import com.diatom.island.{SingleIslandEvvo, TerminationCriteria}
import com.diatom.tags.Integration
import org.scalatest.{FlatSpec, Matchers, WordSpec}

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
    def createFunc(): Set[Solution] = {
      val out = Vector.fill(10)((listLength to 1 by -1).toList).map(mutate).toSet
      out
    }

    def mutate(sol: Solution): Solution = {
      val i = util.Random.nextInt(sol.length)
      val j = util.Random.nextInt(sol.length)
      val tmp = sol(j)
      sol.updated(j, sol(i)).updated(i, tmp)
    }

    def mutateFunc(s: Set[TScored[Solution]]): Set[Solution] = {
      s.map(scoredSol => {
        val sol = scoredSol.solution
        val out = mutate(sol)
        out
      })
    }

    def deleteFunc(s: Set[TScored[Solution]]): Set[TScored[Solution]] = {
      if (s.isEmpty) {
        s
      } else {
        val sums = s.map(_.score.values.sum).toVector.sorted
        val cutoff = sums(sums.size / 2)
        s.filter(_.score.values.sum > cutoff)
      }
    }

    def numInversions(s: Solution): Double = {
      (for ((elem, index) <- s.zipWithIndex) yield {
        s.drop(index).count(_ < elem)
      }).sum
    }


    SingleIslandEvvo.builder[Solution]()
      .addCreator(createFunc)
      .addMutator(mutateFunc)
      .addDeletor(deleteFunc)
      .addFitness(numInversions)
      .build()
  }

  "Single Island Evvo" should {
    "be able to sort a list of length 5 within 1 second" taggedAs Integration in {
      val listLength = 5
      val timeout = 1
      val terminate = TerminationCriteria(timeout.seconds)


      val pareto: Set[Solution] = getEvvo(listLength)
        .run(terminate)
        .solutions
        .map(_.solution)
      pareto should contain(1 to listLength toList)
    }

    // is 8 really sufficient here?
    // maybe, because the algorithm we are using (without evolutionary computing, just
    // swapping random ones) is n! time.
    "be able to sort a list of length 8 within 10 seconds" taggedAs Integration in {
      val listLength = 8
      val timeout = 10
      val terminate = TerminationCriteria(timeout.seconds)


      val pareto: Set[Solution] = getEvvo(listLength)
        .run(terminate)
        .solutions
        .map(_.solution)
      pareto should contain(1 to listLength toList)
    }
  }
}
