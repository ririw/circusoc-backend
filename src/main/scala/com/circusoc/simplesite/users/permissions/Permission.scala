package com.circusoc.simplesite.users.permissions

import spray.json._

sealed trait Permission {
  val name: String
  // Used for JSON conversion
  val asPermission = this.asInstanceOf[Permission]
}

case class CanAdministerUsersPermission() extends Permission {
  val name = "CanAdministerUsersPermission"
}

case class CanChangePermissionsPermission() extends Permission {
  val name = "CanChangePermissionsPermission"
}

case class CanEditTagsPermission() extends Permission {
  val name = "CanEditTagsPermission"
}

case class CanUpdateMembers() extends Permission {
  val name = "CanUpdateMembers"
}

case class ModifyImagesPermission() extends Permission {
  val name = "ModifyImagesPermission"
}

object Permission {
  def apply(name: String): Permission = {name match {
    case "CanAdministerUsersPermission" => CanAdministerUsersPermission()
    case "CanChangePermissionsPermission" => CanChangePermissionsPermission()
    case "ModifyImagesPermission" => ModifyImagesPermission()
    case "CanUpdateMembers" => CanUpdateMembers()
    case "CanEditTagsPermission" => CanEditTagsPermission()
    case _ => throw new PermissionConstructionException(s"Invalid permission: $name")
  }}

  object PermissionJSONProtocol extends DefaultJsonProtocol {
    implicit object PermissionJsonFormat extends RootJsonFormat[Permission] {
      def write(c: Permission) =
        JsString(c.name)

      def read(value: JsValue): Permission = value match {
        case JsString(name) => Permission.apply(name)
        case _ => deserializationError("Permission expected")
      }
    }
  }
}

case class PermissionConstructionException(name: String) extends Exception
