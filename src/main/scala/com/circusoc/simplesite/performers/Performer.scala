package com.circusoc.simplesite.performers

import spray.json._
import java.net.URL
import com.circusoc.simplesite.WithConfig


/**
 * Performers have names, skills, profile pictures,
 * profile thumbnails and secondary pictures.
 *
 * Performers can be hidden or shown.
 */
case class Performer(id: Long,
                name: String,
                skills: Set[Skill],
                profilePicture: PictureURL,
                otherPictures: Set[PictureURL]) {}


/**
 * The JSON formatter for a performer.
 * This needs to be instantiated as a class because it depends on the picture formatter.
 * @param config
 */
class PerformerJsonFormat(implicit config: WithConfig) extends RootJsonFormat[Performer] with DefaultJsonProtocol {
  import Skill.SkillJsonFormat._

  implicit val pictureJsonFormatter = new PictureURLJsonFormatter()
  def write(performer: Performer) =
    JsObject(
      "id" -> JsNumber(performer.id),
      "username" -> JsString(performer.name),
      "skills" -> performer.skills.toJson,
      "profile_picture" -> performer.profilePicture.toJson,
      "other_pictures" -> performer.otherPictures.toJson
    )

  def read(value: JsValue): Performer = value match {
    case o: JsObject =>
      val fields = o.fields
      val performerFields = for {
        id <- fields.get("id")
        username <- fields.get("name")
        skills <- fields.get("skills")
        profilePicture <- fields.get("profile_picture")
        otherPictures <- fields.get("other_pictures")
      } yield (id, username, skills, profilePicture, otherPictures)
      performerFields match {
        case Some((JsNumber(id), JsString(name), JsArray(_skills), _profilePic, JsArray(_otherPics))) =>
          val skills = _skills.map(_.convertTo[Skill]).toSet
          val performerPic = _profilePic.convertTo[PictureURL]
          val otherPics = _otherPics.map(_.convertTo[PictureURL]).toSet
          Performer(id.toLong, name, skills, performerPic, otherPics)
        case _ => deserializationError("Perfomer expected")
      }
    case _ => deserializationError("Performer expected")
  }
}

case class Skill(skill: String) extends AnyVal
object Skill extends DefaultJsonProtocol {
  implicit object SkillJsonFormat extends RootJsonFormat[Skill] {
    def write(skill: Skill) = JsString(skill.skill)
    def read(value: JsValue) = value match {
      case JsString(v) => Skill(v)
      case _ => deserializationError("Skill expected")
    }
  }
}

case class PictureURL(id: Long, url: URL)

object PictureURL extends  {
  def fromURL(url: URL)(implicit config: WithConfig) = ???
}

/**
 * The picture formatter. This is instantiated as a class so you can
 * get the config, which happens in the usual implicit config way.
 * Just do:
 *
 *  implicit val config = something
 *  ...
 *  implicit val pictureURLJsonFormatter = new PictureURLJsonFormatter()
 *
 * @param config the config we're after.
 */
class PictureURLJsonFormatter(implicit config: WithConfig) extends RootJsonFormat[PictureURL] with DefaultJsonProtocol {
  def write(link: PictureURL) = JsString(link.url.toExternalForm)
  def read(value: JsValue) = value match {
    case JsString(v) => PictureURL.fromURL(new URL(v))
    case _ => deserializationError("URL expected")
  }
}