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
import spray.http.{MediaTypes, MediaType}
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream
import scala.util.{Failure, Success}

/**
 *
 */
class PictureResultSpec extends DBTestCase with FlatSpecLike with BeforeAndAfter with PropertyChecks {
  implicit val config = new WithConfig {
    override val db: DB = new DB {
      override val poolName = 'pictureresultspec
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
    val c = DriverManager.getConnection("jdbc:h2:mem:pictureresultspec;DB_CLOSE_DELAY=-1", "sa", "")
    c.setAutoCommit(true)
    c
  }
  config.db.setup()
  DBSetup.setup()(config)

  val conn = new DatabaseConnection(getJDBC)
  DatabaseOperation.CLEAN_INSERT.execute(conn, getDataSet())

  override def getDataSet: IDataSet = new FlatXmlDataSetBuilder().
    build(classOf[PictureResultSpec].
    getResourceAsStream("/com/circusoc/simplesite/pictures/PicturesDBSpec.xml"))

  it should "Correctly create pictures from files" in {
    val imageFiles = Table(
      ("File", "File type"),
      ("test.png", MediaTypes.`image/png`),
      ("test.jpg", MediaTypes.`image/jpeg`),
      ("test.gif", MediaTypes.`image/gif`)
    )
    TableDrivenPropertyChecks.forAll(imageFiles) {(filename: String, mediaType: MediaType) =>
      val picture = pictureFromTestFile(filename)
      val is = classOf[PictureResultSpec].getResourceAsStream(s"/com/circusoc/simplesite/pictures/$filename")
      val data = Stream.continually(is.read()).takeWhile(-1 !=).map(_.toByte).toArray
      picture.mediaType should be(mediaType)
      picture.data.take(20)      should be(data.take(20))
      picture.data.takeRight(20) should be(data.takeRight(20))
      // use predef to specifically avoid using scalatest's assert, which spams the console
      scala.Predef.assert(picture.data.deep == data.deep)
    }
  }
  it should "reject a non-picture" in {
    intercept[MediaTypeException] {
      pictureFromTestFile("mediaFailureTest.xml")
    }
  }

  def pictureFromTestFile(filename: String): PictureResult = {
    PictureResult(classOf[PictureResultSpec].getResourceAsStream(s"/com/circusoc/simplesite/pictures/$filename")).get
  }

  it should "only allow a subset of media types" in {
    val allowedTypes = Table(
      ("type", "string"),
      (MediaTypes.`image/png`, MediaTypes.`image/png`.value),
      (MediaTypes.`image/jpeg`, MediaTypes.`image/jpeg`.value),
      (MediaTypes.`image/gif`, MediaTypes.`image/gif`.value)
    )
    TableDrivenPropertyChecks.forAll(allowedTypes) { (t: MediaType, s: String) =>
      PictureResult.isValidMediaType(t) should be(true)
      PictureResult.getMediaType(s) should be(Success(t))
    }

    PictureResult.isValidMediaType(MediaTypes.`image/pict`) should be(false)

    val disallowdTypes = Table(
      ("type", "string"),
      (MediaTypes.`application/atom+xml`, MediaTypes.`application/atom+xml`.value),
      (MediaTypes.`image/pict`, MediaTypes.`image/pict`.value)
    )
    TableDrivenPropertyChecks.forAll(disallowdTypes) { (t: MediaType, s: String) =>
      PictureResult.isValidMediaType(t) should be(false)
      PictureResult.getMediaType(s) match {
        case Success(_) => assert(false, "should have failed, this was an invalid mediatype")
        case _ => Unit
      }
    }
    PictureResult.getMediaType("blerg") match {
      case Success(_) => assert(false, "should have failed, this was an invalid mediatype")
      case _ => Unit
    }
  }
}

