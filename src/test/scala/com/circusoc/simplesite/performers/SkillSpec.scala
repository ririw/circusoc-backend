package com.circusoc.simplesite.performers

import java.net.URL
import java.sql.{DriverManager, Connection}

import com.circusoc.simplesite._
import com.circusoc.simplesite.pictures.PictureReference
import org.codemonkey.simplejavamail.Email
import org.dbunit.DBTestCase
import org.dbunit.database.DatabaseConnection
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import org.dbunit.operation.DatabaseOperation
import org.scalatest.FlatSpecLike
import org.scalatest.Matchers._
import org.scalatest.prop.PropertyChecks
import scalikejdbc.ConnectionPool

class SkillSpec extends DBTestCase with FlatSpecLike with PropertyChecks {
  implicit val config = new WithConfig {
    override val port: Int = 8080
    override val db: DB = new DB {
      override val poolName = 'skillspec
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
    val c = DriverManager.getConnection("jdbc:h2:mem:skillspec;DB_CLOSE_DELAY=-1", "sa", "")
    c.setAutoCommit(true)
    c
  }
  config.db.setup()
  DBSetup.setup()(config)

  val conn = new DatabaseConnection(getJDBC)
  DatabaseOperation.CLEAN_INSERT.execute(conn, getDataSet())

  override def getDataSet: IDataSet = new FlatXmlDataSetBuilder().
    build(classOf[PerformerSpec].
    getResourceAsStream("/com/circusoc/simplesite/performers/SkillDBSpec.xml"))

  it should "deserialize skills" in {
    import spray.json._
    implicit val skillformatter = new Skill.SkillJsonFormat()
    val skill = "\"fire\""
    skill.parseJson.convertTo[Skill] should be(Skill(1, "fire", PictureReference(1)))
  }

  it should "not deserialize unknown skills" in {
    import spray.json._
    implicit val skillformatter = new Skill.SkillJsonFormat()
    val skill = "\"1\""
    val t = intercept[spray.json.DeserializationException] {
      val s = skill.parseJson.convertTo[Skill]
    }
    t.getMessage should be("Unknown skill")

    val skill2 = "1"
    val t2 = intercept[spray.json.DeserializationException] {
      val s2 = skill2.parseJson.convertTo[Skill]
    }
    t2.getMessage should be("Skill expected")

  }

  it should "get a list of all the skills" in {
    Skill.allPerformerSkills() should be(
      List(
        Skill(1,"fire",PictureReference(1)),
        Skill(2,"acro",PictureReference(2)),
        Skill(3,"badminton",PictureReference(3)),
        Skill(1,"fire",PictureReference(1)))
    )
  }
}
