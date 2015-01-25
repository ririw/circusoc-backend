package com.circusoc.testgraph

import com.circusoc.simplesite.WithConfig
import com.circusoc.simplesite.performers.{DebugMayAlterPerformerProof, PendingSkill, Performer, Skill}
import com.circusoc.simplesite.pictures.PictureReference
import com.circusoc.simplesite.users.permissions.Permission
import com.circusoc.simplesite.users.{Password, User}
import org.scalacheck.Gen
import org.slf4j.LoggerFactory

/**
 * Created by riri on 25/01/15.
 */
object PerformerTestGraph {
  val logger = LoggerFactory.getLogger(this.getClass.getName)

  def performerNodeFactory(implicit config: WithConfig): TestNodeFactory[Performer] = {
    new TestNodeFactory[Performer] {
      override def randomItem(): Performer = {
        val performerName: String = Gen.alphaStr.sample.get.take(10)
        logger.info(s"Creating performer with name $performerName")
        Performer.newPerformer(performerName, true, DebugMayAlterPerformerProof())
      }
    }
  }

  def pendingSkillNodeFactory(implicit config: WithConfig): TestNodeFactory[PendingSkill] = {
    new TestNodeFactory[PendingSkill] {
      override def randomItem(): PendingSkill = {
        val skill: String = Gen.alphaStr.sample.get.take(10)
        logger.info(s"Creating pending skill: $skill")
        PendingSkill(skill)
      }
    }
  }

  def adminNodeFactory(implicit conifg: WithConfig): TestNodeFactory[User] = {
    new TestNodeFactory[User] {
      var usernamecounter = 1
      override def randomItem(): User = {
        val username = usernamecounter.toString
        usernamecounter += 1
        val password = "password"
        logger.info(s"Creating user: $username with password $password")
        User.addUser(username, Password(password), new User.DebugMayAlterUsersProof())
      }
    }
  }

  def userPermissionJoiner(implicit config: WithConfig): NodeJoiner[User, Permission, User] = {
    new NodeJoiner[User, Permission, User] {
      override def join(from: User, to: Permission): User = {
        logger.info(s"Awarding user permission: ${to.name}")
        from.addPermission(to, new User.DebugMayAlterUsersProof())
      }
    }
  }

  def skillPictureJoin(implicit config: WithConfig): NodeJoiner[PendingSkill, PictureReference, Skill] = {
    new NodeJoiner[PendingSkill, PictureReference, Skill] {
      override def join(from: PendingSkill, to: PictureReference): Skill = {
        logger.info(s"Creating skill ${from.name} with picture ${to.id}")
        Skill.createOrGetSkill(from.name, to)
      }
    }
  }

  def performerSkillJoiner(implicit config: WithConfig): NodeJoiner[Performer, Skill, Performer] = {
    new NodeJoiner[Performer, Skill, Performer] {
      override def join(from: Performer, to: Skill): Performer = {
        logger.info(s"Awarding performer ${from.name} with skill ${to.skill}")
        from.addSkill(to, new DebugMayAlterPerformerProof())
      }
    }
  }

  def performerPictureJoiner(implicit config: WithConfig): NodeJoiner[Performer, PictureReference, Performer] = {
    new NodeJoiner[Performer, PictureReference, Performer] {
      override def join(from: Performer, to: PictureReference): Performer = {
        logger.info(s"Giving performer ${from.name} picture ${to.id}")
        from.addPicture(to, new DebugMayAlterPerformerProof())
      }
    }
  }

  def performerProfilePicJoiner(implicit config: WithConfig): NodeJoiner[Performer, PictureReference, Performer] = {
    new NodeJoiner[Performer, PictureReference, Performer] {
      override def join(from: Performer, to: PictureReference): Performer = {
        logger.info(s"Setting performer ${from.name} with profile picture ${to.id}")
        from.setProfilePic(to, new DebugMayAlterPerformerProof())
      }
    }
  }
}
