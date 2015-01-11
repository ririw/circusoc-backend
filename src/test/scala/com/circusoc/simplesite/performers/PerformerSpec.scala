package com.circusoc.simplesite.performers

import java.net.URL
import java.sql.{Connection, DriverManager}

import com.circusoc.simplesite._
import com.circusoc.simplesite.pictures.PictureReference
import com.circusoc.simplesite.users.AuthenticatedUser
import com.circusoc.simplesite.users.User.UserBuilder
import com.circusoc.simplesite.users.permissions.CanAdministerUsersPermission
import org.codemonkey.simplejavamail.Email
import org.dbunit.DBTestCase
import org.dbunit.database.DatabaseConnection
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import org.dbunit.operation.DatabaseOperation
import org.scalatest.Matchers._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{BeforeAndAfter, FlatSpecLike}
import scalikejdbc.ConnectionPool
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

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
    override val paths: PathConfig = new PathConfig {
      override def baseUrl: URL = new URL("http://localhost:8080")
      override def cookieUrl: String = "localhost"
      override def cdnUrl: URL = new URL("http://localhost:8000")
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

  val debugProof: DebugMayAlterPerformerProof = new DebugMayAlterPerformerProof()


  it should "get a performer by their ID" in {
    val _steve = Performer.getPerformerByID(1)
    assert(_steve.isDefined)
    val steve = _steve.get
    steve.id should be(1)
    steve.name should be("steve")
    steve.skills should be(Set(Skill(1, "fire", PictureReference(1)), Skill(2, "acro", PictureReference(2))))
    steve.profilePicture should be(PictureReference(1))
    steve.otherPictures should be(Set(PictureReference(2)))
    steve.shown should be(false)
  }

  it should "get another performer by their id" in {
    val _dale = Performer.getPerformerByID(2)
    assert(_dale.isDefined)
    val dale = _dale.get
    dale.id should be(2)
    dale.name should be("dale")
    dale.profilePicture should be(PictureReference(2))
    dale.shown should be(true)
    dale.skills should be(Set(Skill(3, "badminton", PictureReference(3))))
    dale.otherPictures should be(Set(PictureReference(3), PictureReference(4)))
  }

  it should "get a performer that has no skills" in {
    val _leela = Performer.getPerformerByID(3)
    assert(_leela.isDefined)
    val leela = _leela.get
    leela.id should be(3)
    leela.name should be("leela")
    leela.profilePicture should be(PictureReference(4))
    leela.shown should be(true)
    leela.skills should be(Set())
    leela.otherPictures should be(Set(PictureReference(5)))
  }

  it should "get a performer that has no pictures" in {
    val _carla = Performer.getPerformerByID(4)
    assert(_carla.isDefined)
    val carla = _carla.get
    carla.id should be(4)
    carla.name should be("Carla")
    carla.profilePicture should be(PictureReference(6))
    carla.skills should be(Set(Skill(1, "fire", PictureReference(1))))
    carla.otherPictures should be(Set())
  }

  it should "get a performer with no skills and no pictures" in {
    val _alexa = Performer.getPerformerByID(5)
    assert(_alexa.isDefined)
    val alexa = _alexa.get
    alexa.id should be(5)
    alexa.name should be("Alexa")
    alexa.profilePicture should be(PictureReference(7))
    alexa.otherPictures should be(Set())
    alexa.skills should be(Set())
  }

  it should "not get other random performers" in {
    forAll {id: Int =>
      whenever(id > 5) {
        Performer.getPerformerByID(id) should be(None)
      }
    }
  }

  it should "add skills & not re-add skills" in {
    val drudgeon = Skill.createOrGetSkill("Being a drudgeon", PictureReference(1))
    val fargsnooth = Performer.getPerformerByID(6).get
    fargsnooth.skills should be(Set())
    val fargsnoothen = fargsnooth.addSkill(drudgeon, debugProof)
    fargsnoothen.skills should be(Set(drudgeon))
    val fargsnooth_verify = Performer.getPerformerByID(6).get
    fargsnooth_verify.skills should be(Set(drudgeon))
    val fargsnoothen_2 = fargsnooth_verify.addSkill(drudgeon, debugProof)
    val fargsnooth_verify_2 = Performer.getPerformerByID(6).get
    fargsnooth_verify_2 .skills should be(Set(drudgeon))
  }

  it should "ignore duplicate skills" in {
    val skill: Skill = Skill(2, "acro", PictureReference(1))
    List.fill(50)(Future{
      val fargsnooth = Performer.getPerformerByID(6).get
      fargsnooth.addSkill(skill, debugProof)
    })
  }

  it should "remove skills and not remove made-up skills" in {
    val biergliden = Performer.getPerformerByID(7).get
    biergliden.skills should be(Set(Skill(4, "fadminton", PictureReference(4))))
    val biergliden_2 = biergliden.
      removeSkill(4, debugProof).
      removeSkill(5, debugProof)
    biergliden_2.skills should be(Set())
    val biergliden_3 = Performer.getPerformerByID(7).get
    biergliden_3.skills should be(Set())
  }

  it should "change the profile pic" in {
    val kiruna = Performer.getPerformerByID(8).get
    kiruna.profilePicture.id should be(1l)
    val somePic = PictureReference(100l)
    val kiruna2 = kiruna.setProfilePic(somePic, debugProof)
    kiruna2.profilePicture.id should be(100l)
    val kiruna_get = Performer.getPerformerByID(8).get
    kiruna_get.profilePicture.id should be(100l)
  }
  
  it should "add performer pictures" in {
    val kiruna = Performer.getPerformerByID(8).get
    kiruna.otherPictures should be(Set())
    val kiruna2 = kiruna.addPicture(PictureReference(1l), debugProof)
    kiruna2.otherPictures should be(Set(PictureReference(1l)))
    kiruna.addPicture(PictureReference(2l), debugProof)
    kiruna.addPicture(PictureReference(3l), debugProof)
    val kiruna3 = Performer.getPerformerByID(8).get
    kiruna3.otherPictures should be(Set(PictureReference(1l), PictureReference(2l), PictureReference(3l)))
    kiruna3.addPicture(PictureReference(3l), debugProof)
    val kiruna4 = Performer.getPerformerByID(8).get
    kiruna4.otherPictures should be(Set(PictureReference(1l), PictureReference(2l), PictureReference(3l)))
  }

  it should "delete performer pictures" in {
    val kex = Performer.getPerformerByID(9).get
    kex.otherPictures should be(Set(PictureReference(2l), PictureReference(3l)))
    val kex2 = kex.deletePicture(PictureReference(2l), debugProof)
    kex2.otherPictures should be(Set(PictureReference(3l)))
    val kex3 = Performer.getPerformerByID(9).get
    kex3.otherPictures should be(Set(PictureReference(3l)))
    val kex4 = kex3.deletePicture(PictureReference(4l), debugProof)
    kex4.otherPictures should be(Set(PictureReference(3l)))
    val kex5 = Performer.getPerformerByID(9).get
    kex5.otherPictures should be(Set(PictureReference(3l)))
  }

  it should "create new performers" in {
    val gimli = Performer.newPerformer("Gimli", true, debugProof)
    val gimli2 = Performer.getPerformerByID(gimli.id).get
    gimli should be(gimli2)
    gimli.profilePicture should be(PictureReference.defaultPicture)
    gimli.otherPictures should be(Set())
  }


  it should "at the very least, get those performers in the test set" in {
    val allIDs = Performer.getPerformerIds
    // From when I was verifying the test itself...
    // val expected = Set(1,2,3,4,5,6,7,8,9,100)
    val expected = Set(1,2,3,4,5,6,7,8,9)
    // {1, 2, 3, 4} ∩ {1, 2, 3, 4} == {1, 2, 3, 4}
    // {1, 2, 3} ∩ {1, 2, 3, 4} != {1, 2, 3, 4}
    // {1, 2, 3, 4} ∩ {1, 2, 3} != {1, 2, 3, 4}
    allIDs.intersect(expected) should be(expected)
    expected.intersect(allIDs) should be(expected)
  }

  /************************ SERIALIZATION TESTS ************************/
  /************************ SERIALIZATION TESTS ************************/
  /************************ SERIALIZATION TESTS ************************/
  /************************ SERIALIZATION TESTS ************************/
  /************************ SERIALIZATION TESTS ************************/

  "the performer serialization code" should "serialize a full performer" in {
    import spray.json._
    implicit val implperf = new PerformerJsonFormat()
    val steve = Performer.getPerformerByID(1).get.toJson
    val expected = JsObject(
      "id" -> JsNumber(1),
      "name" -> JsString("steve"),
      "skills" -> JsArray(
        JsObject("name" -> JsString("fire"), "picture" -> JsString("http://localhost:8080/picture/1")),
        JsObject("name" -> JsString("acro"), "picture" -> JsString("http://localhost:8080/picture/2"))),
      "profile_picture" -> JsString("http://localhost:8080/picture/1"),
      "other_pictures" -> JsArray(JsString("http://localhost:8080/picture/2")),
      "shown" -> JsBoolean(false)
    )
    steve.asJsObject.getFields("id")    should be(expected.getFields("id"))
    steve.asJsObject.getFields("name")  should be(expected.getFields("name"))
    steve.asJsObject.getFields("shown") should be(expected.getFields("shown"))
    steve.asJsObject.getFields("skills").head.asInstanceOf[JsArray].elements.toSet should be(
            expected.getFields("skills").head.asInstanceOf[JsArray].elements.toSet)
    steve.asJsObject.getFields("other_pictures") should be(
            expected.getFields("other_pictures"))
    steve.asJsObject.getFields("profile_picture") should be(
            expected.getFields("profile_picture"))
  }
  it should "serialize another performer" in {
    import spray.json._
    implicit val implperf = new PerformerJsonFormat()
    val dale = Performer.getPerformerByID(2).get.toJson

    val expected = JsObject(
      "id" -> JsNumber(2),
      "name" -> JsString("dale"),
      "skills" -> JsArray(
        JsObject("name" -> JsString("badminton"), "picture" -> JsString("http://localhost:8080/picture/3"))
      ),
      "profile_picture" -> JsString("http://localhost:8080/picture/2"),
      "other_pictures" -> JsArray(
        JsString("http://localhost:8080/picture/3"),
        JsString("http://localhost:8080/picture/4")),
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
        |  "profile_picture":"http://localhost:8080/picture/4",
        |  "other_pictures":["http://localhost:8080/picture/5"],
        |  "shown":true
        |}
      """.stripMargin.parseJson.convertTo[Performer]
    performer.id should be(3)
    performer.name should be("scarlet")
    performer.skills should be(Set(Skill(5, "contortion", PictureReference(5)), Skill(6, "burlesque", PictureReference(1))))
    performer.profilePicture should be(PictureReference(4))
    performer.otherPictures should be(Set(PictureReference(5)))
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
        |  "profile_picture":"http://localhost:8080/picture/4",
        |  "other_pictures":[],
        |  "shown":true
        |}
      """.stripMargin.parseJson.convertTo[Performer]
    performer.id should be(3)
    performer.name should be("scarlet")
    performer.skills should be(Set())
    performer.profilePicture should be(PictureReference(4))
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
        |  "profile_picture":"http://localhost:8080/picture/4",
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

}


class MayAlterPerformersProofSpec extends FlatSpecLike {
  implicit val config = new WithConfig {
    override val isProduction = true
    override val db: DB = new DB {}
    override val hire: Hire = new Hire {}
    override val paths: PathConfig = new PathConfig {}
    override val mailer: MailerLike = new MailerLike {
      override def sendMail(email: Email): Unit = ???
    }
  }
  it should "not initialize when we are in production" in {
    intercept[AssertionError] {
      new DebugMayAlterPerformerProof()
    }
  }
}
