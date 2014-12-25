package com.circusoc.simplesite.pictures

import org.dbunit.DBTestCase
import org.scalatest.{BeforeAndAfter, FlatSpecLike}
import org.scalatest.prop.{TableDrivenPropertyChecks, PropertyChecks}
import com.circusoc.simplesite._
import scalikejdbc.ConnectionPool
import org.codemonkey.simplejavamail.Email
import java.sql.{DriverManager, Connection}
import org.dbunit.database.DatabaseConnection
import org.dbunit.operation.DatabaseOperation
import org.dbunit.dataset.IDataSet
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder
import java.net.URL
import org.scalatest.Matchers._
import spray.http.MediaTypes

/**
 *
 */
class PictureSpec extends DBTestCase with FlatSpecLike with BeforeAndAfter with PropertyChecks {
  implicit val config = new WithConfig {
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
      override val cdnUrl = new URL("https://localhost:5051")
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
    val picture = Picture(1)
    picture.url() should be(new URL("https://localhost:8080/picture/1"))
    picture.cdnUrl() should be(new URL("https://localhost:5051/picture/1"))
  }

  it should "work for large IDs" in {
    forAll { id: Long => whenever(id > 0) {
        val picture = Picture(id)
        picture.url() should be(new URL(s"https://localhost:8080/picture/$id"))
        picture.cdnUrl() should be(new URL(s"https://localhost:5051/picture/$id"))
    }}
  }

  it should "find the right picture from a path" in {
    forAll { id: Long => whenever(id > 0) {
      val url = new URL(s"https://localhost:8080/picture/$id")
      val picture = Picture.fromURL(url)
      picture.url() should be(new URL(s"https://localhost:8080/picture/$id"))
    }}
  }
  it should "reject derp urls in" in {
    forAll { id: Long => whenever(id > 0) {
      intercept[AssertionError] {
        val url = new URL(s"https://localhost:8080/$id")
        Picture.fromURL(url)
      }
      intercept[AssertionError] {
        val url = new URL(s"https://localhost:8080/picture/foo/$id")
        Picture.fromURL(url)
      }
      intercept[AssertionError] {
        val url = new URL(s"https://localhost:8080/picture/")
        Picture.fromURL(url)
      }
      intercept[NumberFormatException] {
        val url = new URL(s"https://localhost:8080/picture/asdasd123")
        Picture.fromURL(url)
      }
    }}
  }

  it should "deserialize pictures" in {
    import spray.json._
    implicit val implSkill = new PictureJsonFormatter()
    val pic1 = "\"https://localhost:8080/picture/4\""
    pic1.parseJson.convertTo[Picture] should be(Picture(4))
    val pic2 = "1"
    intercept[spray.json.DeserializationException] {
      pic2.parseJson.convertTo[Picture]
    }
    val pic3 = "\"http://reddit.com/4\""
    intercept[AssertionError] {
      pic3.parseJson.convertTo[Picture]
    }

    val pic4 = "\"https://localhost:8080/picture/derp\""
    intercept[java.lang.NumberFormatException] {
      pic4.parseJson.convertTo[Picture]
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
      val resultPic = Picture.putPicture(picture, user)
      val retrievedPic = Picture.getPicture(Picture(resultPic.id))
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
      val resultPic = Picture.putPicture(picture, user)
      val retrievedPic = Picture.getPicture(Picture(resultPic.id))
      retrievedPic should be(Some(pictureFromTestFile(name)))

      val result = Picture.deletePicture(Picture(resultPic.id), user)
      result should be(true)

      val unretrievedPic = Picture.getPicture(Picture(resultPic.id))
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
        val resultPic = Picture.putPicture(picture, user)
        val retrievedPic = Picture(resultPic.id).get()
        retrievedPic should be(Some(pictureFromTestFile(name)))
    }
  }

  it should "not delete non-existent images" in {
    val result = Picture.deletePicture(Picture(10000), Misc.superuser)
    result should be(false)
  }

  def pictureFromTestFile(filename: String): PictureResult = {
    PictureResult(classOf[PictureResultSpec].getResourceAsStream(s"/com/circusoc/simplesite/pictures/$filename")).get
  }
}

