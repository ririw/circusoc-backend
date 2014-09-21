package com.circusoc.simplesite.hire

import com.circusoc.simplesite.WithConfig
import scala.concurrent.{ExecutionContext, Future}
import scalikejdbc._
import ExecutionContext.Implicits.global
import org.codemonkey.simplejavamail.Email
import javax.mail.Message.RecipientType

object Hire {
  def hire(clientEmail: EmailAddress, location: Location, skills: List[String])
          (implicit config: WithConfig): Future[Unit] = {
    val id = NamedDB(config.db.symbol).localTx {implicit session =>
      val k = sql"""INSERT INTO hirerequest (email  , location)
                    VALUES (${clientEmail.email}, ${location.location})""".updateAndReturnGeneratedKey()()
      for (skill <- skills) {
        sql"""insert into hirerequest_skill VALUES ($k, $skill)""".execute()()
      }
      k
    }
    Future {
      // probably should log this...
      processHireRequest(id)
    }
  }

  def pendingHireQueueSize()(implicit config: WithConfig): Int = {
    NamedDB(config.db.symbol).readOnly {
      implicit session =>
      sql"select count(*) from hirerequest".map(_.int(1)).headOption().apply().getOrElse(0)
    }
  }

  def processPendingQueue()(implicit config: WithConfig): Unit = {
    val sendIds = NamedDB(config.db.symbol).readOnly {implicit session =>
      sql"select id from hirerequest".map(_.int(1)).toList().apply()
    }
    sendIds.map(processHireRequest(_))
  }

  def processHireRequest(id: Long)(implicit config: WithConfig): Unit = {
    NamedDB(config.db.symbol).localTx {implicit session =>
      // Puts us in serializable mode. This
      // is equivalent to a situation where there are
      // no overlapping transactions. So we should be safe
      // from problems.
      sql"SET LOCK_MODE 1".execute()()
      val emailLoc = sql"""select email, location
                           from hirerequest where id=$id""".
                           map{el => el.string(1) -> el.string(2)}.headOption()()
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
          email.setText(config.hire.emailText.format(clientEmail, location, skills.mkString(", ")))
          email.setTextHTML(config.hire.emailHTML.format(clientEmail, location, skills.mkString(", ")))
          config.mailer.sendMail(email)
      }
    }
  }
}

case class EmailAddress(email: String) extends AnyVal
case class Location(location: String) extends AnyVal
