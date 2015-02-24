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

/**
 *
 */
class PictureResultSpec extends DBTestCase with FlatSpecLike with BeforeAndAfter with PropertyChecks {
  implicit val config = new WithConfig {
    override val port: Int = 8080
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
      val picture_o = pictureFromTestFile(filename)
      val is = classOf[PictureResultSpec].getResourceAsStream(s"/com/circusoc/simplesite/pictures/$filename")
      val data = Stream.continually(is.read()).takeWhile(-1 !=).map(_.toByte).toArray
      assert(picture_o.isDefined)
      val picture = picture_o.get
      picture.mediaType should be(mediaType)
      picture.data.take(20)      should be(data.take(20))
      picture.data.takeRight(20) should be(data.takeRight(20))
      // use predef to specifically avoid using scalatest's assert, which spams the console
      scala.Predef.assert(picture.data.deep == data.deep)
    }
  }
  it should "reject a non-picture" in {
    pictureFromTestFile("mediaFailureTest.xml") should be(None)
  }

  def pictureFromTestFile(filename: String): Option[PictureResult] = {
    PictureResult(classOf[PictureResultSpec].getResourceAsStream(s"/com/circusoc/simplesite/pictures/$filename"))
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
      PictureResult.getMediaType(s) should be(Some(t))
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
        case Some(_) => assert(false, "should have failed, this was an invalid mediatype")
        case _ => Unit
      }
    }
    PictureResult.getMediaType("blerg") match {
      case Some(_) => assert(false, "should have failed, this was an invalid mediatype")
      case _ => Unit
    }
  }
  it should "reject improper media types" in {
    intercept[AssertionError] {
      PictureResult(Array(1, 2, 3, 4).map(_.toByte), MediaTypes.`application/atom+xml`)
    }
  }

  it should "compare correctly" in {
    val a = PictureResult(Array(1, 2, 3, 4).map(_.toByte), MediaTypes.`image/png`)
    val b = PictureResult(Array(1, 2, 3, 4).map(_.toByte), MediaTypes.`image/png`)
    a should be(b); b should be(a)
    val c = PictureResult(Array(1, 2, 3, 4).map(_.toByte), MediaTypes.`image/jpeg`)
    a should not be c; c should not be a
    val d = PictureResult(Array(1, 2, 3, 4, 5).map(_.toByte), MediaTypes.`image/jpeg`)
    a should not be d; d should not be c
    val derp = new Integer(1)
    a should not be derp; derp should not be a
    class SillyExtender(_data: Array[Byte], _media: MediaType) extends PictureResult(_data, _media)
    val e = new SillyExtender(Array(1, 2, 3, 4).map(_.toByte), MediaTypes.`image/png`) {
      override def canEqual(that: Any): Boolean = that.isInstanceOf[SillyExtender]
      override def equals(_that: Any): Boolean = _that match {
        case that: SillyExtender => that.canEqual(this) &&
          that.mediaType == this.mediaType &&
          this.data.deep == that.data.deep
        case _ => false
      }
    }
    e.asInstanceOf[PictureResult] should not be a
    a should not be e.asInstanceOf[PictureResult]
    a should not be e
    e should not be a
  }
}


