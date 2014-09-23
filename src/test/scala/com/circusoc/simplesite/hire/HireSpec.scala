package com.circusoc.simplesite.hire

import org.dbunit.DBTestCase
import org.scalatest.{BeforeAndAfter, FlatSpecLike}
import com.circusoc.simplesite._
import scalikejdbc.ConnectionPool
import java.sql.{DriverManager, Connection}
import org.dbunit.database.DatabaseConnection
import org.dbunit.operation.DatabaseOperation
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.codemonkey.simplejavamail.{Mailer, Email}
import org.scalatest.Matchers._

class HireSpec extends DBTestCase with FlatSpecLike with BeforeAndAfter {
  val config = new PartialConfig({_ => Unit})

  def getJDBC(): Connection = {
    Class.forName("org.h2.Driver")
    val c =DriverManager.getConnection("jdbc:h2:mem:hirespec;DB_CLOSE_DELAY=-1", "sa", "")
    c.setAutoCommit(true)
    c
  }
  config.db.setup()
  DBSetup.setup()(config)

  val conn = new DatabaseConnection(getJDBC())
  DatabaseOperation.CLEAN_INSERT.execute(conn, getDataSet())

  override def getDataSet: IDataSet = new FlatXmlDataSetBuilder().
    build(classOf[HireSpec].
    getResourceAsStream("/com/circusoc/simplesite/users/UserDBSpec.xml"))

  it should "send emails and delete their log entries" in {
    var sends = 0
    def mockSend(e: Email) {
      sends += 1
    }
    implicit val mockConfig = new PartialConfig(mockSend)
    val hire = Hire.hire(EmailAddress("richard@example.com"), Some(Location("sydney")), List("Fire", "Juggles"))
    Await.result(hire, Duration.Inf)
    sends should be(1)
    Hire.pendingHireQueueSize() should be(0)
  }


  it should "send emails and delete their log entries with non locations" in {
    var sends = 0
    def mockSend(e: Email) {
      sends += 1
    }
    implicit val mockConfig = new PartialConfig(mockSend)
    val hire = Hire.hire(EmailAddress("richard@example.com"), None, List("Fire", "Juggles"))
    Await.result(hire, Duration.Inf)
    sends should be(1)
    Hire.pendingHireQueueSize() should be(0)
  }

  it should "queue emails if they don't send and then send them" in {
    var badSends = 0
    def badSend(e: Email) {
      badSends += 1
      throw new Exception()
    }
    val mockConfig1 = new PartialConfig(badSend)
    val hire = Hire.hire(EmailAddress("steve@example.com"), Some(Location("sydney")), List("Fire", "Juggles"))(mockConfig1)
    intercept[Exception]{
      Await.result(hire, Duration.Inf)
    }
    Hire.pendingHireQueueSize()(mockConfig1) should be(1)

    var goodSends = 0
    def goodSend(e: Email) {
      goodSends += 1
    }
    val mockConfig2 = new PartialConfig(goodSend)
    Hire.processPendingQueue()(mockConfig2)

    Hire.pendingHireQueueSize()(mockConfig2) should be(0)
    goodSends should be(1)
  }

  it should "send nothing when there is nothing to send" in {
    var goodSends = 0
    def goodSend(e: Email) {
      goodSends += 1
    }
    val mockConfig2 = new PartialConfig(goodSend)
    Hire.pendingHireQueueSize()(mockConfig2) should be(0)
    Hire.processPendingQueue()(mockConfig2)

    Hire.pendingHireQueueSize()(mockConfig2) should be(0)
    goodSends should be(0)
  }
  it should "not send made up emails" in {
    var goodSends = 0
    def goodSend(e: Email) {
      goodSends += 1
    }
    val mockConfig2 = new PartialConfig(goodSend)
    Hire.pendingHireQueueSize()(mockConfig2) should be(0)
    Hire.processHireRequest(-1)(mockConfig2)
    goodSends should be(0)
  }

  it should "really send an email" ignore {
    val mailer = new Mailer(config.hire.smtpHost, config.hire.smtpPort, config.hire.smtpUser, config.hire.smtpPass)
    def sendMail(email: Email): Unit = mailer.sendMail(email)
    val realConfig = new PartialConfig(sendMail)
    val hire = Hire.hire(EmailAddress("steve@example.com"), Some(Location("sydney")), List("Fire", "Juggles"))(realConfig)
    Await.result(hire, Duration.Inf)
  }
}

class PartialConfig(mockMailer: Email => Unit) extends WithConfig {
  override val db: DB = new DB {
    override val poolName = 'hirespec
    override def setup() = {
      Class.forName("org.h2.Driver")
      val url = s"jdbc:h2:mem:${poolName.name};DB_CLOSE_DELAY=-1"
      ConnectionPool.add(poolName, url, "sa", "")
    }
  }
  override val hire: Hire = new Hire {}
  override val mailer: MailerLike = new MailerLike{
    override def sendMail(email: Email): Unit = mockMailer(email)
  }
}
