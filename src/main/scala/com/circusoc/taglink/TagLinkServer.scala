package com.circusoc.taglink

import akka.actor.ActorSystem
import com.circusoc.simplesite.Main._
import com.circusoc.simplesite.WithConfig
import com.circusoc.simplesite.auth.AuthService
import com.circusoc.simplesite.users.AuthenticatedUser
import com.circusoc.simplesite.users.permissions.CanEditTagsPermission
import org.slf4j.LoggerFactory
import scalikejdbc.{NamedDB, ConnectionPool}
import scalikejdbc._
import spray.http.{HttpResponse, StatusCodes, MediaTypes}
import spray.httpx.unmarshalling.BasicUnmarshallers
import scala.concurrent.ExecutionContext.Implicits.global
import spray.json._


trait TagLinkServer {
  this: TagLinkConfig with AuthService =>
  val taglinkRoutes = {
    path("taglink" / Segment / Segment) {(location, tag) =>
      get {
        respondWithMediaType(MediaTypes.`application/json`)
        complete {
          getItems(location, tag)
        }
      } ~
    authenticate(authenticateUser) { user: AuthenticatedUser =>
        authorize(user.hasPermission(CanEditTagsPermission())) {
          put {
            entity(BasicUnmarshallers.StringUnmarshaller) { bodyJson: String =>
              complete {
                try {
                  val json = bodyJson.parseJson
                  val compacted = json.compactPrint
                  val updated = putItems(location, tag)
                  if (updated) StatusCodes.Created
                  else StatusCodes.OK
                } catch {
                  case e: org.parboiled.errors.ParsingException =>
                    HttpResponse(StatusCodes.BadRequest,
                      "Could not decode request\n" + e.getMessage)
                }
              }
            }
          } ~
          delete {
            complete {
              val was404 = deleteItems(location, tag)
              if (was404) StatusCodes.NotFound
              else StatusCodes.OK
            }
          }
        }
      }
    }
  }

  def putItems(location: String, tag: String): Boolean = {
    NamedDB(config.db.poolName).localTx { implicit session =>
      val count = sql"""SELECT count(*) FROM tag WHERE location=$location AND tag=$tag""".map(_.int(1)).first()().get
      sql"""DELETE FROM tag WHERE location=$location AND tag=$tag""".execute()()
      sql"""INSERT INTO tag VALUES ($location, $tag)""".execute()()
      count > 0
    }
  }

  def getItems(location: String, tag: String): Option[String] = {
    NamedDB(config.db.poolName).readOnly { implicit session =>
      val items = sql"""SELECT items FROM tag WHERE location=$location AND tag=$tag""".
        map(_.string(1)).headOption()()
      items
    }
  }

  def deleteItems(location: String, tag: String): Boolean = {
    NamedDB(config.db.poolName).autoCommit { implicit session =>
      val count = sql"""SELECT count(*) FROM tag WHERE location=$location AND tag=$tag""".map(_.int(1)).first()().get
      sql"""DELETE FROM tag WHERE location=$location AND tag=$tag""".execute()()
      count > 0
    }
  }
}
trait TagLinkConfig {
  val db = new DB{}
}

trait DB {
  def poolName: Symbol = 'taglink
  def setup() {
    Class.forName("org.h2.Driver")
    // ConnectionPool.add(poolName, "jdbc:h2:~/tmp/taglink", "sa", "")
    ConnectionPool.add(poolName, "jdbc:h2:mem:production;DB_CLOSE_DELAY=-1", "sa", "")
  }
}

object DBSetup {
  val logger = LoggerFactory.getLogger(DBSetup.getClass.getName)

  def setup()(implicit config: WithConfig) {
    NamedDB(config.db.poolName) autoCommit {implicit ses =>
      logger.warn("Creating database schema for TAGLINK.")
      sql"""CREATE TABLE tag (location VARCHAR(1024) NOT NULL,
                             tag VARCHAR(1024),
                             items VARCHAR(1024) NOT NULL
                             )""".execute()()
    }
  }
}