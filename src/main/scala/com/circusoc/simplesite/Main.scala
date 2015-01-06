package com.circusoc.simplesite

import java.net.URL

import com.circusoc.simplesite.services._
import com.codahale.metrics.MetricRegistry
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
            with MemberService
            with CorsService
            with TrackingEventService {
  implicit val system = ActorSystem("my-system")
  config.db.setup()
  startServer(interface = "localhost", port = 8080) {
    authroutes ~
    hireRoutes ~
    pictureRoutes ~
    trackingRoutes ~
    memberroutes ~
    performerRoutes ~
    corsRoutes ~
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
        println("Sent mail: " + email.getText)
      }
      //mailer.sendMail(email)
    }
    override val paths: PathConfig = new PathConfig {}
  }

  protected implicit def system: ActorSystem
}


class CorsOriginHeader(url: URL) extends HttpHeader {
  override def name: String = "Access-Control-Allow-Origin"
  override def value: String = url.toExternalForm
  override def lowercaseName: String = "access-control-allow-origin"
  override def render[R <: Rendering](r: R): r.type = r ~~ s"$name: $value"
}
class CorsMethodHeader extends HttpHeader {
  override def name: String = "Access-Control-Allow-Methods"
  override def value: String = "GET, POST, PUT"
  override def lowercaseName: String = "access-control-allow-methods"
  override def render[R <: Rendering](r: R): r.type = r ~~ s"$name: $value"
}
class CorsAgeHeader extends HttpHeader {
  override def name: String = "Access-Control-Max-Age"
  override def value: String = "0"
  override def lowercaseName: String = "access-control-max-age"
  override def render[R <: Rendering](r: R): r.type = r ~~ s"$name: $value"
}
class CorsAcceptHeadersHeader(requested_headers: String) extends HttpHeader {
  override def name: String = "Access-Control-Allow-Headers"
  override def value: String = requested_headers
  override def lowercaseName: String = "access-control-allow-headers"
  override def render[R <: Rendering](r: R): r.type = r ~~ s"$name: $value"
}