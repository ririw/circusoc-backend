package com.circusoc.taglink

import spray.routing.Directives._
import org.slf4j.LoggerFactory
import scalikejdbc.{ConnectionPool, NamedDB, _}
import spray.http.{HttpResponse, MediaTypes, StatusCodes}
import spray.httpx.unmarshalling.BasicUnmarshallers
import spray.json._

trait TagLinkServer {
  this: TagLinkConfig =>

  val taglinkRoutes = {
    path("taglink" / Segment / Segment) {(location, tag) =>
      get {
        respondWithMediaType(MediaTypes.`application/json`)
        complete {
          getItem(location, tag)
        }
      } ~
      put {
        entity(BasicUnmarshallers.StringUnmarshaller) { bodyJson: String =>
          complete {
            try {
              val json = bodyJson.parseJson
              val compacted = json.compactPrint
              val updated = putItem(location, tag, compacted)
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
        spray.routing.directives.MethodDirectives.delete {
        complete {
          val was404 = deleteItems(location, tag)
          if (was404) StatusCodes.NotFound
          else StatusCodes.OK
        }
      }
    }
  }

  def putItem(location: String, tag: String, item: String): Boolean = {
    NamedDB(taglinkDB.poolName).localTx { implicit session =>
      val count = sql"""SELECT count(*) FROM tag WHERE location=$location AND tag=$tag""".map(_.int(1)).first()().get
      sql"""DELETE FROM tag WHERE location=$location AND tag=$tag""".execute()()
      sql"""INSERT INTO tag VALUES ($location, $tag, $item)""".execute()()
      count > 0
    }
  }

  def getItem(location: String, tag: String): Option[String] = {
    NamedDB(taglinkDB.poolName).readOnly { implicit session =>
      val items = sql"""SELECT items FROM tag WHERE location=$location AND tag=$tag""".
        map(_.string(1)).headOption()()
      items
    }
  }

  def deleteItems(location: String, tag: String): Boolean = {
    NamedDB(taglinkDB.poolName).autoCommit { implicit session =>
      val count = sql"""SELECT count(*) FROM tag WHERE location=$location AND tag=$tag""".map(_.int(1)).first()().get
      sql"""DELETE FROM tag WHERE location=$location AND tag=$tag""".execute()()
      count > 0
    }
  }
}

trait TagLinkConfig {
  val taglinkDB = new DB{}
}

trait DB {
  def poolName: Symbol = 'taglink
  def setup() {
    Class.forName("org.h2.Driver")
    // ConnectionPool.add(poolName, "jdbc:h2:~/tmp/taglink", "sa", "")
    ConnectionPool.add(poolName, "jdbc:h2:mem:taglink;DB_CLOSE_DELAY=-1", "sa", "")
  }
}

object DBSetup {
  val logger = LoggerFactory.getLogger(DBSetup.getClass.getName)

  def setup()(implicit config: TagLinkConfig) {
    NamedDB(config.taglinkDB.poolName) autoCommit {implicit ses =>
      logger.warn("Creating database schema for TAGLINK.")
      sql"""CREATE TABLE tag (location VARCHAR(1024) NOT NULL,
                             tag VARCHAR(1024),
                             items VARCHAR(1024) NOT NULL
                             )""".execute()()
    }
  }
}