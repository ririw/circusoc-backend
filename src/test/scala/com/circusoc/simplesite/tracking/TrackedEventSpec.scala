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
import org.scalatest.Matchers._
import spray.json._
import scala.Some

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
      PageID("wat"),
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
      PageID("fooo"),
      t,
      new URL("http://www.google.com"),
      ActionSpec("HireNow", Some("UserCard"))
    )
    val f1 = TrackedEvent.trackEvent(event)
    val f2 = TrackedEvent.trackEvent(event.copy(
      sessionID=SessionID("bbb"),
      actionSpec=ActionSpec("HireNow", None)))
    Await.ready(f1, Duration.Inf)
    Await.ready(f2, Duration.Inf)
    val record = getConnection.createQueryTable(
      "action",
      "SELECT * FROM tracking.page_actions WHERE clientid = 'action' " +
        "ORDER BY sessionid")
    val expected = testDataSet.getTable("action")
    Assertion.assertEquals(expected, record)
  }

  "the PageViewClientEvent" should "correctly adjust timestamps" in {
    // Create an event in the past. 1 day ago, and a 5 second offset
    // then check it today, and see what's up. We should see it comming
    // from 5 seconds ago, or so
    val now = new time.DateTime()
    val event = PageViewClientEvent(
      "asd",
      "qwe",
      "trf",
      -5000,
      "http://www.google.com/home",
      None
    )
    val derivedEvent = event.pageView
    derivedEvent.clientID should be(ClientID("asd"))
    derivedEvent.sessionID should be(SessionID("qwe"))
    derivedEvent.page should be(new URL("http://www.google.com/home"))
    derivedEvent.referrer should be(None)
    new time.Duration(derivedEvent.timestamp, now).getMillis should be > -5500l
    new time.Duration(derivedEvent.timestamp, now).getMillis should be < -4500l
  }
  it should "deserialize correctly" in {
    import PageViewJsonReaders._

    val json =
      """{
        |  "clientID": "asd",
        |  "sessionID": "qwe",
        |  "pageID": "mew",
        |  "dt": -5000,
        |  "page": "http://www.google.com/home"
        |}""".stripMargin

    val event = PageViewClientEvent(
      "asd",
      "qwe",
      "mew",
      -5000,
      "http://www.google.com/home",
      None
    )
    json.parseJson.convertTo[PageViewClientEvent] should be(event)
    event.toJson.prettyPrint should be(json)

    val json2 =
      """{
        |  "clientID": "asd",
        |  "sessionID": "qwe",
        |  "pageID": "jer",
        |  "dt": -5000,
        |  "page": "http://www.google.com/home",
        |  "referrer": "http://www.bing.com"
        |}""".stripMargin
    val event2 = PageViewClientEvent(
      "asd", "qwe", "jer", -5000, "http://www.google.com/home", Some("http://www.bing.com")
    )
    json2.parseJson.convertTo[PageViewClientEvent] should be(event2)
    event2.toJson.prettyPrint should be(json2)
}


  "the PageViewActionEvent" should "correctly adjust timestamps" in {
    val now = new time.DateTime()
    val event = PageActionClientEvent(
      "asd",
      "qwe",
      "asdj",
      -5000,
      "http://www.google.com/home",
      "click",
      Some("test")
    )
    val derivedEvent = event.pageAction
    derivedEvent.clientID should be(ClientID("asd"))
    derivedEvent.sessionID should be(SessionID("qwe"))
    derivedEvent.page should be(new URL("http://www.google.com/home"))
    derivedEvent.actionSpec should be(ActionSpec("click", Some("test")))
    new time.Duration(derivedEvent.timestamp, now).getMillis should be > -5500l
    new time.Duration(derivedEvent.timestamp, now).getMillis should be < -4500l
  }

  it should "deserialize correctly" in {
    import PageViewJsonReaders._
    val json =
      """{
        |  "clientID": "asd",
        |  "sessionID": "qwe",
        |  "pageID": "red",
        |  "dt": -5000,
        |  "page": "http://www.google.com/home",
        |  "label": "click",
        |  "section": "test"
        |}""".stripMargin
    val event = PageActionClientEvent(
      "asd", "qwe", "red", -5000, "http://www.google.com/home", "click", Some("test")
    )
    json.parseJson.convertTo[PageActionClientEvent] should be(event)
    event.toJson.prettyPrint should be(json)

    val json2 =
      """{
        |  "clientID": "asd",
        |  "sessionID": "qwe",
        |  "pageID": "tgfr",
        |  "dt": -5000,
        |  "page": "http://www.google.com/home",
        |  "label": "click"
        |}""".stripMargin
    val event2 = PageActionClientEvent(
      "asd", "qwe", "tgfr", -5000, "http://www.google.com/home", "click", None
    )
    json2.parseJson.convertTo[PageActionClientEvent] should be(event2)
    event2.toJson.prettyPrint should be(json2)
  }

}

