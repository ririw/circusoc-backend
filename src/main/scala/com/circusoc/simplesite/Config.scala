package com.circusoc.simplesite

import java.sql.{Connection, DriverManager}
import scalikejdbc._
trait WithConfig {
  val db: DB
  val hire: Hire
}

trait DB {
  def symbol: Symbol = 'production
  def setup() {
    //Class.forName("org.h2.Driver")
    //ConnectionPool.singleton("jdbc:h2:~/test", "sa", "")
    //ConnectionPool.add('foo, "jdbc:h2:~/test", "sa", "")
    ???
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