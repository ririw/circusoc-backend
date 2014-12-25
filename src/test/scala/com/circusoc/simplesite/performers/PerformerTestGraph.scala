package com.circusoc.simplesite.performers

import com.circusoc.simplesite.WithConfig
import com.circusoc.simplesite.pictures.Picture
import com.circusoc.testgraph.{NodeJoiner, TestNodeFactory}
import org.scalacheck.Gen
import org.slf4j.LoggerFactory

import scala.collection.mutable

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

  def skillNodeFactory(implicit config: WithConfig): TestNodeFactory[Skill] = {
    new TestNodeFactory[Skill] {
      override def randomItem(): Skill = {
        val skill: String = Gen.alphaStr.sample.get.take(10)
        logger.info(s"Creating skill: $skill")
        Skill(skill)
      }
    }
  }

  def performerSkillJoiner(implicit config: WithConfig): NodeJoiner[Performer, Skill, Performer] = {
    new NodeJoiner[Performer, Skill, Performer] {
      override def _join(from: Performer, to: Skill): Performer = {
        logger.info(s"Awarding performer ${from.name} with skill ${to.skill}")
        from.addSkill(to, new DebugMayAlterPerformerProof())
      }
    }
  }

  def performerPictureJoiner(implicit config: WithConfig): NodeJoiner[Performer, Picture, Performer] = {
    new NodeJoiner[Performer, Picture, Performer] {
      override def _join(from: Performer, to: Picture): Performer = {
        logger.info(s"Giving performer ${from.name} picture ${to.id}")
        from.addPicture(to, new DebugMayAlterPerformerProof())
      }
    }
  }

  def performerProfilePicJoiner(implicit config: WithConfig): NodeJoiner[Performer, Picture, Performer] = {
    new NodeJoiner[Performer, Picture, Performer] {
      override def _join(from: Performer, to: Picture): Performer = {
        logger.info(s"Setting performer ${from.name} with profile picture ${to.id}")
        from.setProfilePic(to, new DebugMayAlterPerformerProof())
      }
    }
  }
}
