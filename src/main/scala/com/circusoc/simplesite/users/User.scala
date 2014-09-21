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
class User(val id: Int, val username: String, val userPermissions: Set[permissions.Permission]) extends Equals {
  def hasPermission(permission: permissions.Permission): Boolean = userPermissions.contains(permission)
  def addPermission(permission: permissions.Permission,
                    changingUser: AuthenticatedUser)(implicit config: WithConfig): User = {
    User.addPermission(this, permission, User.MayChangePermsProof.hasChangePermisProof(changingUser))
  }
  def removePermission(permission: permissions.Permission,
                       changingUser: AuthenticatedUser)(implicit config: WithConfig): User = {
    User.removePermission(this, permission, User.MayChangePermsProof.hasChangePermisProof(changingUser))
  }
  def changePassword(newPassword: Password,
                     changingUser: AuthenticatedUser)(implicit config: WithConfig): User = {
    User.changePassword(this, newPassword, User.MayChangePassProof.hasChangePerm(changingUser))
  }

  override def canEqual(that: Any): Boolean = that.isInstanceOf[User]
  override def equals(_that: Any): Boolean = {
    _that match {
      case that: User =>
        that.canEqual(this) &&
          this.canEqual(that) &&
          that.id == id &&
          that.username == username &&
          that.userPermissions == userPermissions
      case _ => false
    }
  }
  override def hashCode(): Int = 9623 * id.hashCode() ^
                                 8971 * username.hashCode ^
                                 652903 * userPermissions.hashCode() ^
                                 "User".hashCode
}

class  AuthenticatedUser(
  _id: Int,
  _username: String,
  _permissions: Set[permissions.Permission])
  extends User(_id, _username, _permissions) with Equals {
  override def changePassword(newPassword: Password, changingUser: AuthenticatedUser)(implicit config: WithConfig): User = {
    User.changePassword(this, newPassword, User.MayChangePassProof.isChangingUser(this, changingUser))
  }

  override def canEqual(that: Any): Boolean = that.isInstanceOf[AuthenticatedUser]
  override def equals(_that: Any): Boolean = {
    _that match {
      case that: AuthenticatedUser =>
        that.canEqual(this) &&
          this.canEqual(that) &&
          that.id == id &&
          that.username == username &&
          that.userPermissions == userPermissions
      case _ => false
    }
  }
  override def hashCode(): Int = 652499 * id.hashCode() ^
    981137 * username.hashCode ^
    25243 * userPermissions.hashCode() ^
    "AuthenticatedUser".hashCode()
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

  def getUserByID(id: Int)(implicit config: WithConfig): Option[User] = {
    val builder = NamedDB(config.db.symbol).readOnly{implicit session =>
      sql"""
        SELECT
          id, username, permission
        FROM
          user
          LEFT JOIN permission ON user.id=permission.user_id
        WHERE id=$id""".foldLeft(UserBuilder())(collectUser)
    }
    builder.build()
  }

  def getUserByName(name: String)(implicit config: WithConfig): Option[User] = {
    val builder = NamedDB(config.db.symbol).readOnly{implicit session =>
      sql"""
        SELECT
          id, username, permission
        FROM
          user
          LEFT JOIN permission ON user.id=permission.user_id
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
                            (implicit config: WithConfig): Option[AuthenticatedUser] = {
    NamedDB(config.db.symbol).readOnly { implicit session =>
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
        // $COVERAGE-OFF$
        case _ => None // this will only happen in certain race conditions.
        // $COVERAGE-ON$
      }
    }
  }
  def changePassword(user: User, newpass: Password, mayChangeProof: MayChangePassProof)(implicit config: WithConfig): User = {
    NamedDB(config.db.symbol).autoCommit{implicit session =>
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
    def isTest = {
      println("TESTTESTTEST") // fixme
      new MayChangePassProof {}
    }
  }
  
  def addPermission(to: User,
                    permission: permissions.Permission,
                    mayChangePermsProof: MayChangePermsProof)(implicit config: WithConfig): User = {
    NamedDB(config.db.symbol).autoCommit{implicit session =>
      sql"INSERT INTO permission VALUES (${to.id}, ${permission.name})".execute().apply()
    }
    val user = getUserByID(to.id)
    // $COVERAGE-OFF$
    assert(user.isDefined, "Invalid state - we added a permission, but couldn't find the user in the db afterwards.")
    // $COVERAGE-ON$
    user.get
  }

  def removePermission(from: User,
                       permission: permissions.Permission,
                       mayChangePermsProof: MayChangePermsProof)(implicit config: WithConfig): User = {

    NamedDB(config.db.symbol).autoCommit{implicit session =>
      sql"DELETE FROM permission WHERE user_id=${from.id} AND permission=${permission.name}".execute().apply()
    }

    val user = getUserByID(from.id)
    // $COVERAGE-OFF$
    assert(user.isDefined, "Invalid state - we removed a permission, but couldn't find the user in the db afterwards.")
    // $COVERAGE-ON$
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
          "permissions" -> c.userPermissions.toJson
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