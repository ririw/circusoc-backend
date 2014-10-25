package com.circusoc.simplesite.tracking

import org.joda.time
import java.net.URL
import scala.concurrent.{ExecutionContext, Future}
import com.circusoc.simplesite.WithConfig
import scalikejdbc._
import ExecutionContext.Implicits.global
import spray.json._
import scalikejdbc.NamedDB

/**
 * We may track a specific set of things:
 *  * Page views, including: Route, Referrer
 *  * On page actions, including: Clicks, Hover Events
 *
 * Clients are clients, they identify a device
 *
 * Sessions are individual use periods, for example,
 * the life of a tab in a browser. I suggest using sessionStorage
 */
trait TrackedEvent {
  val clientID: ClientID
  val sessionID: SessionID
  val timestamp: time.DateTime
  val page: URL
}

case class PageView(
  clientID: ClientID,
  sessionID: SessionID,
  timestamp: time.DateTime,
  page: URL,
  referrer: Option[URL]
) extends TrackedEvent

case class PageAction(
  clientID: ClientID,
  sessionID: SessionID,
  timestamp: time.DateTime,
  page: URL,
  actionSpec: ActionSpec
)

case class ActionSpec(label: String, section: Option[String])
case class SessionID(sessionID: String) extends AnyVal
case class ClientID(clientID: String) extends AnyVal

object TrackedEvent {
  def trackEvent(event: PageView)(implicit config: WithConfig): Future[Unit] = {
    Future {
      NamedDB(config.db.poolName).autoCommit {
        implicit session =>
          sql"""
            INSERT INTO tracking.page_views VALUES (
              ${event.clientID.clientID},
              ${event.sessionID.sessionID},
              ${event.timestamp},
              ${event.page.toString},
              ${event.referrer.map(_.toString)}
            )
          """.execute()()
      }
    }
  }

  def trackEvent(event: PageAction)(implicit config: WithConfig): Future[Unit] = {
    Future {
      NamedDB(config.db.poolName).autoCommit {
        implicit session =>
          sql"""
            INSERT INTO tracking.page_actions VALUES (
              ${event.clientID.clientID},
              ${event.sessionID.sessionID},
              ${event.timestamp},
              ${event.page.toString},
              ${event.actionSpec.label},
              ${event.actionSpec.section}
          )""".execute()()
      }
    }
  }
}

case class PageViewClientEvent(
  clientID: String,
  sessionID: String,
  dt: Long,
  page: String,
  referrer: Option[String]
) {
  def pageView = {
    PageView(
      ClientID(clientID),
      SessionID(sessionID),
      new time.DateTime().minus(dt),
      new URL(page),
      referrer.map(s => new URL(s)))
  }
}
case class PageActionClientEvent(
  clientID: String,
  sessionID: String,
  dt: Long,
  page: String,
  label: String,
  section: Option[String]
) {
  def pageAction = {
    PageAction(
      ClientID(clientID),
      SessionID(sessionID),
      new time.DateTime().minus(dt),
      new URL(page),
      ActionSpec(label, section))
  }
}

object PageViewJsonReaders extends DefaultJsonProtocol {
  implicit val pageViewReader: RootJsonReader[PageViewClientEvent] = jsonFormat5(PageViewClientEvent)
  implicit val pageViewWriter: RootJsonWriter[PageViewClientEvent] = jsonFormat5(PageViewClientEvent)
  implicit val pageActionReader: RootJsonReader[PageActionClientEvent] = jsonFormat6(PageActionClientEvent)
  implicit val pageActionWriter: RootJsonWriter[PageActionClientEvent] = jsonFormat6(PageActionClientEvent)
}
