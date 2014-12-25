package com.circusoc.simplesite.auth

import java.util.UUID

import com.circusoc.simplesite.WithConfig
import com.circusoc.simplesite.users.{AuthenticatedUser, User}
import scalikejdbc.{NamedDB, _}




object Auth {
  def getToken(user: AuthenticatedUser)(implicit config: WithConfig): AuthToken = {
    config.db.getDB.autoCommit { implicit session =>
      val token = UUID.randomUUID().toString
      sql"""INSERT INTO token (user_id, token) VALUES (${user.id}, $token)""".execute()()
      AuthToken(token)
    }
  }
  def revokeToken(token: AuthToken)(implicit config: WithConfig): Boolean = {
    NamedDB(config.db.poolName).autoCommit { implicit session =>
      val c = sql"""DELETE FROM token WHERE token=${token.token}""".executeUpdate()()
      c > 0
    }
  }
  def checkToken(token: String)(implicit config: WithConfig): Option[AuthenticatedUser] = {
    NamedDB(config.db.poolName).readOnly {
      implicit session =>
        val check = sql"""SELECT user_id FROM token WHERE token=$token""".map(_.int(1)).headOption()()
        for {
          id <- check
          user <- User.getUserByID(id)
        } yield new AuthenticatedUser(user.id, user.username, user.userPermissions)
    }
  }
}

case class AuthToken(token: String)
case class UsernamePassword(username: String, password: String)

