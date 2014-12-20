package com.circusoc.simplesite.performers

import com.circusoc.simplesite.WithConfig
import com.circusoc.simplesite.pictures.Picture
import com.circusoc.testgraph.{NodeJoiner, TestNodeFactory}
import org.scalacheck.Gen

import scala.collection.mutable

object PerformerTestGraph {
  def performerNodeFactory(implicit config: WithConfig): TestNodeFactory[Performer] = {
    new TestNodeFactory[Performer] {
      override def randomItem(): Performer = {
        val performerName: String = Gen.alphaStr.sample.get
        Performer.newPerformer(performerName, true, DebugMayAlterPerformerProof())
      }
    }
  }

  def skillNodeFactory(implicit config: WithConfig): TestNodeFactory[Skill] = {
    new TestNodeFactory[Skill] {
      override def randomItem(): Skill = Skill(Gen.alphaStr.sample.get)
    }
  }

  def performerSkillJoiner(implicit config: WithConfig): NodeJoiner[Performer, Skill, Performer] = {
    new NodeJoiner[Performer, Skill, Performer] {
      override def _join(from: Performer, to: Skill): Performer = {
        from.addSkill(to, new DebugMayAlterPerformerProof())
      }
    }
  }

  def performerPictureJoiner(implicit config: WithConfig): NodeJoiner[Performer, Picture, Performer] = {
    new NodeJoiner[Performer, Picture, Performer] {
      override def _join(from: Performer, to: Picture): Performer =
        from.addPicture(to, new DebugMayAlterPerformerProof())
    }
  }

  def performerProfilePicJoiner(implicit config: WithConfig): NodeJoiner[Performer, Picture, Performer] = {
    new NodeJoiner[Performer, Picture, Performer] {
      override def _join(from: Performer, to: Picture): Performer =
        from.setProfilePic(to, new DebugMayAlterPerformerProof())
    }
  }
}
