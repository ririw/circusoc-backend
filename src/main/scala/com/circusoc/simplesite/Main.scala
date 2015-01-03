package com.circusoc.simplesite

import com.circusoc.simplesite.services._
import spray.http.HttpHeaders.Cookie
import spray.http.{HttpHeaders, Rendering, HttpHeader}
import spray.routing.SimpleRoutingApp
import akka.actor.ActorSystem
import org.codemonkey.simplejavamail.{Mailer, Email}

object Main extends App
            with SimpleRoutingApp
            with Core
            with AuthService
            with HireService
            with PerformerService
            with PictureService
            with TrackingEventService {
  implicit val system = ActorSystem("my-system")
  config.db.setup()
  startServer(interface = "localhost", port = 8080) {
    authroutes ~
    hireRoutes ~
    pictureRoutes ~
    trackingRoutes ~
    performerRoutes ~
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
  implicit lazy val config = new WithConfig {
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


class CorsHeader extends HttpHeader {
  override def name: String = "Access-Control-Allow-Origin"
  override def value: String = "*"
  override def lowercaseName: String = "access-control-allow-origin"
  override def render[R <: Rendering](r: R): r.type = r ~~ s"$name: $value"
}