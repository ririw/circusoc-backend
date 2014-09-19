package com.circusoc.simplesite.users

import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import spray.routing.{AuthenticationFailedRejection, HttpService}
import spray.routing.authentication.{Authentication, ContextAuthenticator}
import scala.concurrent.Future
import com.circusoc.simplesite.Core

trait Auth extends HttpService {
  this: Core =>

  val authroutes = {
    path("auth") {
      get {
        authenticate(authenticateUser) {user =>
          ???
        }
      }
    }
  }


  def authenticateUser: ContextAuthenticator[User] = {
    ctx =>
      val tok = ctx.request.uri.query.get("tok")
      tok match {
        case None => Future(Left(AuthenticationFailedRejection("CredentialsMissing")))
        case Some(t) => doAuth(t)
      }
  }
  private def doAuth(tok: String): Future[Authentication[User]] = {
    //here you can call database or a web service to authenticate the user
    Future {
      Either.cond(tok == "asd", new User(1, "joe", Set()), AuthenticationFailedRejection("CredentialsRejected"))
    }
  }
}