package com.circusoc.simplesite

import java.net.URL

import org.codemonkey.simplejavamail.Email
import scalikejdbc._

trait WithConfig {
  val db: DB
  val hire: Hire
  val mailer: MailerLike
  val paths: PathConfig
  val isProduction = false
  def defaultPictureStream = this.getClass.getResourceAsStream("/com/circusoc/simplesite/pictures/defaultimage.jpg")
}

trait DB {
  def poolName: Symbol = 'production
  def getDB: NamedDB = NamedDB(poolName)
  def setup() {
    Class.forName("org.h2.Driver")
    ConnectionPool.add(poolName, "jdbc:h2:~/tmp/test", "sa", "")
    // ConnectionPool.add(poolName, "jdbc:h2:mem:production;DB_CLOSE_DELAY=-1", "sa", "")
  }
}

trait Hire {
  val smtpHost: String = "mailtrap.io"
  val smtpPort: Integer = 2525
  val smtpUser: String = "24485bf9f4db6d2e4"
  val smtpPass: String = "3dd1b073699411"
  val fromEmail: String = "gigs@circusoc.com"
  val fromName: String = "gigs"
  val subject: String = "Someone wants to hire us!"
  val gigManagerName: String = "Richard"
  val gigManagerEmail: String = "richard@circusoc.com"
  val emailText: String = scala.io.Source.fromInputStream(
    this.getClass.getResourceAsStream("/com/circusoc/simplesite/hire/email.txt")).getLines().mkString("\n")
  val emailHTML: String = scala.io.Source.fromInputStream(
    this.getClass.getResourceAsStream("/com/circusoc/simplesite/hire/email.html")).getLines().mkString("\n")
}

trait MailerLike {
  def sendMail(email: Email): Unit
}
trait PathConfig {
  def baseUrl: URL = new URL("https://localhost:8080")
  def cdnUrl: URL = baseUrl
}