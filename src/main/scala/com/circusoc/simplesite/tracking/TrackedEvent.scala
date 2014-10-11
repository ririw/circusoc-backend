package com.circusoc.simplesite.tracking

import org.joda.time
import java.net.URL
import scala.concurrent.{ExecutionContext, Future}
import com.circusoc.simplesite.WithConfig
import scalikejdbc._
import ExecutionContext.Implicits.global

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