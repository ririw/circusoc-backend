package com.circusoc.simplesite

import spray.routing.SimpleRoutingApp
import akka.actor.ActorSystem
import com.circusoc.simplesite.users.Auth

object Main extends App with SimpleRoutingApp with Core with Auth {
  implicit val system = ActorSystem("my-system")
  implicit val config = new WithConfig {
    override val db: DB = new DB{}
  }

  startServer(interface = "localhost", port = 8080) {
    path("hello") {
      get {
        complete {
          <h1>Say hello to spray</h1>
        }
      }
    } ~
    authroutes ~
    path("setup") {
      get {
        complete {
          DBSetup.setup()
          "Done"
        }
    }
    }
  }
}

/**
 * Core is type containing the ``system: ActorSystem`` member. This enables us to use it in our
 * apps as well as in our tests.
 */
trait Core {
  protected implicit def system: ActorSystem
}