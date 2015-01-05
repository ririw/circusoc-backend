package com.circusoc.simplesite.performers

import com.circusoc.simplesite.performers.Skill.SkillJsonFormat
import spray.json._
import com.circusoc.simplesite.WithConfig
import scalikejdbc._
import com.circusoc.simplesite.pictures.{PictureJsonFormatter, PictureReference}


/**
 * Performers have names, skills, profile pictures,
 * profile thumbnails and secondary pictures.
 *
 * Performers can be hidden or shown.
 */
case class Performer(id: Long,
                     name: String,
                     skills: Set[Skill],
                     profilePicture: PictureReference,
                     otherPictures: Set[PictureReference],
                     shown: Boolean) {
  def addSkill(skill: Skill, proof: MayAlterPerformersProof)(implicit config: WithConfig): Performer = {
    if (!skills.contains(skill)) {
      try {
        config.db.getDB.localTx { implicit session =>
          sql"""INSERT INTO performer_skill VALUES ($id, ${skill.id})""".execute()()
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
  def removeSkill(skill_id: Long, proof: MayAlterPerformersProof)(implicit config: WithConfig): Performer = {
    config.db.getDB.autoCommit {implicit session =>
      if (skills.exists(_.id == skill_id)) {
        println("REMOVIE")
        sql"""DELETE FROM performer_skill WHERE performer_id=$id and skill_id=$skill_id""".execute()()
        this.copy(skills = skills.filter(_.id != skill_id))
      } else {
        this
      }
    }
  }

  def addPicture(picture: PictureReference, proof: MayAlterPerformersProof)(implicit config: WithConfig): Performer = {
    if (!otherPictures.contains(picture)) {
      config.db.getDB.autoCommit {implicit session =>
        sql"""INSERT INTO performer_picture VALUES ($id, ${picture.id})""".execute()()
        this.copy(otherPictures=otherPictures + picture)
      }
    } else {
      this
    }
  }

  def deletePicture(picture: PictureReference, proof: MayAlterPerformersProof)(implicit config: WithConfig): Performer = {
    if (otherPictures.contains(picture)) {
      config.db.getDB.autoCommit { implicit session =>
        sql"""DELETE FROM performer_picture WHERE performer_id=$id AND picture_id=${picture.id}""".execute()()
        this.copy(otherPictures = otherPictures - picture)
      }
    } else {
      this
    }
  }

  def setProfilePic(picture: PictureReference, proof: MayAlterPerformersProof)(implicit config: WithConfig): Performer = {
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
    profilePicture: Option[PictureReference] = None,
    otherPictures: Set[PictureReference] = Set(),
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
    def addPicture(picture: PictureReference) = this.copy(otherPictures=otherPictures + picture)
    def addProfilePicture(_profilePicture: PictureReference) = {
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
    pb.addId(id).addName(name).addProfilePicture(PictureReference(profile_picture)).addShown(shown)
  }
  
  def buildFromSkillTable(pb: PerformerBuilder, rs: WrappedResultSet): PerformerBuilder = {
    val id = rs.long("id")
    val skill = rs.string("skill")
    val picture_id = rs.long("picture_id")
    pb.addSkill(Skill(id, skill, PictureReference(picture_id)))
  }

  def buildFromPictureTable(pb: PerformerBuilder, rs: WrappedResultSet): PerformerBuilder = {
    val picture = rs.long("picture_id")
    pb.addPicture(PictureReference(picture))
  }
  
  def getPerformerByID(id: Long)(implicit config: WithConfig): Option[Performer] = {
    val performerDetails = config.db.getDB.readOnly{implicit session =>
      val perfTableCollection = sql"""
        SELECT id, name, profile_picture_id, shown FROM performer
        WHERE id=$id
      """.foldLeft(PerformerBuilder())(buildFromPerformerTable)
      val skillTableCollection = sql"""
        SELECT id, skill, picture_id FROM
          performer_skill
          JOIN skill ON performer_skill.skill_id=skill.id
        WHERE performer_id=$id
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
    val picture = PictureReference.defaultPicture.id
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

  def getPerformerIds(implicit config: WithConfig): Set[Int] = {
    config.db.getDB.readOnly {implicit session =>
      sql"""SELECT id FROM performer""".map(_.int(1)).toList()()
    }.toSet
  }
}

sealed trait MayAlterPerformersProof
case class IsUser(user: com.circusoc.simplesite.users.User) extends MayAlterPerformersProof
case class DebugMayAlterPerformerProof(implicit config: WithConfig) extends MayAlterPerformersProof {
  assert(!config.isProduction)
}

case class Skill(id: Long, skill: String, picture: PictureReference)

object Skill {
  def getSkillByName(name: String)(implicit config: WithConfig): Option[Skill] = {
    config.db.getDB.readOnly{implicit session =>
      sql"""SELECT id, skill, picture_id FROM skill WHERE skill=$name""".map{r =>
        val id = r.long(1)
        val name = r.string(2)
        val picture_id = r.long(3)
        Skill(id, name, PictureReference(picture_id))
      }.first()()
    }
  }
  
  def createOrGetSkill(name: String, pictureReference: PictureReference)(implicit config: WithConfig): Skill = {
    val id = config.db.getDB.autoCommit {implicit session =>
      sql"""INSERT INTO skill (skill, picture_id) VALUES ($name, ${pictureReference.id})""".updateAndReturnGeneratedKey()()
    }
    Skill(id, name, pictureReference)
  }

  def allPerformerSkills()(implicit config: WithConfig): List[Skill] = {
    config.db.getDB.readOnly { implicit session =>
      // Includes a join to ensure we're only gettings skills that a performer has.
      sql"""SELECT skill.id, skill, picture_id
           FROM skill JOIN performer_skill ON skill.id=performer_skill.skill_id""".map { r =>
        val id = r.long(1)
        val name = r.string(2)
        val picture_id = r.long(3)
        Skill(id, name, PictureReference(picture_id))
      }.list()()
    }
  }

  class SkillJsonFormat(implicit config: WithConfig) extends RootJsonFormat[Skill] with DefaultJsonProtocol {
    implicit val pictureJsonFormatter = new PictureJsonFormatter()

    def write(skill: Skill) = JsObject("name" -> JsString(skill.skill), "picture" -> skill.picture.toJson)
    def read(value: JsValue) = value match {
      case JsString(v) => Skill.getSkillByName(v)(config).getOrElse(deserializationError("Unkown skill"))
      case _ => deserializationError("Skill expected")
    }
  }
}


/**
 * The JSON formatter for a performer.
 * This needs to be instantiated as a class because it depends on the picture formatter.
 * @param config
 */
class PerformerJsonFormat(implicit config: WithConfig)
  extends RootJsonFormat[Performer]
  with DefaultJsonProtocol {

  implicit val skillJsonFormatter = new SkillJsonFormat()
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
          val performerPic = _profilePic.convertTo[PictureReference]
          val otherPics = _otherPics.map(_.convertTo[PictureReference]).toSet
          Performer(id.toLong, name, skills, performerPic, otherPics, shown)
        case _ => deserializationError("Perfomer expected")
      }
    case _ => deserializationError("Performer expected")
  }
}

