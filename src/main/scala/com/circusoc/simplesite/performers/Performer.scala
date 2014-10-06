package com.circusoc.simplesite.performers

import spray.json._
import java.net.URL
import scalikejdbc.NamedDB
import com.circusoc.simplesite.WithConfig
import scalikejdbc._
import org.slf4j.LoggerFactory
import com.circusoc.simplesite.pictures.{PictureJsonFormatter, Picture}


/**
 * Performers have names, skills, profile pictures,
 * profile thumbnails and secondary pictures.
 *
 * Performers can be hidden or shown.
 */
case class Performer(id: Long,
                     name: String,
                     skills: Set[Skill],
                     profilePicture: Picture,
                     otherPictures: Set[Picture],
                     shown: Boolean) {}

object Performer {
  case class PerformerBuilder(
    id: Option[Long] = None,
    name: Option[String] = None,
    skills: Set[Skill] = Set(),
    profilePicture: Option[Picture] = None,
    otherPictures: Set[Picture] = Set(),
    shown: Option[Boolean] = None
  ) {
    def addId(_id: Long) = {
      assert(id.isEmpty || id.get == _id)
      this.copy(id=Some(_id))
    }
    def addName(_name: String) = {
      assert(name.isEmpty || name.get == _name)
      this.copy(name=Some(_name))
    }
    def addSkill(skill: Skill) = this.copy(skills=skills + skill)
    def addPicture(picture: Picture) = this.copy(otherPictures=otherPictures + picture)
    def addProfilePicture(_profilePicture: Picture) = {
      assert(profilePicture.isEmpty || profilePicture.get == _profilePicture)
      this.copy(profilePicture=Some(_profilePicture))
    }
    def addShown(_shown: Boolean) = {
      assert(shown.isEmpty || shown.get == _shown)
      this.copy(shown=Some(_shown))
    }
    def build(): Option[Performer] = {
      id match {
        case None => None
        case Some(_) =>
          assert(name.isDefined)
          assert(shown.isDefined)
          assert(profilePicture.isDefined)
          Some(new Performer(id.get, name.get, skills, profilePicture.get, otherPictures, shown.get))
      }
    }
  }
  
  def buildFromPerformerTable(pb: PerformerBuilder, rs: WrappedResultSet): PerformerBuilder = {
    val id = rs.long("id")
    val name = rs.string("name")
    val profile_picture = rs.long("profile_picture_id")
    val shown = rs.boolean("shown")
    pb.addId(id).addName(name).addProfilePicture(Picture(profile_picture)).addShown(shown)
  }
  
  def buildFromSkillTable(pb: PerformerBuilder, rs: WrappedResultSet): PerformerBuilder = {
    val skill = rs.string("skill")
    pb.addSkill(Skill(skill))
  }

  def buildFromPictureTable(pb: PerformerBuilder, rs: WrappedResultSet): PerformerBuilder = {
    val picture = rs.long("picture_id")
    pb.addPicture(Picture(picture))
  }
  
  def getPerformerByID(id: Long)(implicit config: WithConfig): Option[Performer] = {
    val performerDetails = NamedDB(config.db.poolName).readOnly{implicit session =>
      val perfTableCollection = sql"""
        SELECT id, name, profile_picture_id, shown FROM performer
        WHERE id=$id
      """.foldLeft(PerformerBuilder())(buildFromPerformerTable)
      val skillTableCollection = sql"""
        SELECT skill FROM performer_skill WHERE performer_id=$id
      """.foldLeft(perfTableCollection)(buildFromSkillTable)
      val pictureTableCollection = sql"""
        SELECT picture_id FROM performer_picture WHERE performer_id=$id
      """.foldLeft(skillTableCollection)(buildFromPictureTable)
      pictureTableCollection
    }
    performerDetails.build()
  }
}

case class Skill(skill: String) extends AnyVal


/**
 * The JSON formatter for a performer.
 * This needs to be instantiated as a class because it depends on the picture formatter.
 * @param config
 */
class PerformerJsonFormat(implicit config: WithConfig)
  extends RootJsonFormat[Performer]
  with DefaultJsonProtocol {
  import Skill.SkillJsonFormat._

  implicit val pictureJsonFormatter = new PictureJsonFormatter()
  def write(performer: Performer) =
    JsObject(
      "id" -> JsNumber(performer.id),
      "name" -> JsString(performer.name),
      "skills" -> performer.skills.toJson,
      "profile_picture" -> performer.profilePicture.toJson,
      "other_pictures" -> performer.otherPictures.toJson,
      "shown" -> JsBoolean(performer.shown)
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
        shown <- fields.get("shown")
      } yield (id, username, skills, profilePicture, otherPictures, shown)
      performerFields match {
        case Some((JsNumber(id),
                   JsString(name),
                   JsArray(_skills),
                   _profilePic,
                   JsArray(_otherPics),
                   JsBoolean(shown))) =>
          val skills = _skills.map(_.convertTo[Skill]).toSet
          val performerPic = _profilePic.convertTo[Picture]
          val otherPics = _otherPics.map(_.convertTo[Picture]).toSet
          Performer(id.toLong, name, skills, performerPic, otherPics, shown)
        case _ => deserializationError("Perfomer expected")
      }
    case _ => deserializationError("Performer expected")
  }
}

object Skill extends DefaultJsonProtocol {
  implicit object SkillJsonFormat extends RootJsonFormat[Skill] {
    def write(skill: Skill) = JsString(skill.skill)
    def read(value: JsValue) = value match {
      case JsString(v) => Skill(v)
      case _ => deserializationError("Skill expected")
    }
  }
}
