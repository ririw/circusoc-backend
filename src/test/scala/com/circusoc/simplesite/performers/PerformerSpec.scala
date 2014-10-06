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
import com.circusoc.simplesite.pictures.{PictureJsonFormatter, Picture}

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
    steve.profilePicture should be(Picture(1))
    steve.otherPictures should be(Set(Picture(2)))
    steve.shown should be(false)
  }

  it should "get another performer by their id" in {
    val _dale = Performer.getPerformerByID(2)
    assert(_dale.isDefined)
    val dale = _dale.get
    dale.id should be(2)
    dale.name should be("dale")
    dale.profilePicture should be(Picture(2))
    dale.shown should be(true)
    dale.skills should be(Set(Skill("badminton")))
    dale.otherPictures should be(Set(Picture(3), Picture(4)))
  }

  it should "get a performer that has no skills" in {
    val _leela = Performer.getPerformerByID(3)
    assert(_leela.isDefined)
    val leela = _leela.get
    leela.id should be(3)
    leela.name should be("leela")
    leela.profilePicture should be(Picture(4))
    leela.shown should be(true)
    leela.skills should be(Set())
    leela.otherPictures should be(Set(Picture(5)))
  }

  it should "get a performer that has no pictures" in {
    val _carla = Performer.getPerformerByID(4)
    assert(_carla.isDefined)
    val carla = _carla.get
    carla.id should be(4)
    carla.name should be("Carla")
    carla.profilePicture should be(Picture(6))
    carla.skills should be(Set(Skill("fire")))
    carla.otherPictures should be(Set())
  }

  it should "get a performer with no skills and no pictures" in {
    val _alexa = Performer.getPerformerByID(5)
    assert(_alexa.isDefined)
    val alexa = _alexa.get
    alexa.id should be(5)
    alexa.name should be("Alexa")
    alexa.profilePicture should be(Picture(7))
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

  "the performer serialization code" should "serialize a full performer" in {
    import spray.json._
    implicit val implperf = new PerformerJsonFormat()
    val steve = Performer.getPerformerByID(1).get.toJson

    val expected = JsObject(
      "id" -> JsNumber(1),
      "name" -> JsString("steve"),
      "skills" -> JsArray(JsString("fire"), JsString("acro")),
      "profile_picture" -> JsString("http://example.com/1"),
      "other_pictures" -> JsArray(JsString("http://example.com/2")),
      "shown" -> JsBoolean(false)
    )
    steve should be(expected)
  }
  "the performer serialization code" should "serialize another performer" in {
    import spray.json._
    implicit val implperf = new PerformerJsonFormat()
    val dale = Performer.getPerformerByID(2).get.toJson

    val expected = JsObject(
      "id" -> JsNumber(2),
      "name" -> JsString("dale"),
      "skills" -> JsArray(JsString("badminton")),
      "profile_picture" -> JsString("http://example.com/2"),
      "other_pictures" -> JsArray(
        JsString("http://example.com/3"),
        JsString("http://example.com/4")),
      "shown" -> JsBoolean(true)
    )
    dale should be(expected)
  }
  "The deserialization code" should "deserialize a performer" in {
    import spray.json._
    implicit val implperf = new PerformerJsonFormat()
    val performer =
      """
        |{
        |  "id":3,
        |  "name":"scarlet",
        |  "skills":["contortion", "burlesque"],
        |  "profile_picture":"http://example.com/4",
        |  "other_pictures":["http://example.com/5"],
        |  "shown":true
        |}
      """.stripMargin.parseJson.convertTo[Performer]
    performer.id should be(3)
    performer.name should be("scarlet")
    performer.skills should be(Set(Skill("contortion"), Skill("burlesque")))
    performer.profilePicture should be(Picture(4))
    performer.otherPictures should be(Set(Picture(5)))
    performer.shown should be(true)
  }
  it should "deserialize a boring performer" in {
    import spray.json._
    implicit val implperf = new PerformerJsonFormat()
    val performer =
      """
        |{
        |  "id":3,
        |  "name":"scarlet",
        |  "skills":[],
        |  "profile_picture":"http://example.com/4",
        |  "other_pictures":[],
        |  "shown":true
        |}
      """.stripMargin.parseJson.convertTo[Performer]
    performer.id should be(3)
    performer.name should be("scarlet")
    performer.skills should be(Set())
    performer.profilePicture should be(Picture(4))
    performer.otherPictures should be(Set())
    performer.shown should be(true)
  }
  it should "Not deserialize bad performers" in {
    import spray.json._
    implicit val implperf = new PerformerJsonFormat()
    intercept[spray.json.DeserializationException] {
      val performer =
        """
        |{
        |  "id":3,
        |  "name":"scarlet",
        |  "profile_picture":"http://example.com/4",
        |  "shown":true
        |}
      """.
          stripMargin.parseJson.convertTo[Performer]
    }
    intercept[spray.json.DeserializationException] {
      val performer =
        "1".parseJson.convertTo[Performer]
    }

  }
  it should "deserialize skills" in {
    import spray.json._
    implicit val implSkill = Skill.SkillJsonFormat
    val skill1 = "\"fire\""
    skill1.parseJson.convertTo[Skill] should be(Skill("fire"))
    val skill2 = "1"
    intercept[spray.json.DeserializationException] {
      skill2.parseJson.convertTo[Skill]
    }
  }
  it should "deserialize pictures" in {
    import spray.json._
    implicit val implSkill = new PictureJsonFormatter()
    val pic1 = "\"http://example.com/4\""
    pic1.parseJson.convertTo[Picture] should be(Picture(4))
    val pic2 = "1"
    intercept[spray.json.DeserializationException] {
      pic2.parseJson.convertTo[Picture]
    }
    val pic3 = "\"http://reddit.com/4\""
    intercept[AssertionError] {
      pic3.parseJson.convertTo[Picture]
    }

    val pic4 = "\"http://example.com/derp\""
    intercept[java.lang.NumberFormatException] {
      pic4.parseJson.convertTo[Picture]
    }
  }
}
