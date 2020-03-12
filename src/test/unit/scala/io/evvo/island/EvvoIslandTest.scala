package io.evvo.island

import io.evvo.island.population.{Maximize, Objective}
import io.evvo.tags.Slow
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import unit.scala.io.evvo.fixtures.testemigrators
import unit.scala.io.evvo.fixtures.testemigrators.{
  LocalEmigrator,
  LocalImmigrator,
  LocalParetoFrontierIgnorer
}

class EvvoIslandTest extends WordSpec with Matchers with BeforeAndAfter {
  // private because EvvoIsland is private, required to compile.
  private var island1: EvvoIsland[Int] = _
  private var island2: EvvoIsland[Int] = _
  object MaximizeInt extends Objective[Int]("Test", Maximize()) {
    override protected def objective(sol: Int): Double = sol
  }

  before {
    island1 = new EvvoIsland(
      Vector(),
      Vector(),
      Vector(),
      Vector(MaximizeInt),
      new LocalImmigrator[Int](),
      ElitistImmigrationStrategy(),
      new LocalEmigrator[Int](SendToAllEmigrationTargetStrategy()),
      RandomSampleEmigrationStrategy(4),
      LogPopulationLoggingStrategy(),
      LocalParetoFrontierIgnorer()
    )

    island2 = new EvvoIsland(
      Vector(),
      Vector(),
      Vector(),
      Vector(MaximizeInt),
      new LocalImmigrator[Int](),
      AllowAllImmigrationStrategy(),
      new LocalEmigrator[Int](SendToAllEmigrationTargetStrategy()),
      RandomSampleEmigrationStrategy(4),
      LogPopulationLoggingStrategy(),
      LocalParetoFrontierIgnorer()
    )
  }

  "EvvoIsland" should {
    "use immigration strategy to filter incoming solutions" in {
      island1.addSolutions(Seq(10))
      island2.emigrate()
      island1.immigrate()

      // The three shouldn't be added, because Elitist will prevent anything < 10 from being added
      island1.currentParetoFrontier().solutions should have size 1

      // But 11 should make it through.
      island2.addSolutions(Seq(11))
      island2.emigrate()
      island1.immigrate()
      island1.currentParetoFrontier().solutions.map(_.solution) should be(Set(11))
      testemigrators.reset()
    }

    "emigrate strategies to other islands" taggedAs Slow in {
      // Is island2 is changed by island 1 running, then emigration must have happened.
      island2.currentParetoFrontier().solutions.size shouldBe 0
      island1.addSolutions(Seq(1))
      island1.emigrate()
      island2.immigrate()
      island2.currentParetoFrontier().solutions.size shouldBe 1
      testemigrators.reset()
    }

    "use the emigration strategy to choose which solutions to emigrate" taggedAs Slow in {
      val noEmigrationIsland = new EvvoIsland[Int](
        Vector(),
        Vector(),
        Vector(),
        Vector(MaximizeInt),
        new LocalImmigrator[Int](),
        ElitistImmigrationStrategy(),
        new LocalEmigrator[Int](SendToAllEmigrationTargetStrategy()),
        NoEmigrationEmigrationStrategy,
        LogPopulationLoggingStrategy(),
        LocalParetoFrontierIgnorer()
      )

      // Because island2 wasn't changed, in conjunction with the above test, the change in
      // emigration strategy made a difference.
      island2.currentParetoFrontier().solutions.size shouldBe 0
      noEmigrationIsland.emigrate()
      island2.immigrate()
      island2.currentParetoFrontier().solutions.size shouldBe 0
    }
  }
}
