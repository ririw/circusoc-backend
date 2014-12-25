package com.circusoc.simplesite.services

import com.circusoc.simplesite.auth.{Auth, AuthToken, UsernamePassword}
import com.circusoc.simplesite.users.{AuthenticatedUser, Password, User}
import com.circusoc.simplesite.{Core, WithConfig}
import spray.httpx.SprayJsonSupport
import spray.json.{JsBoolean, JsObject, JsString, _}
import spray.routing.{AuthenticationFailedRejection, HttpService}
import spray.routing.authentication._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


trait AuthService extends HttpService with SprayJsonSupport {
  this: Core =>
  import com.circusoc.simplesite.services.UsernamePasswordJsonSupport._
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

// $COVERAGE-OFF$
object UsernamePasswordJsonSupport extends DefaultJsonProtocol {
  implicit val unPwJsonReader: RootJsonReader[UsernamePassword] = jsonFormat2(UsernamePassword)
  implicit val unPwJsonWriter: RootJsonWriter[UsernamePassword] = jsonFormat2(UsernamePassword)
}
// $COVERAGE-ON$