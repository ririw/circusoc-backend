package com.circusoc.taglink

import java.sql.{Connection, DriverManager}

import akka.actor.{ActorSystem, ActorRefFactory}
import com.circusoc.simplesite._
import com.circusoc.simplesite.auth.AuthService
import com.circusoc.testgraph.testgraph._
import com.circusoc.taglink._
import org.codemonkey.simplejavamail.{Email, Mailer}
import org.dbunit.DBTestCase
import org.dbunit.database.DatabaseConnection
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import org.dbunit.operation.DatabaseOperation
import org.scalatest.FlatSpecLike
import scalikejdbc.ConnectionPool

class TagLinkSpec extends DBTestCase with FlatSpecLike {
  implicit val tlconfig = new TagLinkConfig {
    override val taglinkDB: DB = new DB {
      override val poolName = 'taglinkspec
      override def setup() = {
        Class.forName("org.h2.Driver")
        val url = s"jdbc:h2:mem:${poolName.name};DB_CLOSE_DELAY=-1"
        ConnectionPool.add(poolName, url, "sa", "")
      }
    }
  }
  val sysConfig = new WithConfig {
    override val isProduction = false
    override val db: com.circusoc.simplesite.DB = new com.circusoc.simplesite.DB {
      override def poolName: Symbol = 'taglinkspecprod

      override def setup() = {
        Class.forName("org.h2.Driver")
        val url = s"jdbc:h2:mem:${poolName.name};DB_CLOSE_DELAY=-1"
        ConnectionPool.add(poolName, url, "sa", "")
      }
    }
    override val hire: Hire = new Hire {}
    override val mailer: MailerLike = new MailerLike {
      val mailer = new Mailer(hire.smtpHost, hire.smtpPort, hire.smtpUser, hire.smtpPass)

      override def sendMail(email: Email): Unit = {
        Thread.sleep(500)
        println("Sent mail")
      }

      //mailer.sendMail(email)
    }
    override val paths: PathConfig = new PathConfig {}
  }

  implicit val testtools = new TaglinkTestTools {
    override def server = new TagLinkServer with TagLinkConfig with AuthService with Core {
      override implicit def actorRefFactory: ActorRefFactory = null
      override val taglinkDB = tlconfig.taglinkDB
      override implicit val config = sysConfig

      override protected implicit def system: ActorSystem = null
    }
  }

  implicit val foo = testtools.testContentLinker
  implicit val foo2 = testtools.TagLocationLinker


  def getJDBC: Connection = {
    Class.forName("org.h2.Driver")
    val c = DriverManager.getConnection("jdbc:h2:mem:taglinkspec;DB_CLOSE_DELAY=-1", "sa", "")
    c.setAutoCommit(true)
    c
  }

  tlconfig.taglinkDB.setup()
  DBSetup.setup()(tlconfig)

  val conn = new DatabaseConnection(getJDBC)
  DatabaseOperation.CLEAN_INSERT.execute(conn, getDataSet())

  override def getDataSet: IDataSet = new FlatXmlDataSetBuilder().
    build(classOf[TagLinkSpec].
    getResourceAsStream("/com/circusoc/taglink/TagLinkDBSpec.xml"))


  it should "correctly add items to the set of tags" in {
    val contentFactory = new TaglinkContentFactory()
    val locationFactory = new TaglinkLocationFactory()
    val tagFactory = new TaglinkTagFactory()
    val content = (0 to 20).map {_ => contentFactory.randomNode()}.toList
    val locations = (0 to 10).map {_ => locationFactory.randomNode()}.toList
    val tags = (0 to 20).map {_ => tagFactory.randomNode()}.toList
    val locationsAndTags = locations.join.randomSurjectionJoin(tags)
    val locTagsandContent = locationsAndTags.join.bijectiveJoin(content)
    for (node <- locTagsandContent) {
      val testLocTag = node.from.node
      val testLoc = testLocTag.location
      val testTag = testLocTag.tag
      val testContent = node.to.node
      assert(testtools.server.getItem(testLoc.name.name, testTag.name.name).exists(_ == testContent.content))
    }
  }
  it should "correctly overwrite items" in {
    val contentFactory = new TaglinkContentFactory()
    val locationFactory = new TaglinkLocationFactory()
    val tagFactory = new TaglinkTagFactory()
    val content = List.fill(20)(contentFactory.randomNode)
    val locations = List.fill(10)(locationFactory.randomNode)
    val tags = List.fill(20)(tagFactory.randomNode)
    val locationsAndTags = locations.join.randomSurjectionJoin(tags)
    val locTagsandContent = locationsAndTags.join.bijectiveJoin(content)

    val extraContent = List.fill(20)(contentFactory.randomNode)
    val extraJoins = locationsAndTags.join.bijectiveJoin(extraContent)

    for (node <- extraJoins) {
      val testLocTag = node.from.node
      val testLoc = testLocTag.location
      val testTag = testLocTag.tag
      val testContent = node.to.node
      assert(testtools.server.getItem(testLoc.name.name, testTag.name.name).exists(_ == testContent.content))
    }
  }
}
