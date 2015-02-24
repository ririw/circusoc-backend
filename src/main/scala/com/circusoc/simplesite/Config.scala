package com.circusoc.simplesite

import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

import com.codahale.metrics
import com.typesafe.config.{Config, ConfigFactory}
import org.codemonkey.simplejavamail.{Email, Mailer}
import org.slf4j.LoggerFactory
import scalikejdbc._

trait WithConfig {
  val port: Int
  val db: DB
  val hire: Hire
  val mailer: MailerLike
  val paths: PathConfig
  def stats: Stats = new Stats{}
  val isProduction = false
  def defaultPictureStream = this.getClass.getResourceAsStream("/com/circusoc/simplesite/pictures/defaultimage.jpg")
}

class PropertiesConfig() extends WithConfig {
  val logger = LoggerFactory.getLogger(classOf[PropertiesConfig].getName)
  val config: Config = {
    val configFile = new File("backend.conf")
    assert(configFile.canRead)
    ConfigFactory.parseFile(new File("backend.conf"))
  }
  override def defaultPictureStream = this.getClass.getResourceAsStream(config.getString("com.circusoc.defaultpicture"))
  override val isProduction = config.getBoolean("com.circusoc.isproduction")
  override val db = new PropertiesConfigDB(config)
  override val hire = new PropertiesConfigHire(config)
  override val mailer = new PropertiesConfigMailer(config, hire)
  override val paths = new PropertiesConfigPath(config)
  override val port: Int = config.getInt("com.circusoc.port")
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
    ConnectionPool.add(poolName, "ajdbc:h2:~/tmp/test", "sa", "")
    // ConnectionPool.add(poolName, "jdbc:h2:mem:production;DB_CLOSE_DELAY=-1", "sa", "")
  }
}

class PropertiesConfigDB(config: Config) extends DB {
  override def setup(): Unit = {
    val dbpath = config.getString("com.circusoc.db.path")
    val dbuser = config.getString("com.circusoc.db.user")
    val dbpass = config.getString("com.circusoc.db.pass")
    Class.forName("org.h2.Driver")
    if (dbpath == null || dbpath == "" || dbpath == "mem") {
      val url = s"jdbc:h2:mem:${poolName.name};DB_CLOSE_DELAY=-1"
      ConnectionPool.add(poolName, url, dbuser, dbpass)
    } else {
      ConnectionPool.add(poolName, "jdbc:h2:" + dbpath, dbuser, dbpass)
    }
  }
}

trait Hire {
  val smtpHost: String        = "mailtrap.io"
  val smtpPort: Integer       = 2525
  val smtpUser: String        = "24485bf9f4db6d2e4"
  val smtpPass: String        = "3dd1b073699411"
  val fromEmail: String       = "gigs@circusoc.com"
  val fromName: String        = "gigs"
  val subject: String         = "Someone wants to hire us!"
  val gigManagerName: String  = "Richard"
  val gigManagerEmail: String = "richard@circusoc.com"
  val emailText: String = scala.io.Source.fromInputStream(
    this.getClass.getResourceAsStream("/com/circusoc/simplesite/hire/email.txt")).getLines().mkString("\n")
  val emailHTML: String = scala.io.Source.fromInputStream(
    this.getClass.getResourceAsStream("/com/circusoc/simplesite/hire/email.html")).getLines().mkString("\n")
}

class PropertiesConfigHire(config: Config) extends Hire {
  override val smtpHost: String        = config.getString("com.circusoc.hire.smtpHost"       )
  override val smtpPort: Integer       = config.getInt   ("com.circusoc.hire.smtpPort"       )
  override val smtpUser: String        = config.getString("com.circusoc.hire.smtpUser"       )
  override val smtpPass: String        = config.getString("com.circusoc.hire.smtpPass"       )
  override val fromEmail: String       = config.getString("com.circusoc.hire.fromEmail"      )
  override val fromName: String        = config.getString("com.circusoc.hire.fromName"       )
  override val subject: String         = config.getString("com.circusoc.hire.subject"        )
  override val gigManagerName: String  = config.getString("com.circusoc.hire.gigManagerName" )
  override val gigManagerEmail: String = config.getString("com.circusoc.hire.gigManagerEmail")
  override val emailText: String = scala.io.Source.fromInputStream(
    this.getClass.getResourceAsStream(config.getString("com.circusoc.hire.emailtextPath"))).getLines().mkString("\n")
  override val emailHTML: String = scala.io.Source.fromInputStream(
    this.getClass.getResourceAsStream(config.getString("com.circusoc.hire.emailhtmlPath"))).getLines().mkString("\n")

}

trait MailerLike {
  def sendMail(email: Email): Unit
}
class PropertiesConfigMailer(config: Config, hire: Hire) extends MailerLike{
  val logger = LoggerFactory.getLogger(classOf[PropertiesConfigMailer].getName)

  val sender = if (config.getBoolean("com.circusoc.sendMail")) {
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

class PropertiesConfigPath(config: Config) extends PathConfig {
  override def baseUrl: URL = new URL(config.getString("com.circusoc.paths.baseURL"))
  override def cookieUrl: String = config.getString("com.circusoc.paths.cookieURL")
  override def cdnUrl: URL = new URL(config.getString("com.circusoc.paths.cdnURL"))
}