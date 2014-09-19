package com.circusoc.simplesite.users

import com.circusoc.simplesite.WithConfig
import scalikejdbc._
import org.mindrot.jbcrypt.BCrypt
import spray.json._
import scala.Some
import com.circusoc.simplesite.users.permissions.Permission

// !!! IMPORTANT, else `convertTo` and `toJson` won't work correctly


/**
 *  A user - this is for admin users of the circusoc site.
 *
 *  It includes access control and authentication methods.
 */
class User(val id: Int, val username: String, val userPermissions: Set[permissions.Permission]) {
  def hasPermission(permission: permissions.Permission): Boolean = userPermissions.contains(permission)
  def addPermission(permission: permissions.Permission,
                    changingUser: AuthenticatedUser)(implicit WithConfig: WithConfig): User = {
    User.addPermission(this, permission, User.MayChangePermsProof.hasChangePermisProof(changingUser))
  }
  def removePermission(permission: permissions.Permission,
                       changingUser: AuthenticatedUser)(implicit WithConfig: WithConfig): User = {
    User.removePermission(this, permission, User.MayChangePermsProof.hasChangePermisProof(changingUser))
  }
  def changePassword(newPassword: Password,
                     changingUser: AuthenticatedUser)(implicit WithConfig: WithConfig): User = {
    User.changePassword(this, newPassword, User.MayChangePassProof.hasChangePerm(changingUser))
  }
}

class   AuthenticatedUser(
  _id: Int,
  _username: String,
  _permissions: Set[permissions.Permission])
  extends User(_id, _username, _permissions) {
  override def changePassword(newPassword: Password, changingUser: AuthenticatedUser)(implicit WithConfig: WithConfig): User = {
    User.changePassword(this, newPassword, User.MayChangePassProof.isChangingUser(this, changingUser))
  }
}

object User {
  private def collectUser(ub: UserBuilder, rs: WrappedResultSet): UserBuilder = {
    val id = rs.int("id")
    val username = rs.string("username")
    val permission = rs.stringOpt("permission")
    val res = ub.addId(id).addUsername(username)
    permission.map {
      p => res.addPermission(Permission.apply(p))
    }.getOrElse(res)
  }

  def getUserByID(id: Int)(implicit WithConfig: WithConfig): Option[User] = {
    val builder = DB.readOnly{implicit session =>
      sql"""
        SELECT
          id, username, permission
        FROM
          user
          LEFT JOIN permissions ON user.id=permissions.user_id
        WHERE id=$id""".foldLeft(UserBuilder())(collectUser)
    }
    builder.build()
  }

  def getUserByName(name: String)(implicit WithConfig: WithConfig): Option[User] = {
    val builder = DB.readOnly{implicit session =>
      sql"""
        SELECT
          id, username, permission
        FROM
          user
          LEFT JOIN permissions ON user.id=permissions.user_id
        WHERE username=$name""".foldLeft(UserBuilder())(collectUser)
    }
    builder.build()
  }

  case class UserBuilder(username: Option[String] = None,
                         id: Option[Int] = None,
                         permissions: List[Permission] = List()) {
    def addUsername(_username: String): UserBuilder = {
      assert(this.username.isEmpty || this.username.get == _username)
      copy(username=Some(_username))
    }
    def addId(_id: Int): UserBuilder = {
      assert(this.id.isEmpty || this.id.get == _id)
      copy(id=Some(_id))
    }
    def addPermission(_permission: Permission): UserBuilder = {
      copy(permissions=_permission :: permissions)
    }
    def build(): Option[User] = {
      id match {
        case Some(_) =>
          assert(username.nonEmpty)
          Some(new User(id.get, username.get, permissions.toSet))
        case None => None
      }
      
    }
  }

  def authenticateByUsername(name: String, pass: Password)
                            (implicit WithConfig: WithConfig): Option[AuthenticatedUser] = {
    DB.readOnly { implicit session =>
      val _hashedpw = sql"""
        SELECT password
        FROM
          user
        WHERE
          username=$name
      """.map(_.string("password")).headOption().apply()
      val _user = getUserByName(name)
      (_user, _hashedpw) match {
        case (Some(user), Some(hashedpw)) =>
          if (BCrypt.checkpw(pass.pass, hashedpw)) {
            Some(new AuthenticatedUser(user.id, user.username, user.userPermissions))
          } else {
            None
          }
        case _ => None
      }
    }
  }
  def changePassword(user: User, newpass: Password, mayChangeProof: MayChangePassProof)(implicit WithConfig: WithConfig): User = {
    DB.autoCommit{implicit session =>
      val password = BCrypt.hashpw(newpass.pass, BCrypt.gensalt())
      sql"UPDATE user SET password=$password WHERE id=${user.id}".executeUpdate().apply()
    }
    user
  }

  sealed trait MayChangePassProof
  object MayChangePassProof {
    def hasChangePerm(changingUser: AuthenticatedUser): MayChangePassProof = {
      assert(changingUser.hasPermission(permissions.ChangePasswordPermission()))
      new MayChangePassProof {}
    }
    def isChangingUser(changingUser: AuthenticatedUser, changedUser: User) = {
      assert(changingUser.id == changedUser.id)
      new MayChangePassProof {}
    }
    def isTest() = {
      println("TESTTESTTEST") // fixme
      new MayChangePassProof {}
    }
  }
  
  def addPermission(to: User,
                    permission: permissions.Permission,
                    mayChangePermsProof: MayChangePermsProof)(implicit WithConfig: WithConfig): User = {
    DB.autoCommit{implicit session =>
      sql"INSERT INTO permission VALUES (${to.id}, ${permission.name})".execute().apply()
    }
    val user = getUserByID(to.id)
    assert(user.isDefined, "Invalid state - we added a permission, but couldn't find the user in the db afterwards.")
    user.get
  }

  def removePermission(from: User,
                       permission: permissions.Permission,
                       mayChangePermsProof: MayChangePermsProof)(implicit WithConfig: WithConfig): User = {

    DB.autoCommit{implicit session =>
      sql"DELETE FROM permission WHERE id=${from.id} AND permission=${permission.name}".execute().apply()
    }

    val user = getUserByID(from.id)
    assert(user.isDefined, "Invalid state - we removed a permission, but couldn't find the user in the db afterwards.")
    user.get
  }
  
  sealed trait MayChangePermsProof
  object MayChangePermsProof {
    def hasChangePermisProof(changingUser: AuthenticatedUser): MayChangePermsProof = {
      assert(changingUser.hasPermission(permissions.CanChangePermissionsPermission()))
      new MayChangePermsProof {}
    }
  }

  import permissions.Permission.PermissionJSONProtocol._
  object UserJSONProtocol extends DefaultJsonProtocol {
    implicit object UserJsonFormat extends RootJsonFormat[User] {
      def write(c: User) =
        JsObject(
          "id" -> JsNumber(c.id),
          "username" -> JsString(c.username),
          "permissions" -> JsArray(c.userPermissions.toJson)
      )

      def read(value: JsValue): User = value match {
        case o: JsObject =>
          val fields = o.fields
          val userFields: Option[(JsValue, JsValue, JsValue)] = for {
            id <- fields.get("id")
            username <- fields.get("username")
            permissions <- fields.get("permissions")
          } yield (id, username, permissions)
          userFields match {
            case Some((JsNumber(id), JsString(name), JsArray(_perms))) =>
              val perms = _perms.map(p => permissions.Permission.PermissionJSONProtocol.PermissionJsonFormat.read(p))
              new User(id.toIntExact, name, perms.toSet)
            case _ => deserializationError("User expected")
          }
        case _ => deserializationError("User expected")
      }
    }
  }
}
case class Password(pass: String) extends AnyVal