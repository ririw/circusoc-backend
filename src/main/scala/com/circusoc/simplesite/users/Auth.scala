package com.circusoc.simplesite.users

import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import spray.routing.{AuthenticationFailedRejection, HttpService}
import spray.routing.authentication.{Authentication, ContextAuthenticator}
import scala.concurrent.Future
import com.circusoc.simplesite.Core
import com.circusoc.simplesite.users.permissions.ModifyImagesPermission

trait Auth extends HttpService {
  this: Core =>

  val authroutes = {
    path("login") {
      post {
        // ??? // TODO: a simple login route, returning a token
        null
      }
      get {
        authenticate(authenticateUser) {user =>
          // ???
          null
        }
      }
    }
  }


  def authenticateUser: ContextAuthenticator[AuthenticatedUser] = {
    ctx =>
      val tok = ctx.request.uri.query.get("tok")
      tok match {
        case None => Future(Left(AuthenticationFailedRejection("CredentialsMissing")))
        case Some(t) => doAuth(t)
      }
  }
  private def doAuth(tok: String): Future[Authentication[AuthenticatedUser]] = {
    //here you can call database or a web service to authenticate the user
    Future {
      Either.cond(tok == "asd",
        new AuthenticatedUser(1, "joe", Set(ModifyImagesPermission())),
        AuthenticationFailedRejection("CredentialsRejected"))
    }
  }
}