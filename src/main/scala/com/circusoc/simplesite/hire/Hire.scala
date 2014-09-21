package com.circusoc.simplesite.hire

import com.circusoc.simplesite.WithConfig
import scala.concurrent.{ExecutionContext, Future}
import scalikejdbc._
import ExecutionContext.Implicits.global
import org.codemonkey.simplejavamail.{Mailer, Email}
import javax.mail.Message.RecipientType

object Hire {
  def hire(clientEmail: EmailAddress, location: Location, skills: List[String])(implicit config: WithConfig): Future[Unit] = {
    val id = NamedDB(config.db.symbol).localTx {implicit session =>
      val k = sql"""INSERT INTO hirerequest (email  , location)
                    VALUES (${clientEmail.email}, ${location.location})""".updateAndReturnGeneratedKey()()
      for (skill <- skills) {
        sql"""insert into hirerequest_skill VALUES ($k, $skill)""".execute()()
      }
      k
    }
    Future {
      // A failure to send will rollback the transaction
      // so it'll still be there in the end.
      // probably should log this...
      NamedDB(config.db.symbol).localTx {implicit session =>
        sql"""delete from hirerequest where id=$id""".execute()()
        sql"""delete from hirerequest_skill where hirerequest_id=$id""".execute()()
        val email = new Email()
        email.setFromAddress(config.hire.fromName, config.hire.fromEmail)
        email.setSubject(config.hire.subject)
        email.addRecipient(config.hire.gigManagerName, config.hire.gigManagerEmail, RecipientType.TO)
        email.setText(config.hire.emailText.format(clientEmail.email, location.location, skills.mkString(", ")))
        email.setTextHTML(config.hire.emailHTML.format(clientEmail.email, location.location, skills.mkString(", ")))
        new Mailer(config.hire.smtpHost, config.hire.smtpPort, config.hire.smtpUser, config.hire.smtpPass).sendMail(email)
      }
    }
  }
}

case class EmailAddress(email: String) extends AnyVal
case class Location(location: String) extends AnyVal
