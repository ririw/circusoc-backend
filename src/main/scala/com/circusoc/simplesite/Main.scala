package com.circusoc.simplesite

import spray.routing.SimpleRoutingApp
import akka.actor.ActorSystem
import org.codemonkey.simplejavamail.{Mailer, Email}
import com.circusoc.simplesite.hire.HireService
import com.circusoc.simplesite.pictures.PictureService
import com.circusoc.simplesite.auth.AuthService
import com.circusoc.simplesite.tracking.TrackingEventService

object Main extends App
            with SimpleRoutingApp
            with Core
            with AuthService
            with HireService
            with PictureService
            with TrackingEventService {
  implicit val system = ActorSystem("my-system")
  config.db.setup()
  startServer(interface = "localhost", port = 8080) {
    path("hello") {
      get {
        complete {
          "Say hello to spray"
        }
      }
    } ~
    authroutes ~
    hireRoutes ~
    pictureRoutes ~
    trackingRoutes ~
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
  implicit val config = new WithConfig {
    override val isProduction = true
    override val db: DB = new DB{}
    override val hire: Hire = new Hire {}
    override val mailer: MailerLike = new MailerLike {
      val mailer = new Mailer(hire.smtpHost, hire.smtpPort, hire.smtpUser, hire.smtpPass)
      override def sendMail(email: Email): Unit = {
        Thread.sleep(500)
        println("Sent mail")
      }
      //mailer.sendMail(email)
    }
    override val paths: PathConfig = new PathConfig {}
  }

  protected implicit def system: ActorSystem
}
