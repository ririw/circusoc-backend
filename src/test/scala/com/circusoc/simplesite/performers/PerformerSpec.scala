package com.circusoc.simplesite.performers

import org.dbunit.DBTestCase
import org.scalatest.{BeforeAndAfter, FlatSpecLike}
import org.scalatest.prop.PropertyChecks
import org.scalatest.Matchers._
import java.sql.{DriverManager, Connection}
import com.circusoc.simplesite._
import org.dbunit.database.DatabaseConnection
import org.dbunit.operation.DatabaseOperation
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import scalikejdbc.ConnectionPool
import org.codemonkey.simplejavamail.Email

/**
 *
 */
class PerformerSpec extends DBTestCase with FlatSpecLike with BeforeAndAfter with PropertyChecks {
  implicit val config = new WithConfig {
    override val db: DB = new DB {
      override val poolName = 'performerspec
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
  }

  def getJDBC: Connection = {
    Class.forName("org.h2.Driver")
    val c = DriverManager.getConnection("jdbc:h2:mem:performerspec;DB_CLOSE_DELAY=-1", "sa", "")
    c.setAutoCommit(true)
    c
  }
  config.db.setup()
  DBSetup.setup()(config)

  val conn = new DatabaseConnection(getJDBC)
  DatabaseOperation.CLEAN_INSERT.execute(conn, getDataSet())

  override def getDataSet: IDataSet = new FlatXmlDataSetBuilder().
    build(classOf[PerformerSpec].
    getResourceAsStream("/com/circusoc/simplesite/performers/PerformerDBSpec.xml"))

  it should "get a performer by their ID" in {
    val _steve = Performer.getPerformerByID(1)
    assert(_steve.isDefined)
    val steve = _steve.get
    steve.id should be(1)
    steve.name should be("steve")
    steve.skills should be(Set(Skill("fire"), Skill("acro")))
    steve.profilePicture should be(PictureFromID(1))
    steve.otherPictures should be(Set(PictureFromID(2)))
    steve.shown should be(false)
  }

  it should "get another performer by their id" in {
    val _dale = Performer.getPerformerByID(2)
    assert(_dale.isDefined)
    val dale = _dale.get
    dale.id should be(2)
    dale.name should be("dale")
    dale.profilePicture should be(PictureFromID(2))
    dale.shown should be(true)
    dale.skills should be(Set(Skill("badminton")))
    dale.otherPictures should be(Set(PictureFromID(3), PictureFromID(4)))
  }

  it should "get a performer that has no skills" in {
    val _leela = Performer.getPerformerByID(3)
    assert(_leela.isDefined)
    val leela = _leela.get
    leela.id should be(3)
    leela.name should be("leela")
    leela.profilePicture should be(PictureFromID(4))
    leela.shown should be(true)
    leela.skills should be(Set())
    leela.otherPictures should be(Set(PictureFromID(5)))
  }

  it should "get a performer that has no pictures" in {
    val _carla = Performer.getPerformerByID(4)
    assert(_carla.isDefined)
    val carla = _carla.get
    carla.id should be(4)
    carla.name should be("Carla")
    carla.profilePicture should be(PictureFromID(6))
    carla.skills should be(Set(Skill("fire")))
    carla.otherPictures should be(Set())
  }

  it should "get a performer with no skills and no pictures" in {
    val _alexa = Performer.getPerformerByID(5)
    assert(_alexa.isDefined)
    val alexa = _alexa.get
    alexa.id should be(5)
    alexa.name should be("Alexa")
    alexa.profilePicture should be(PictureFromID(7))
    alexa.otherPictures should be(Set())
    alexa.skills should be(Set())
  }

  it should "not get other random performers" in {
    forAll {id: Long =>
      whenever(id > 5) {
        Performer.getPerformerByID(id) should be(None)
      }
    }
  }
}
