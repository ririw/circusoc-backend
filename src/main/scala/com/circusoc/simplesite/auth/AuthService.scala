package com.circusoc.simplesite.auth

import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import spray.routing.HttpService
import spray.routing.authentication._
import scala.concurrent.Future
import com.circusoc.simplesite.{WithConfig, Core}
import scalikejdbc._
import java.util.UUID
import spray.httpx.SprayJsonSupport
import spray.json._
import com.circusoc.simplesite.users.{AuthenticatedUser, User}
import scala.Some
import scalikejdbc.NamedDB
import spray.routing.AuthenticationFailedRejection
import com.circusoc.simplesite.users.Password


trait AuthService extends HttpService with SprayJsonSupport {
  this: Core =>
  import UsernamePasswordJsonSupport._
  val authroutes = {
    path("login") {
      entity(as[UsernamePassword]) {usernamepw =>
        complete {
          val maybeAuthedUser = User.authenticateByUsername(usernamepw.username, Password(usernamepw.password))
          maybeAuthedUser match {
            case None => JsObject("error" -> JsString("Unknown username or password"))
            case Some(authedUser) =>
              val token = Auth.getToken(authedUser)
              JsObject("token" -> JsString(token.token))
          }
        }
      }
    }
    (path("logout") & post) {
      authenticate(authenticateUser) {user =>
        parameter("tok") {tok: String =>
          complete {
            val authToken = AuthToken(tok)
            JsBoolean(Auth.revokeToken(authToken)).compactPrint
          }
        }
      }
    }
  }
  def authenticateUser()(implicit config: WithConfig): ContextAuthenticator[AuthenticatedUser] = { ctx =>
    val tok = ctx.request.uri.query.get("tok")
    tok match {
      case None => Future(Left(AuthenticationFailedRejection("CredentialsMissing")))
      case Some(t) => doAuth(t)
    }
  }
  private def doAuth(tok: String)(implicit config: WithConfig): Future[Authentication[AuthenticatedUser]] = {
    Future {
      Auth.checkToken(tok) match {
        case None => Left(AuthenticationFailedRejection("CredentialsRejected"))
        case Some(u) => Right(u)
      }
    }
  }
}

object Auth {
  def getToken(user: AuthenticatedUser)(implicit config: WithConfig): AuthToken = {
    NamedDB(config.db.poolName).autoCommit { implicit session =>
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
// $COVERAGE-OFF$
object UsernamePasswordJsonSupport extends DefaultJsonProtocol {
  implicit val unPwJsonReader: RootJsonReader[UsernamePassword] = jsonFormat2(UsernamePassword)
  implicit val unPwJsonWriter: RootJsonWriter[UsernamePassword] = jsonFormat2(UsernamePassword)
}
// $COVERAGE-ON$
