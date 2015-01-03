package com.circusoc.simplesite.performers

import spray.json._
import com.circusoc.simplesite.WithConfig
import scalikejdbc._
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
                     shown: Boolean) {
  def addSkill(skill: Skill, proof: MayAlterPerformersProof)(implicit config: WithConfig): Performer = {
    if (!skills.contains(skill)) {
      try {
        config.db.getDB.localTx { implicit session =>
          sql"""INSERT INTO performer_skill VALUES ($id, ${skill.skill})""".execute()()
          this.copy(skills = skills + skill)
        }
      } catch {
        // Ignore duplicate keys, which arise when two users try to add
        // skills at the same time
        // $COVERAGE-OFF$
        case e: org.h2.jdbc.JdbcSQLException =>
          if (e.getMessage.contains("Unique index or primary key violation")) this
          else throw e
        // $COVERAGE-ON$
      }
    } else {
      this
    }
  }
  def removeSkill(skill: Skill, proof: MayAlterPerformersProof)(implicit config: WithConfig): Performer = {
    config.db.getDB.autoCommit {implicit session =>
      if (skills.contains(skill)) {
        sql"""DELETE FROM performer_skill WHERE performer_id=$id and skill=${skill.skill}""".execute()()
        this.copy(skills = skills - skill)
      } else {
        this
      }
    }
  }

  def addPicture(picture: Picture, proof: MayAlterPerformersProof)(implicit config: WithConfig): Performer = {
    if (!otherPictures.contains(picture)) {
      config.db.getDB.autoCommit {implicit session =>
        sql"""INSERT INTO performer_picture VALUES ($id, ${picture.id})""".execute()()
        this.copy(otherPictures=otherPictures + picture)
      }
    } else {
      this
    }
  }

  def deletePicture(picture: Picture, proof: MayAlterPerformersProof)(implicit config: WithConfig): Performer = {
    if (otherPictures.contains(picture)) {
      config.db.getDB.autoCommit { implicit session =>
        sql"""DELETE FROM performer_pictures WHERE performer_id=$id AND picture_id=${picture.id}""".execute()()
        this.copy(otherPictures = otherPictures - picture)
      }
    } else {
      this
    }
  }

  def setProfilePic(picture: Picture, proof: MayAlterPerformersProof)(implicit config: WithConfig): Performer = {
    config.db.getDB.autoCommit { implicit session =>
      sql"""UPDATE performer SET profile_picture_id=${picture.id} WHERE id=$id""".execute()()
    }
    this.copy(profilePicture=picture)
  }
}

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
    val performerDetails = config.db.getDB.readOnly{implicit session =>
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

  def newPerformer(name: String, shown: Boolean, mayUpdateUserProof: MayAlterPerformersProof)
                  (implicit config: WithConfig): Performer = {
    val picture = Picture.defaultPicture.id
    val performerid = config.db.getDB.autoCommit {implicit session =>
      sql"""INSERT INTO performer (name,  profile_picture_id, shown)
                           VALUES ($name, $picture,          $shown)""".updateAndReturnGeneratedKey()()
    }
    getPerformerByID(performerid) match {
      case None =>
        // $COVERAGE-OFF$
        throw new Exception("Invalid state, something strange happened")
        // $COVERAGE-ON$
      case Some(p) => p
    }
  }

  def getPerformerIds(implicit config: WithConfig): List[Int] = {
    config.db.getDB.readOnly {implicit session =>
      sql"""SELECT id FROM performer""".map(_.int(1)).toList()()
    }
  }
}

sealed trait MayAlterPerformersProof
case class IsUser(user: com.circusoc.simplesite.users.User) extends MayAlterPerformersProof
case class DebugMayAlterPerformerProof(implicit config: WithConfig) extends MayAlterPerformersProof {
  assert(!config.isProduction)
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
