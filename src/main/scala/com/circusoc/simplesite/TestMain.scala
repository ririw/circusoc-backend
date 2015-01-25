package com.circusoc.simplesite

import java.net.URL

import akka.actor.ActorSystem
import com.circusoc.simplesite.services._
import com.circusoc.simplesite.users.permissions
import com.circusoc.testgraph.testgraph._
import com.circusoc.testgraph.{PerformerTestGraph, PictureTestGraph}
import org.codemonkey.simplejavamail.{Email, Mailer}
import scalikejdbc.{ConnectionPool, NamedDB}
import spray.routing.SimpleRoutingApp


object TestMain extends App {
  fullSite()
  def fullSite(): Unit = {
    val server = new MainSite()
    server.config.db.setup()
    implicit val config = server.config
    com.circusoc.simplesite.DBSetup.setup
    val performersF   = PerformerTestGraph.performerNodeFactory
    val pendingSkillF = PerformerTestGraph.pendingSkillNodeFactory
    val picF          = PictureTestGraph.pictureFactory
    val userF         = PerformerTestGraph.adminNodeFactory
    implicit val performerPictureJoiner    = PerformerTestGraph.performerPictureJoiner
    implicit val performerProfilePicJoiner = PerformerTestGraph.performerProfilePicJoiner
    implicit val performerSkillJoiner      = PerformerTestGraph.performerSkillJoiner
    implicit val skillPicJoiner            = PerformerTestGraph.skillPictureJoin
    implicit val userPermJoiner            = PerformerTestGraph.userPermissionJoiner

    val numSkills = 10
    val users       = List.fill(3)  (userF.randomNode())
    val performers  = List.fill(10) (performersF.randomNode)
    val otherpics   = List.fill(100)(picF.randomNode)
    val profilePics = List.fill(10) (picF.randomNode)
    val skillPics   = List.fill(numSkills)(picF.randomNode)
    val skills      = List.fill(numSkills)(pendingSkillF.randomNode).join.bijectiveJoin(skillPics)

    users.map(userPermJoiner.join(_, permissions.CanUpdateMembers()))
    performers.join.bijectiveJoin(profilePics)(performerProfilePicJoiner)
    performers.join.randomSurjectionJoin(otherpics)(performerPictureJoiner)
    performers.join.randomJoin(skills)

    server.serve()
  }
}

class MainSite extends SimpleRoutingApp
with Core
with AuthService
with HireService
with MemberService
with PerformerService
with PictureService
with CorsService
with TrackingEventService {
  implicit val system = ActorSystem("my-system")
  override implicit lazy val config: WithConfig = new WithConfig {
    override val isProduction = false
    override val db = new com.circusoc.simplesite.DB{
      override def poolName: Symbol = 'mainsite
      override def getDB: NamedDB = NamedDB(poolName)
      override def setup() {
        Class.forName("org.h2.Driver")
        ConnectionPool.add(poolName, "jdbc:h2:mem:mainsite;DB_CLOSE_DELAY=-1", "sa", "")
        // ConnectionPool.add(poolName, "jdbc:h2:~/tmp/test", "sa", "")
      }
    }
    override val hire: Hire = new Hire {}
    override val mailer: MailerLike = new MailerLike {
      val mailer = new Mailer(hire.smtpHost, hire.smtpPort, hire.smtpUser, hire.smtpPass)
      override def sendMail(email: Email): Unit = {
        Thread.sleep(500)
        println("Sent mail: " + email.getText)
      }
      //mailer.sendMail(email)
    }
    override val paths: PathConfig = new PathConfig {
      override val baseUrl: URL = new URL("http://localhost:8080")
    }
  }

  def serve(): Unit = {
    startServer(interface = "localhost", port = 8080) {
      corsRoutes ~
        respondWithHeader(new CorsOriginHeader(config.paths.cdnUrl)) {
          performerRoutes ~
            authroutes ~
            memberroutes ~
            hireRoutes ~
            pictureRoutes ~
            trackingRoutes
        }
    }
  }
}

