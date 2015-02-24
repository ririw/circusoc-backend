package com.circusoc.simplesite.pictures

import java.io.{ByteArrayInputStream, InputStream}
import java.net.{URL, URLConnection}

import com.circusoc.simplesite.WithConfig
import com.circusoc.simplesite.users.AuthenticatedUser
import com.circusoc.simplesite.users.permissions.ModifyImagesPermission
import scalikejdbc._
import spray.http.{MediaType, MediaTypes}
import spray.json._

case class PictureReference(id: Long) {
  def url()(implicit config: WithConfig): URL = new URL(config.paths.baseUrl.toExternalForm + s"/picture/$id")
  def cdnUrl()(implicit config: WithConfig): URL = new URL(config.paths.cdnUrl.toExternalForm + s"/picture/$id")
  def get()(implicit config: WithConfig): Option[PictureResult] = PictureReference.getPicture(this)
}

object PictureReference {
  val defaultImage = PictureResult(PictureReference.getClass.getResourceAsStream("/com/circusoc/simplesite/pictures/defaultimage.jpg"))

  def fromURL(url: URL)(implicit config: WithConfig): PictureReference = {
    assert(url.getHost == config.paths.baseUrl.getHost)
    assert(url.getPort == config.paths.baseUrl.getPort)
    assert(url.getProtocol == config.paths.baseUrl.getProtocol)
    assert(url.getPath.startsWith("/picture/"))
    val idStartsAt = url.getPath.lastIndexOf('/')
    assert(idStartsAt + 1 == "/picture/".length)
    assert(idStartsAt+1 < url.getPath.length, "ID not found")
    PictureReference(url.getPath.substring(idStartsAt+1).toLong)
  }

  def defaultPicture(implicit config: WithConfig): PictureReference = {
    val imgopt = config.db.getDB.readOnly {implicit session =>
      sql"""SELECT id FROM picture p join default_performer_picture dp on p.id=dp.picture_id""".map{r =>
        PictureReference(r.int(1))
      }.headOption().apply()
    }
    imgopt match {
      case Some(img) => img
      case None =>
        val defaultStream = config.defaultPictureStream
        assert(defaultStream != null, "Default picture not found.")
        val defaultPicResult = PictureResult(defaultStream).get
        val picture = putPicture(defaultPicResult)
        config.db.getDB.autoCommit{implicit s =>
          sql"""INSERT INTO default_performer_picture VALUES (${picture.id}, true)""".execute()()
        }
        picture
    }
  }

  def setDefaultPicture(picture: PictureReference)(implicit config: WithConfig): PictureReference = {
    config.db.getDB.localTx { implicit session =>
      sql"""DELETE FROM default_performer_picture""".execute()()
      sql"""INSERT INTO default_performer_picture VALUES (${picture.id}, true)""".execute()()
      picture
    }
  }

  def getPicture(picture: PictureReference)(implicit config: WithConfig): Option[PictureResult] = {
    config.db.getDB.readOnly { implicit session =>
      sql"""SELECT picture, mediatype FROM picture WHERE id=${picture.id}""".
        map{r => 
          val stream = r.blob(1).getBinaryStream
          val data = Stream.continually(stream.read).takeWhile(_ != -1).toArray.map(_.toByte)
          val mediaType = PictureResult.getMediaType(r.string(2)).get
          PictureResult(data, mediaType)
        }.headOption().apply()
    }
  }

  def putPicture(picture: PictureResult, insertingUser: AuthenticatedUser)
                (implicit config: WithConfig): PictureReference = {
    assert(insertingUser.hasPermission(ModifyImagesPermission))
    putPicture(picture)
  }

  private def putPicture(picture: PictureResult)(implicit config: WithConfig): PictureReference = {
    config.db.getDB.localTx { implicit session =>
      val id = sql"""INSERT INTO picture (picture, mediatype)
                     VALUES (?, ?)""".bind(picture.data, picture.mediaType.value).
        updateAndReturnGeneratedKey()()
      PictureReference(id)
    }
  }

  def deletePicture(picture: PictureReference, insertingUser: AuthenticatedUser)
                   (implicit config: WithConfig): Boolean = {
    assert(insertingUser.hasPermission(ModifyImagesPermission))
    config.db.getDB.localTx {implicit session =>
      val existsResult = sql"""SELECT COUNT(*) FROM picture WHERE id=${picture.id}""".map(_.int(1)).first()()
      val exists = existsResult match {
        case Some(a) =>
          assert(a < 2)
          a >= 1
        // $COVERAGE-OFF$
        case _ => false
        // $COVERAGE-ON$
      }
      if (exists) sql"""DELETE FROM picture WHERE id=${picture.id}""".execute()()
      exists
    }
  }
}


/**
 * The picture formatter. This is instantiated as a class so you can
 * get the config, which happens in the usual implicit config way.
 * Just do:
 *
 *  implicit val config = something
 *  ...
 *  implicit val PictureFromIDJsonFormatter = new PictureFromIDJsonFormatter()
 *
 * @param config the config we're after.
 */
class PictureJsonFormatter()(implicit config: WithConfig)
  extends RootJsonFormat[PictureReference] with DefaultJsonProtocol {
  def write(link: PictureReference) = JsString(link.url().toExternalForm)
  def read(value: JsValue) = value match {
    case JsString(v) => PictureReference.fromURL(new URL(v))
    case _ => deserializationError("URL expected")
  }
}

case class PictureResult(data: Array[Byte], mediaType: MediaType) extends Equals {
  assert(PictureResult.isValidMediaType(mediaType))
  override def canEqual(that: Any): Boolean =
    that.isInstanceOf[PictureResult]
  override def equals(_that: Any): Boolean = _that match {
    case that: PictureResult => that.canEqual(this) &&
      that.mediaType == this.mediaType &&
      this.data.deep == that.data.deep
    case _ => false
  }
}

object PictureResult {
  def apply(data: InputStream): Option[PictureResult] = {
    val dat = Stream.continually(data.read).takeWhile(_ != -1).map(_.toByte).toArray
    val is = new ByteArrayInputStream(dat)
    val mimeType = URLConnection.guessContentTypeFromStream(is)
    val foundMimetype = getMediaType(mimeType)
    foundMimetype.map { mediaType =>
        PictureResult(dat, mediaType)
    }
  }
  private def validMediaTypes = Set(
    MediaTypes.`image/gif`,
    MediaTypes.`image/jpeg`,
    MediaTypes.`image/png`
  )

  def isValidMediaType(mediaType: MediaType): Boolean = validMediaTypes.contains(mediaType)
  def getMediaType(name: String): Option[MediaType] = {
    name match {
      case "image/gif"  => Some(MediaTypes.`image/gif`)
      case "image/jpeg" => Some(MediaTypes.`image/jpeg`)
      case "image/png"  => Some(MediaTypes.`image/png`)
      case _ => None
    }
  }
}

case class MediaTypeException(msg: String) extends Exception