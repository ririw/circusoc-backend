package com.circusoc.simplesite.users

import com.circusoc.simplesite.WithConfig
import com.circusoc.simplesite.users.permissions.Permission
import com.circusoc.testgraph.{NodeJoiner, TestNodeFactory}
import org.scalacheck.Gen

import scala.collection.mutable

object UserTestGraph {
  def userFactory(implicit config: WithConfig): TestNodeFactory[User] = {
    new TestNodeFactory[User] {
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
  }

  def addPermissionJoiner(implicit config: WithConfig): NodeJoiner[User, Permission, User] = {
    new NodeJoiner[User, Permission, User] {
      override def _join(from: User, to: Permission): User =
        User.addPermission(from, to, new User.DebugMayAlterUsersProof())
    }
  }
}
