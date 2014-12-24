package com.circusoc

import akka.actor.ActorSystem
import com.circusoc.taglink._
import spray.routing.SimpleRoutingApp
import com.circusoc.testgraph.testgraph._
import scalikejdbc.ConnectionPool

/**
 * The name of the game here is to create little setups, each of which describes the
 * server as we want it. Then you may pass the configuration you want as the first
 * command line argument, and go from there.
 */
object TestTest extends App {
  val command = if (args.length == 0) {
    Console.readLine(s"${Console.GREEN}Enter the setup you want: ")
  } else {
    args(0)
  }
  command match {
    case s if s.startsWith("taglink:") => taglink(s.drop("taglink:".length))
    case s => default(s)
  }

  def default(arg: String): Unit = {
    Console.println(s"${Console.RED}Oops! Looks like nothing was specified, your argument was $arg")
  }

  def taglink(subcommand: String): Unit = {
    val server = new TagLink()
    server.taglinkDB.setup()
    DBSetup.setup()(server)
    implicit val contentjoin = new TestContentLinker(server)
    implicit val tagLocLinker = new TagLocationLinker()

    subcommand match {
      case "random" =>
        val contentFactory = new TaglinkContentFactory()
        val locationFactory = new TaglinkLocationFactory()
        val tagFactory = new TaglinkTagFactory()
        val content = List.fill(2000)(contentFactory.randomNode())
        val locations = List.fill(1000)(locationFactory.randomNode())
        val tags = List.fill(2000)(tagFactory.randomNode())
        val locationsAndTags = locations.join.randomSurjectionJoin(tags)
        val locTagsandContent = locationsAndTags.join.bijectiveJoin(content)
      case "empty" => Unit
      case _ =>
        Console.println(s"${Console.RED}Oops! Looks like nothing was specified, your argument was $subcommand${Console.RESET}")
    }
    server.serve()
  }
}

class TagLink extends TagLinkServer with TagLinkConfig with SimpleRoutingApp {
  override val taglinkDB = new DB {
    override def poolName: Symbol = 'taglinktest
    override def setup() {
      Class.forName("org.h2.Driver")
      ConnectionPool.add(poolName, "jdbc:h2:mem:taglinktest;DB_CLOSE_DELAY=-1", "sa", "")
    }
  }

  def serve(): Unit = {
    implicit val system = ActorSystem("my-system")
    startServer(interface = "localhost", port = 8080)(taglinkRoutes)
  }
}