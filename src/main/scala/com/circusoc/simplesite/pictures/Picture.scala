package com.circusoc.simplesite.pictures

import com.circusoc.simplesite.WithConfig
import java.net.{URLConnection, URL}
import spray.json._
import scalikejdbc._

import scalikejdbc.NamedDB
import scala.io.Source
import com.circusoc.simplesite.users.AuthenticatedUser
import com.circusoc.simplesite.users.permissions.ModifyImagesPermission
import spray.http.{MediaType, MediaTypes}
import java.io.{ByteArrayInputStream, InputStream}
import scala.util.{Failure, Success, Try}

case class Picture(id: Long) {
  def url()(implicit config: WithConfig): URL = new URL(config.paths.baseUrl.toExternalForm + s"/picture/$id")
  def cdnUrl()(implicit config: WithConfig): URL = new URL(config.paths.cdnUrl.toExternalForm + s"/picture/$id")
  def get()(implicit config: WithConfig): Option[PictureResult] = Picture.getPicture(this)
}

object Picture extends  {
  def fromURL(url: URL)(implicit config: WithConfig): Picture = {
    assert(url.getHost == config.paths.baseUrl.getHost)
    assert(url.getPort == config.paths.baseUrl.getPort)
    assert(url.getProtocol == config.paths.baseUrl.getProtocol)
    assert(url.getPath.startsWith("/picture/"))
    val idStartsAt = url.getPath.lastIndexOf('/')
    assert(idStartsAt + 1 == "/picture/".length)
    assert(idStartsAt+1 < url.getPath.length, "ID not found")
    Picture(url.getPath.substring(idStartsAt+1).toLong)
  }

  def getPicture(picture: Picture)(implicit config: WithConfig): Option[PictureResult] = {
    NamedDB(config.db.poolName).readOnly { implicit session =>
      sql"""SELECT picture, mediatype FROM picture WHERE id=${picture.id}""".
        map{r => 
          val data = Source.fromInputStream(r.blob(1).getBinaryStream).toArray.map(_.toByte)
          val mediaType = PictureResult.getMediaType(r.string(2)).get
        PictureResult(data, mediaType)
        }.headOption().apply()
    }
  }

  def putPicture(picture: PictureResult, insertingUser: AuthenticatedUser)
                (implicit config: WithConfig): Picture = {
    assert(insertingUser.hasPermission(ModifyImagesPermission()))
    NamedDB(config.db.poolName).localTx {implicit session =>
      val id = sql"""INSERT INTO picture (picture, mediatype) 
                     VALUES (?, ?)""".bind(picture.data, picture.mediaType.value).
        updateAndReturnGeneratedKey()()
      Picture(id)
    }
  }

  def deletePicture(picture: Picture, insertingUser: AuthenticatedUser)
                   (implicit config: WithConfig): Boolean = {
    assert(insertingUser.hasPermission(ModifyImagesPermission()))
    NamedDB(config.db.poolName).localTx {implicit session =>
      val existsResult = sql"""SELECT COUNT(*) FROM picture WHERE id=${picture.id}""".map(_.int(1)).first()()
      val exists = existsResult match {
        case Some(a) =>
          assert(a < 2)
          a > 1
        case _ => false
      }
      sql"""DELETE FROM picture id=${picture.id}""".execute()()
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
class PictureJsonFormatter()(implicit config: WithConfig) extends RootJsonFormat[Picture] with DefaultJsonProtocol {
  def write(link: Picture) = JsString(link.url().toExternalForm)
  def read(value: JsValue) = value match {
    case JsString(v) => Picture.fromURL(new URL(v))
    case _ => deserializationError("URL expected")
  }
}

case class PictureResult(data: Array[Byte], mediaType: MediaType) {
  assert(PictureResult.isValidMediaType(mediaType))
}

object PictureResult {
  def apply(data: InputStream): Try[PictureResult] = {
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
  def getMediaType(name: String): Try[MediaType] = {
    name match {
      case "image/gif"  => Success(MediaTypes.`image/gif`)
      case "image/jpeg" => Success(MediaTypes.`image/jpeg`)
      case "image/png"  => Success(MediaTypes.`image/png`)
      case _ =>
        Failure(new MediaTypeException("Invalid media type: " + name))
    }
  }
}

case class MediaTypeException(msg: String) extends Exception