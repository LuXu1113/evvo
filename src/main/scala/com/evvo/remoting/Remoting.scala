package com.evvo.remoting

import java.io.File

import akka.actor.ActorSystem
import com.evvo.agent.defaults.DeleteWorstHalfByRandomObjective
import com.evvo.island.population.{Maximize, Objective}
import com.evvo.island.{RemoteEvvoIsland, RemoteIslandManager, StopAfter}
import com.evvo.{CreatorFunctionType, MutatorFunctionType}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object Remoting {
  val creator: CreatorFunctionType[Int] =
    () => {
      Set(0)
    }

  val mutator: MutatorFunctionType[Int] = {
    s => s.map(_.solution + 1)
  }


  def main(args: Array[String]): Unit = {

//    val builder = RemoteEvvoIsland.builder[Int]()
//      .addObjective(Objective[Int](x => x, "identity", Maximize))
//      .addCreator(CreatorFunc(creator, "Set(0)"))
//      .addMutator(MutatorFunc(mutator, "Add1"))
//      .addDeletor(DeleteWorstHalfByRandomObjective(numInputs = 2))
//
//
//    val config = ConfigFactory
//      .parseFile(new File("src/main/resources/application.conf"))
//      .resolve()
//
//    implicit val actorSystem: ActorSystem = ActorSystem("EvvoNode",config)
//
//    val islandManager = new RemoteIslandManager(10, builder)
//
//    islandManager.runBlocking(StopAfter(1.second))
//
//    println(islandManager.currentParetoFrontier())
//    actorSystem.terminate()
  }
}
