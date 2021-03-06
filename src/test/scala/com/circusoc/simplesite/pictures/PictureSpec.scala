package com.circusoc.simplesite.pictures

import java.net.URL
import java.sql.{Connection, DriverManager}

import com.circusoc.simplesite._
import org.codemonkey.simplejavamail.Email
import org.dbunit.DBTestCase
import org.dbunit.database.DatabaseConnection
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import org.dbunit.operation.DatabaseOperation
import org.scalatest.Matchers._
import org.scalatest.prop.{PropertyChecks, TableDrivenPropertyChecks}
import org.scalatest.{BeforeAndAfter, FlatSpecLike}
import scalikejdbc.ConnectionPool

/**
 *
 */
class PictureSpec extends DBTestCase with FlatSpecLike with BeforeAndAfter with PropertyChecks {
  implicit val config = new WithConfig {
    override val port: Int = 8080
    override val db: DB = new DB {
      override val poolName = 'picturespec
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
    val c = DriverManager.getConnection("jdbc:h2:mem:picturespec;DB_CLOSE_DELAY=-1", "sa", "")
    c.setAutoCommit(true)
    c
  }
  config.db.setup()
  DBSetup.setup()(config)

  val conn = new DatabaseConnection(getJDBC)
  DatabaseOperation.CLEAN_INSERT.execute(conn, getDataSet())

  override def getDataSet: IDataSet = new FlatXmlDataSetBuilder().
    build(classOf[PictureSpec].
    getResourceAsStream("/com/circusoc/simplesite/pictures/PicturesDBSpec.xml"))

  it should "have different CDN and normal URLs" in {
    val picture = PictureReference(1)
    picture.url() should be(new URL("http://localhost:8080/picture/1"))
    picture.cdnUrl() should be(new URL("http://localhost:8000/picture/1"))
  }

  it should "work for large IDs" in {
    forAll { id: Long => whenever(id > 0) {
        val picture = PictureReference(id)
        picture.url() should be(new URL(s"http://localhost:8080/picture/$id"))
        picture.cdnUrl() should be(new URL(s"http://localhost:8000/picture/$id"))
    }}
  }

  it should "find the right picture from a path" in {
    forAll { id: Long => whenever(id > 0) {
      val url = new URL(s"http://localhost:8080/picture/$id")
      val picture = PictureReference.fromURL(url)
      picture.url() should be(new URL(s"http://localhost:8080/picture/$id"))
    }}
  }

  it should "reject derp urls in" in {
    forAll { id: Long => whenever(id > 0) {
      intercept[AssertionError] {
        val url = new URL(s"http://localhost:8080/$id")
        PictureReference.fromURL(url)
      }
      intercept[AssertionError] {
        val url = new URL(s"http://localhost:8080/picture/foo/$id")
        PictureReference.fromURL(url)
      }
      intercept[AssertionError] {
        val url = new URL(s"http://localhost:8080/picture/")
        PictureReference.fromURL(url)
      }
      intercept[NumberFormatException] {
        val url = new URL(s"http://localhost:8080/picture/asdasd123")
        PictureReference.fromURL(url)
      }
    }}
  }

  it should "deserialize pictures" in {
    import spray.json._
    implicit val implSkill = new PictureJsonFormatter()
    val pic1 = "\"http://localhost:8080/picture/4\""
    pic1.parseJson.convertTo[PictureReference] should be(PictureReference(4))
    val pic2 = "1"
    intercept[spray.json.DeserializationException] {
      pic2.parseJson.convertTo[PictureReference]
    }
    val pic3 = "\"http://reddit.com/4\""
    intercept[AssertionError] {
      pic3.parseJson.convertTo[PictureReference]
    }

    val pic4 = "\"http://localhost:8080/picture/derp\""
    intercept[java.lang.NumberFormatException] {
      pic4.parseJson.convertTo[PictureReference]
    }
  }

  it should "upload pictures" in {
    val imageFiles = Table(
      "File",
      "test.png",
      "test.jpg",
      "test.gif"
    )
    TableDrivenPropertyChecks.forAll(imageFiles) {name: String =>
      val picture = pictureFromTestFile(name)
      val user = Misc.superuser
      val resultPic = PictureReference.putPicture(picture, user)
      val retrievedPic = PictureReference.getPicture(PictureReference(resultPic.id))
      retrievedPic should be(Some(pictureFromTestFile(name)))
    }
  }

  it should "delete pictures" in {
    val imageFiles = Table(
      "File",
      "test.png",
      "test.jpg",
      "test.gif"
    )
    TableDrivenPropertyChecks.forAll(imageFiles) {name: String =>
      val picture = pictureFromTestFile(name)
      val user = Misc.superuser
      val resultPic = PictureReference.putPicture(picture, user)
      val retrievedPic = PictureReference.getPicture(PictureReference(resultPic.id))
      retrievedPic should be(Some(pictureFromTestFile(name)))

      val result = PictureReference.deletePicture(PictureReference(resultPic.id), user)
      result should be(true)

      val unretrievedPic = PictureReference.getPicture(PictureReference(resultPic.id))
      unretrievedPic should be(None)
    }
  }

  it should "also retrieve pictures through the picture object" in {
    val imageFiles = Table(
      "File",
      "test.png",
      "test.jpg",
      "test.gif"
    )
    TableDrivenPropertyChecks.forAll(imageFiles) {
      name: String =>
        val picture = pictureFromTestFile(name)
        val user = Misc.superuser
        val resultPic = PictureReference.putPicture(picture, user)
        val retrievedPic = PictureReference(resultPic.id).get()
        retrievedPic should be(Some(pictureFromTestFile(name)))
    }
  }

  it should "not delete non-existent images" in {
    val result = PictureReference.deletePicture(PictureReference(10000), Misc.superuser)
    result should be(false)
  }

  it should "show the default picture" in {
    val defaultpicture = PictureReference.defaultPicture
    PictureReference.getPicture(defaultpicture) should not be None
    PictureReference.getPicture(defaultpicture).get should be(
      pictureFromTestFile("defaultimage.jpg"))
  }

  it should "replace the default picture" in {
    val picture = pictureFromTestFile("test.png")
    val user = Misc.superuser
    val resultPic = PictureReference.putPicture(picture, user)
    PictureReference.setDefaultPicture(resultPic)
    PictureReference.getPicture(PictureReference.defaultPicture).get should be(picture)
  }

  it should "fail to insert non-existent default pictures" in {
    val config_nopic = new WithConfig {
      override val port: Int = 8080
      override val db: DB = new DB {
        override val poolName = 'picturespec_default
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
        override val cdnUrl = new URL("http://localhost:8080")
      }
      override def defaultPictureStream = this.getClass.getResourceAsStream("/com/circusoc/simplesite/pictures/foo.jpg")
    }
    config_nopic.db.setup()
    DBSetup.setup()(config_nopic)
    intercept[AssertionError] {
      PictureReference.defaultPicture(config_nopic)
    }
  }

  def pictureFromTestFile(filename: String): PictureResult = {
    PictureResult(classOf[PictureResultSpec].getResourceAsStream(s"/com/circusoc/simplesite/pictures/$filename")).get
  }
}

