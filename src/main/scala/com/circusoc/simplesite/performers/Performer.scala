package com.circusoc.simplesite.performers

import spray.json._
import java.net.URL


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


object PerformerJSONProtocol extends DefaultJsonProtocol {
  implicit object PerformerJsonFormat extends RootJsonFormat[Performer] {
    import Skill.SkillJsonFormat._
    import PictureURL.PictureURLJsonFormat._

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
case class PictureURL(url: URL) extends AnyVal {}

object PictureURL extends DefaultJsonProtocol {
  implicit object PictureURLJsonFormat extends RootJsonFormat[PictureURL] {
    def write(link: PictureURL) = JsString(link.url.toExternalForm)
    def read(value: JsValue) = value match {
      case JsString(v) => PictureURL(new URL(v))
      case _ => deserializationError("URL expected")
    }
  }
}