package com.circusoc.testgraph

import com.circusoc.simplesite.WithConfig
import com.circusoc.simplesite.users.permissions.Permission
import com.circusoc.simplesite.users.{Password, User, permissions}
import org.scalacheck.Gen

import scala.collection.mutable
import scala.reflect._

object UserTestGraph {
  def userFactory(implicit config: WithConfig): UserNodeFactory = new UserNodeFactory

  def addPermissionJoiner(implicit config: WithConfig): NodeJoiner[User, permissions.Permission, User] = {
    new NodeJoiner[User, permissions.Permission, User] {
      override def join(from: User, to: permissions.Permission): User =
        User.addPermission(from, to, new User.DebugMayAlterUsersProof())
    }
  }

  def permissionsFactory[Perm <: permissions.Permission](implicit tag: ClassTag[Perm]): TestNodeFactory[Perm] = {
    val newPermission = tag.runtimeClass.newInstance().asInstanceOf[Perm]
    new TestNodeFactory[Perm] { override def randomItem(): Perm = newPermission }
  }
}

class UserNodeFactory(implicit config: WithConfig) extends TestNodeFactory[User] {
  private val usernames = mutable.HashSet[String]()
  private val passwords = mutable.HashMap[User, Password]()
  override def randomItem(): User = synchronized {
    val username: String = generateUsername()
    usernames.add(username)
    addToDatabase(username)
  }

  def retrieveUserPassword(user: User): Password = synchronized(passwords(user))

  def addToDatabase(username: String): User = synchronized {
    val password = Gen.alphaStr.sample.get
    val user = User.addUser(username, Password(password), new User.DebugMayAlterUsersProof())
    passwords(user) = Password(password)
    user
  }

  def generateUsername(): String = synchronized {
    val username = Gen.alphaStr.sample.get
    if (usernames.contains(username)) generateUsername()
    else username
  }
}


