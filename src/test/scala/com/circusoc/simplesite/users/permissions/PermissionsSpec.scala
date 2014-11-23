package com.circusoc.simplesite.users.permissions

import org.scalatest.FlatSpec
import org.scalatest.Matchers._
import org.scalatest.prop.PropertyChecks
import com.circusoc.simplesite.users.permissions.Permission.PermissionJSONProtocol._
import spray.json._

class PermissionsSpec extends FlatSpec with PropertyChecks {
  it should "Correctly find permissions" in {
    Permission.apply("ChangePasswordPermission") should be(ChangePasswordPermission())
    Permission.apply("CanChangePermissionsPermission") should be(CanChangePermissionsPermission())
    Permission.apply("ModifyImagesPermission") should be(ModifyImagesPermission())
    Permission.apply("CanUpdateMembers") should be(CanUpdateMembers())
    Permission.apply("CanEditTagsPermission") should be(CanEditTagsPermission())
  }

  it should "Reject crazy permissions" in {
    intercept[PermissionConstructionException] {
      Permission.apply("0be0a897-d590-49d8-8c86-f369b2a0ddc6")
    }
  }

  it should "Serialize permissions" in {
    val validCombos = Table(
      "object" -> "str",
      ChangePasswordPermission() -> "ChangePasswordPermission",
      CanChangePermissionsPermission() -> "CanChangePermissionsPermission",
      ModifyImagesPermission() -> "ModifyImagesPermission",
      CanUpdateMembers() -> "CanUpdateMembers",
      CanEditTagsPermission() -> "CanEditTagsPermission"
    )

    forAll(validCombos) {
      (obj, str) =>
      obj.asPermission.toJson should be(JsString(str))
      "\"%s\"".format(str).parseJson.convertTo[Permission] should be(obj)
    }
  }

  it should "Not serialize fake permissions" in {
    intercept[PermissionConstructionException] {
      "\"1ee45109-3018-4a8f-8db1-e0989e6226f9\"".parseJson.convertTo[Permission]
    }
    intercept[DeserializationException] {
      "1".parseJson.convertTo[Permission]
    }
  }
}
