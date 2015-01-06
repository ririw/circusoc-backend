package com.circusoc.simplesite.services

import com.circusoc.simplesite.auth.{Auth, AuthToken, UsernamePassword}
import com.circusoc.simplesite.users.{AuthenticatedUser, Password, User}
import com.circusoc.simplesite.{Core, WithConfig}
import spray.http._
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
      post {
        entity(as[UsernamePassword]) { usernamepw =>
          complete {
            val maybeAuthedUser = User.authenticateByUsername(usernamepw.username, Password(usernamepw.password))
            maybeAuthedUser match {
              case None => HttpResponse(StatusCodes.Unauthorized ,JsObject("error" -> JsString("Unknown username or password")).compactPrint)
              case Some(authedUser) =>
                val token = Auth.getToken(authedUser)
                HttpResponse(StatusCodes.OK, JsObject("token" -> JsString(token.token)).compactPrint,
                  List(HttpHeaders.`Set-Cookie`(HttpCookie("token",
                    token.token,
                    Some(DateTime.now + 3600000),
                    domain = Some(config.paths.cookieUrl))))
                )
            }
          }
        }
      }
    } ~
    (path("logout") & post) {
      authenticate(authenticateUser) {user =>
        parameter("tok") {tok: String =>
          complete {
            val authToken = AuthToken(tok)
            JsBoolean(Auth.revokeToken(authToken)).compactPrint
          }
        } ~
        cookie("token") {tok: HttpCookie =>
          complete {
            val authToken = AuthToken(tok.content)
            JsBoolean(Auth.revokeToken(authToken)).compactPrint
          }
        }
      }
    } ~
    (path("authenticated") & get) {
      authenticate(authenticateUser) { user =>
        complete(JsObject("authenticated" -> JsBoolean(true)).compactPrint)
      } ~
        complete(JsObject("authenticated" -> JsBoolean(false)).compactPrint)
    }
  }
  def authenticateUser()(implicit config: WithConfig): ContextAuthenticator[AuthenticatedUser] = { ctx =>
    val tok = ctx.request.uri.query.get("tok")
    tok match {
      case None =>
        ctx.request.cookies.find(_.name == "token") match {
          case None => Future(Left(AuthenticationFailedRejection("CredentialsMissing")))
          case Some(t) => doAuth(t.content)
        }
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