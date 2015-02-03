package com.circusoc.simplesite.services

import com.circusoc.simplesite.Core
import com.circusoc.simplesite.members._
import com.circusoc.simplesite.users.permissions
import org.slf4j.LoggerFactory
import spray.http._
import spray.json._
import spray.routing.HttpService

import scala.concurrent.ExecutionContext.Implicits.global


trait MemberService extends HttpService {
  this: Core with AuthService =>
  import com.circusoc.simplesite.services.NewMemberRequestJsonSupport._
  import com.circusoc.simplesite.members.MemberJsonProtocol._
  val logger = LoggerFactory.getLogger(this.getClass.getName)

  val memberroutes = path("member") {
    put {
      authenticate(authenticateUser) { user =>
        authorize(user.hasPermission(permissions.CanUpdateMembers)) {
          entity(as[NewMemberRequest]) {newmember =>
            if (newmember.isArc && newmember.studentNumber.isEmpty) complete {
              HttpResponse(StatusCodes.BadRequest,
                JsObject("error" -> JsBoolean(true),
                  "reason" -> JsString("If a student is an ARC member, they need a student number")).compactPrint)
            }
            else complete {
              val studentRecord = newmember.studentNumber.map {n => new StudentRecord(n, newmember.isArc)}
              config.db.getDB.localTx { implicit session =>
                Member.newMember(newmember.name, newmember.email, studentRecord, newmember.subscribed) match {
                  case Left(member) => HttpResponse(StatusCodes.OK, member.toJson.compactPrint)
                  case Right(DuplicateNameError) => HttpResponse(StatusCodes.BadRequest,
                    JsObject("error" -> JsBoolean(true),
                      "reason" -> JsString("Duplicate member name")).compactPrint)
                  case Right(DuplicateEmailError) => HttpResponse(StatusCodes.BadRequest,
                    JsObject("error" -> JsBoolean(true),
                      "reason" -> JsString("Duplicate email address")).compactPrint)
                  case Right(DuplicateSNumError) => HttpResponse(StatusCodes.BadRequest,
                    JsObject("error" -> JsBoolean(true),
                      "reason" -> JsString("Duplicate student number")).compactPrint)
                }
              }
            }
          }
        }
      }
    }
  }
}


case class NewMemberRequest(name: String,
                            email: Option[String],
                            studentNumber: Option[String],
                            isArc: Boolean,
                            subscribed: Boolean)

// $COVERAGE-OFF$
object NewMemberRequestJsonSupport {
  import spray.json.DefaultJsonProtocol._
  implicit val memberRequestJsonReader: RootJsonReader[NewMemberRequest] = jsonFormat5(NewMemberRequest)
  implicit val memberRequestJsonWriter: RootJsonWriter[NewMemberRequest] = jsonFormat5(NewMemberRequest)
}
// $COVERAGE-ON$
