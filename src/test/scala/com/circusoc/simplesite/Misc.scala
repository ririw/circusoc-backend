package com.circusoc.simplesite

import com.circusoc.simplesite.users.AuthenticatedUser
import com.circusoc.simplesite.users.permissions.ModifyImagesPermission

/**
 *
 */
object Misc {
  val superuser = new AuthenticatedUser(1, "joe",
    Set(ModifyImagesPermission())
  )
}
