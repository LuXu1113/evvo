package io.evvo

import io.evvo.LocalEvvoTestFixtures._
import io.evvo.agent.{CreatorFunction, MutatorFunction}
import io.evvo.builtin.deletors.DeleteDominated
import io.evvo.island._
import io.evvo.island.population.{Minimize, Objective, ParetoFrontier}
import io.evvo.tags.{Performance, Slow}
import org.scalatest.{Matchers, WordSpec}
import unit.scala.io.evvo.fixtures.testemigrators.{
  LocalEmigrator,
  LocalImmigrator,
  LocalParetoFrontierIgnorer
}

import scala.concurrent.duration._

/** Tests a single island cluster.
  *
  * The behavior under test is that an Island can sort a list given the proper modifier and fitness
  * function, and terminate successfully returning a set of lists.
  */
class LocalEvvoTest extends WordSpec with Matchers {

  /** Creates a test Evvo instance running locally, that will use basic swapping
    * to mutate lists, starting in reverse order, scoring them on the number of inversions.
    *
    * @param listLength the length of the lists to sort.
    * @return
    */
  def getEvvo(listLength: Int): EvolutionaryProcess[Solution] = {
    new EvvoIsland[Solution](
      Vector(new ReverseListCreator(10)),
      Vector(new SwapTwoElementsModifier()),
      Vector(DeleteDominated()),
      Vector(new NumInversions()),
      new LocalImmigrator[Solution](),
      AllowAllImmigrationStrategy(),
      new LocalEmigrator[Solution](SendToAllEmigrationTargetStrategy()),
      RandomSampleEmigrationStrategy(n = 16),
      LogPopulationLoggingStrategy(),
      LocalParetoFrontierIgnorer()
    )
  }

  "Local Evvo" should {
    val timeout = 1
    val listLength = 6
    f"be able to sort a list of length $listLength within $timeout seconds" taggedAs (Performance, Slow) in {
      val terminate = StopAfter(timeout.seconds)

      val evvo = getEvvo(listLength)
      val evvo2 = getEvvo(listLength)

      evvo.runAsync(terminate)
      evvo2.runBlocking(terminate)
      val pareto: ParetoFrontier[Solution] = ParetoFrontier[Solution](
        evvo.currentParetoFrontier().solutions ++ evvo2.currentParetoFrontier().solutions)
      assert(
        pareto.solutions.exists(_.score("Inversions")._2 == 0d),
        pareto.solutions.map(_.score("Inversions")._2).fold(Double.MaxValue)(math.min))
    }
  }
}

object LocalEvvoTestFixtures {
  type Solution = List[Int]

  class ReverseListCreator(listLength: Int) extends CreatorFunction[Solution]("ReverseCreator") {
    override def create(): Iterable[Solution] = {
      Vector((listLength to 1 by -1).toList)
    }
  }

  class SwapTwoElementsModifier extends MutatorFunction[Solution]("SwapTwo") {
    override def mutate(sol: Solution): Solution = {
      val i = util.Random.nextInt(sol.length)
      val j = util.Random.nextInt(sol.length)
      sol.updated(j, sol(i)).updated(i, sol(j))
    }
  }

  class NumInversions extends Objective[Solution]("Inversions", Minimize()) {
    override protected def objective(sol: Solution): Double = {
      // Old way of doing it: still works, but more clear with tails: left for clarity
      //      (for ((elem, index) <- sol.zipWithIndex) yield {
      //        sol.drop(index).count(_ < elem)
      //      }).sum

      // For each item, add the number of elements in the rest of the list less than this item.
      // Once you're at the last item, make no change.
      sol.tails.foldRight(0d)((list, numInversionsSoFar) => {
        list match {
          case item :: rest => numInversionsSoFar + rest.count(_ < item)
          case _ => numInversionsSoFar
        }
      })
    }
  }
}
