package com.circusoc.simplesite.hire

import com.circusoc.simplesite.WithConfig
import scala.concurrent.{ExecutionContext, Future}
import scalikejdbc._
import ExecutionContext.Implicits.global
import org.codemonkey.simplejavamail.Email
import javax.mail.Message.RecipientType
import org.slf4j.LoggerFactory
import spray.json.{RootJsonWriter, RootJsonReader, DefaultJsonProtocol}

object Hire {
  val logger = LoggerFactory.getLogger(Hire.getClass.getName)

  // Here we return from the hire request before
  // fully handling it, so the user doesn't have to wait for an
  // email send. What will happen is that the email is pushed
  // into a pending email queue, so that we can recover it
  // evenually.
  def hire(clientEmail: EmailAddress, location: Option[Location], skills: List[String])
          (implicit config: WithConfig): Future[Unit] = {
    val id = NamedDB(config.db.poolName).localTx {implicit session =>
      val loc = location.map(_.location)
      val k = sql"""INSERT INTO hirerequest (email  , location)
                    VALUES (${clientEmail.email}, $loc)""".updateAndReturnGeneratedKey()()
      for (skill <- skills) {
        sql"""insert into hirerequest_skill VALUES ($k, $skill)""".execute()()
      }
      k
    }
    Future {
      processHireRequest(id)
    }
  }

  def pendingHireQueueSize()(implicit config: WithConfig): Int = {
    NamedDB(config.db.poolName).readOnly {
      implicit session =>
      sql"select count(*) from hirerequest".map(_.int(1)).headOption().apply().getOrElse(0)
    }
  }

  def processPendingQueue()(implicit config: WithConfig): Unit = {
    val sendIds = NamedDB(config.db.poolName).readOnly {implicit session =>
      sql"select id from hirerequest".map(_.int(1)).toList().apply()
    }
    sendIds.map(processHireRequest(_))
  }

  def processHireRequest(id: Long)(implicit config: WithConfig): Unit = {
    logger.info(s"Processing email $id")
    NamedDB(config.db.poolName).localTx {implicit session =>
      // Puts us in serializable mode. This
      // is equivalent to a situation where there are
      // no overlapping transactions. So we should be safe
      // from problems.
      sql"SET LOCK_MODE 1".execute()()
      val emailLoc = sql"""select email, location
                           from hirerequest where id=$id""".
                           map{el => el.string(1) -> el.stringOpt(2)}.headOption()()
      emailLoc match {
        case None => Unit
        case Some((clientEmail, location)) =>
          val skills = sql"""select skill from hirerequest_skill where hirerequest_id=$id""".map(_.string(1)).toList()()
          sql"""delete from hirerequest where id=$id""".execute()()
          sql"""delete from hirerequest_skill where hirerequest_id=$id""".execute()()
          val email = new Email()
          email.setFromAddress(config.hire.fromName, config.hire.fromEmail)
          email.setSubject(config.hire.subject)
          email.addRecipient(config.hire.gigManagerName, config.hire.gigManagerEmail, RecipientType.TO)
          email.setText(config.hire.emailText.format(clientEmail,
                          location.getOrElse("NO LOCATION PROVIDED"),
                          skills.mkString(", ")))
          email.setTextHTML(config.hire.emailHTML.format(clientEmail,
                          location.getOrElse("NO LOCATION PROVIDED"),
                          skills.mkString(", ")))
          logger.info(s"Sending email $id")
          config.mailer.sendMail(email)
          logger.info(s"Sent email $id")
      }
    }
  }
}

case class EmailAddress(email: String) extends AnyVal
case class Location(location: String) extends AnyVal



case class HireRequest(email: String, location: Option[String], skills: List[String])
// $COVERAGE-OFF$
object HireRequestJsonSupport extends DefaultJsonProtocol {
  implicit val hireJsonReader: RootJsonReader[HireRequest] = jsonFormat3(HireRequest)
  implicit val hireJsonWriter: RootJsonWriter[HireRequest] = jsonFormat3(HireRequest)
}
// $COVERAGE-ON$

