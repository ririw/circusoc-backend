package com.circusoc.simplesite

import java.net.URL
import java.util.Properties
import java.util.concurrent.TimeUnit

import com.codahale.metrics
import org.codemonkey.simplejavamail.{Mailer, Email}
import org.slf4j.LoggerFactory
import scalikejdbc._

trait WithConfig {
  val db: DB
  val hire: Hire
  val mailer: MailerLike
  val paths: PathConfig
  def stats: Stats = new Stats{}
  val isProduction = false
  def defaultPictureStream = this.getClass.getResourceAsStream("/com/circusoc/simplesite/pictures/defaultimage.jpg")
}

trait PropertiesConfig extends WithConfig {
  val properties: Properties
  override def defaultPictureStream = this.getClass.getResourceAsStream(properties.getProperty("defaultimage"))
  override val isProduction = properties.getProperty("isProduction").toBoolean
  override val db = new PropertiesConfigDB(properties)
  override val hire = new PropertiesConfigHire(properties)
  override val mailer = new PropertiesConfigMailer(properties, hire)
  override val paths = new PropertiesConfigPath(properties)
}

class Stats {
  def registry = new metrics.MetricRegistry()
  def pictureTime = registry.timer("Picture retrieval")
  def run(): Unit = {
    val reporter = metrics.ConsoleReporter.forRegistry(registry)
      .convertRatesTo(TimeUnit.SECONDS)
      .convertDurationsTo(TimeUnit.MILLISECONDS)
      .build()
    reporter.report()
    reporter.start(5, TimeUnit.SECONDS)
  }
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

class PropertiesConfigDB(properties: Properties) extends DB{
  override def setup(): Unit = {
    val dbpath = properties.getProperty("dbpath")
    val dbuser = properties.getProperty("dbuser")
    val dbpass = properties.getProperty("dbpass")
    Class.forName("org.h2.Driver")
    if (dbpass == null || dbpath == "" || dbpass == "mem")
      ConnectionPool.add(poolName, "jdbc:h2:mem:production;DB_CLOSE_DELAY=-1", "sa", "")
    else
      ConnectionPool.add(poolName, "jdbc:h2:" + dbpath, dbuser, dbpass)
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

class PropertiesConfigHire(properties: Properties) extends Hire {
  override val smtpHost: String        = properties.getProperty("smtpHost"       )
  override val smtpPort: Integer       = properties.getProperty("smtpPort"       ).toInt
  override val smtpUser: String        = properties.getProperty("smtpUser"       )
  override val smtpPass: String        = properties.getProperty("smtpPass"       )
  override val fromEmail: String       = properties.getProperty("fromEmail"      )
  override val fromName: String        = properties.getProperty("fromName"       )
  override val subject: String         = properties.getProperty("subject"        )
  override val gigManagerName: String  = properties.getProperty("gigManagerName" )
  override val gigManagerEmail: String = properties.getProperty("gigManagerEmail")
  override val emailText: String = scala.io.Source.fromInputStream(
    this.getClass.getResourceAsStream(properties.getProperty("emailtextPath"))).getLines().mkString("\n")
  override val emailHTML: String = scala.io.Source.fromInputStream(
    this.getClass.getResourceAsStream(properties.getProperty("emailhtmlPath"))).getLines().mkString("\n")

}

trait MailerLike {
  def sendMail(email: Email): Unit
}
class PropertiesConfigMailer(properties: Properties, hire: Hire) extends MailerLike{
  val logger = LoggerFactory.getLogger(classOf[PropertiesConfigMailer].getName)

  val sender = if (properties.getProperty("sendMail").toBoolean) {
    def m(email: Email): Unit = {
      val mailer = new Mailer(hire.smtpHost, hire.smtpPort, hire.smtpUser, hire.smtpPass)
      mailer.sendMail(email)
    }
    m _
  } else {
    def m(email: Email): Unit = {
      logger.info("Sending mail...")
    }
    m _
  }
  override def sendMail(email: Email): Unit = {
    def sendMail(email: Email): Unit = sender(email)
  }
}

// TODO: Cookies do not yet work.
trait PathConfig {
  def baseUrl: URL = new URL("http://localhost.com:8080")
  def cookieUrl: String = "localhost.com"
  def cdnUrl: URL = new URL("http://localhost")
}

class PropertiesConfigPath(properties: Properties) extends PathConfig {
  def baseURL: URL = new URL(properties.getProperty("baseURL"))
  def cookieURL: String = baseURL.getHost
  def cdnURL: URL = new URL(properties.getProperty("cdnURL"))
}