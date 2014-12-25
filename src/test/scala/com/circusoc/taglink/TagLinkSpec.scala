package com.circusoc.taglink

import java.sql.{Connection, DriverManager}

import akka.actor.{ActorRefFactory, ActorSystem}
import com.circusoc.simplesite._
import com.circusoc.simplesite.services.AuthService
import com.circusoc.testgraph.testgraph._
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

  val server = new TagLinkServer with TagLinkConfig {
    override val taglinkDB = tlconfig.taglinkDB
  }

  implicit val foo = new TestContentLinker(server)
  implicit val foo2 = new TagLocationLinker()


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
      assert(server.getItem(testLoc.name.name, testTag.name.name).exists(_ == testContent.content))
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
      assert(server.getItem(testLoc.name.name, testTag.name.name).exists(_ == testContent.content))
    }
  }
  it should "correctly delete items" in {
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
      assert(server.getItem(testLoc.name.name, testTag.name.name).exists(_ == testContent.content))
    }
    for (loctag <- locationsAndTags) {
      server.deleteItems(loctag.joinResult.location.name.name, loctag.joinResult.tag.name.name)
    }
    for (node <- locTagsandContent) {
      val testLocTag = node.from.node
      val testLoc = testLocTag.location
      val testTag = testLocTag.tag
      val testContent = node.to.node
      assert(server.getItem(testLoc.name.name, testTag.name.name).isEmpty)
    }
  }
}
