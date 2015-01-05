package com.circusoc.simplesite.performers

import org.scalatest.FlatSpecLike
import org.scalatest.prop.PropertyChecks
import org.scalatest.Matchers._
import org.scalacheck._
import Arbitrary._
import Prop._
import com.circusoc.simplesite.performers.Performer.PerformerBuilder
import com.circusoc.simplesite.users.User.UserBuilder
import com.circusoc.simplesite.pictures.PictureReference

class PerformerBuilderSpec extends FlatSpecLike with PropertyChecks {
  import SkillChecks._
  import PictureChecks._
  it should "build users" in {
    val user = PerformerBuilder()
    forAll {(id: Long,
             name: String,
             skills: Set[Skill],
             profilePicture: PictureReference,
             otherPictures: Set[PictureReference],
             shown: Boolean
      ) =>
      val updatedWithThings = user.addId(id).addName(name).addProfilePicture(profilePicture).addShown(shown)
      val withSkills = skills.foldLeft(updatedWithThings){(user, skill) => user.addSkill(skill)}
      val withPictures = otherPictures.foldLeft(withSkills){(user, picture) => user.addPicture(picture)}

      val builtUser = withPictures.build()
      assert(builtUser.isDefined)
      val gotUser = builtUser.get
      gotUser.id should be(id)
      gotUser.name should be(name)
      gotUser.profilePicture should be(profilePicture)
      gotUser.shown should be(shown)
      gotUser.skills should be(skills)
      gotUser.otherPictures should be(otherPictures)
    }
  }
  it should "Accept the same thing twice" in {
    val user = PerformerBuilder()
    forAll {(id: Long,
             name: String,
             skills: Set[Skill],
             profilePicture: PictureReference,
             otherPictures: Set[PictureReference],
             shown: Boolean
              ) =>
      val updatedWithThings = user.addId(id).addName(name).addProfilePicture(profilePicture).addShown(shown)
      val withSkills = skills.foldLeft(updatedWithThings){(user, skill) => user.addSkill(skill)}
      val withPictures = otherPictures.foldLeft(withSkills){(user, picture) => user.addPicture(picture)}
      val updatedWithExtraThings = withPictures.addId(id).addName(name).addProfilePicture(profilePicture).addShown(shown)

      val builtUser = updatedWithExtraThings.build()
      assert(builtUser.isDefined)
      val gotUser = builtUser.get
      gotUser.id should be(id)
      gotUser.name should be(name)
      gotUser.profilePicture should be(profilePicture)
      gotUser.shown should be(shown)
      gotUser.skills should be(skills)
      gotUser.otherPictures should be(otherPictures)
    }
  }
  it should "not build id-less users" in {
    val nonUser = PerformerBuilder().
      addName("asd").
      addPicture(PictureReference(1)).
      addPicture(PictureReference(2)).
      addProfilePicture(PictureReference(3)).
      addShown(true).
      addSkill(Skill(1, "lol", PictureReference(1))).
      addSkill(Skill(2, "foo", PictureReference(1)))
    nonUser.build should be(None)
  }
  it should "not build name-less users" in {
    val nonUser = PerformerBuilder().
      addId(1).
      addPicture(PictureReference(1)).
      addPicture(PictureReference(2)).
      addProfilePicture(PictureReference(3)).
      addShown(true).
      addSkill(Skill(1, "lol", PictureReference(1))).
      addSkill(Skill(2, "foo", PictureReference(1)))
    intercept[AssertionError] {nonUser.build()}
  }
  it should "not build profile-less users" in {
    val nonUser = PerformerBuilder().
      addId(1).
      addName("asd").
      addPicture(PictureReference(1)).
      addPicture(PictureReference(2)).
      addShown(true).
      addSkill(Skill(1, "lol", PictureReference(1))).
      addSkill(Skill(2, "foo", PictureReference(1)))
    intercept[AssertionError] {nonUser.build()}

  }
  it should "not build shown-less users" in {
    val nonUser = PerformerBuilder().
      addId(1).
      addName("asd").
      addPicture(PictureReference(1)).
      addPicture(PictureReference(2)).
      addProfilePicture(PictureReference(3)).
      addSkill(Skill(1, "lol", PictureReference(1))).
      addSkill(Skill(2, "foo", PictureReference(1)))
    intercept[AssertionError] {nonUser.build()}
  }
  it should "Not accept different args" in {
    val fullUser = PerformerBuilder().
      addId(1).
      addName("asd").
      addPicture(PictureReference(1)).
      addPicture(PictureReference(2)).
      addProfilePicture(PictureReference(3)).
      addShown(true).
      addSkill(Skill(1, "lol", PictureReference(1))).
      addSkill(Skill(2, "foo", PictureReference(1)))
    intercept[AssertionError] {fullUser.addId(2)}
    intercept[AssertionError] {fullUser.addName("fefesc")}
    intercept[AssertionError] {fullUser.addProfilePicture(PictureReference(4))}
    intercept[AssertionError] {fullUser.addShown(false)}
  }

}


object SkillChecks extends Properties("Skill") {
  lazy val genSkill: Gen[Skill] = {
    for {
      id <- arbitrary[Int]
      name <- arbitrary[String].filter(!_.isEmpty)
      picture <- PictureChecks.genPictureReference
    } yield Skill(scala.math.abs(id), name, picture)
  }
  implicit lazy val arbSkill: Arbitrary[Skill] = Arbitrary(genSkill)
}

object PictureChecks extends Properties("Picture") {
  lazy val genPictureReference: Gen[PictureReference] = arbitrary[Long].map{v => labs(v) + 1}.map(PictureReference(_))
  implicit lazy val arbPicture: Arbitrary[PictureReference] = Arbitrary(genPictureReference)
  def labs(v: Long): Long = if (v < 0) -v else v
}