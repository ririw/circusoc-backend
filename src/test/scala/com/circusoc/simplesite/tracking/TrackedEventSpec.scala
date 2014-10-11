package com.circusoc.simplesite.tracking

import org.dbunit.{PropertiesBasedJdbcDatabaseTester, Assertion, DBTestCase}
import org.scalatest.{BeforeAndAfter, FlatSpecLike}
import org.scalatest.prop.PropertyChecks
import com.circusoc.simplesite._
import scalikejdbc.ConnectionPool
import org.codemonkey.simplejavamail.Email
import java.net.URL
import java.sql.{DriverManager, Connection}
import org.dbunit.database.DatabaseConnection
import org.dbunit.operation.DatabaseOperation
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import org.joda.time
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 *
 */
class TrackedEventSpec extends DBTestCase with FlatSpecLike with BeforeAndAfter with PropertyChecks {
  implicit val config = new WithConfig {
    override val db: DB = new DB {
      override val poolName = 'trackedeventspec
      override def setup() = {
        Class.forName("org.h2.Driver")
        val url = s"jdbc:h2:mem:${poolName.name};DB_CLOSE_DELAY=-1"
        ConnectionPool.add(poolName, url, "sa", "")
      }
    }
    override val hire: Hire = new Hire {}
    override val mailer: MailerLike = new MailerLike {
      override def sendMail(email: Email): Unit = throw new NotImplementedError()
    }
    override val paths: PathConfig = new PathConfig {}
  }

  def getJDBC: Connection = {
    Class.forName("org.h2.Driver")
    DriverManager.getConnection("jdbc:h2:mem:trackedeventspec;DB_CLOSE_DELAY=-1", "sa", "")
  }

  System.setProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_DRIVER_CLASS, "org.h2.Driver")
  System.setProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_CONNECTION_URL, "jdbc:h2:mem:trackedeventspec;DB_CLOSE_DELAY=-1")
  System.setProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_USERNAME, "sa")
  System.setProperty(PropertiesBasedJdbcDatabaseTester.DBUNIT_PASSWORD, "")

  config.db.setup()
  DBSetup.setup()(config)

  val conn = new DatabaseConnection(getJDBC)
  DatabaseOperation.CLEAN_INSERT.execute(conn, getDataSet())

  override def getDataSet: IDataSet = new FlatXmlDataSetBuilder().
    build(classOf[TrackedEventSpec].
    getResourceAsStream("/com/circusoc/simplesite/tracking/TrackedEventSpec.xml"))

  val testDataSet: IDataSet = new FlatXmlDataSetBuilder().
    build(classOf[TrackedEventSpec].
    getResourceAsStream("/com/circusoc/simplesite/tracking/TrackedEventSpec_results.xml"))


  it should "record page views (with and without referrers)" in {
    val t = new time.DateTime(2014, 1, 1, 0, 0)

    val event = PageView(
      ClientID("page_view"),
      SessionID("ewq"),
      t,
      new URL("http://www.google.com"),
      Some(new URL("http://www.bing.com"))
    )
    val f1 = TrackedEvent.trackEvent(event)
    val f2 = TrackedEvent.trackEvent(event.copy(
      referrer = None,
      sessionID = SessionID("qwe")
    ))
    Await.ready(f1, Duration.Inf)
    Await.ready(f2, Duration.Inf)
    val record = getConnection.createQueryTable(
      "page_view",
      "SELECT * FROM tracking.page_views WHERE clientid = 'page_view' " +
        "ORDER BY sessionid")
    val expected = testDataSet.getTable("page_view")
    Assertion.assertEquals(expected, record)
  }

  it should "record actions (with and without sections)" in {
    val t = new time.DateTime(2014, 1, 1, 0, 0)

    val event = PageAction(
      ClientID("action"),
      SessionID("aaa"),
      t,
      new URL("http://www.google.com"),
      ActionSpec("HireNow", Some("UserCard"))
    )
    val f1 = TrackedEvent.trackEvent(event)
    val f2 = TrackedEvent.trackEvent(event.copy(actionSpec=ActionSpec("HireNow", None)))
    Await.ready(f1, Duration.Inf)
    Await.ready(f2, Duration.Inf)
    val record = getConnection.createQueryTable(
      "action",
      "SELECT * FROM tracking.page_actions WHERE clientid = 'action' " +
        "ORDER BY sessionid")
    val expected = testDataSet.getTable("action")
    Assertion.assertEquals(expected, record)
  }
}

