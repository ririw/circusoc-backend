package com.circusoc.simplesite.services

import com.circusoc.simplesite.Core
import com.circusoc.simplesite.performers.Skill.SkillJsonFormat
import com.circusoc.simplesite.performers.{Skill, Performer, PerformerJsonFormat}
import spray.http._
import spray.httpx.SprayJsonSupport
import spray.routing.HttpService
import spray.json._
import spray.json.DefaultJsonProtocol._

trait PerformerService extends HttpService with SprayJsonSupport {
  this: Core with AuthService =>

  implicit val performatter = new PerformerJsonFormat()
  implicit val skillformatter = new SkillJsonFormat()
  val performerRoutes = {
    path("performer" / LongNumber) {id =>
      get {
          complete {
            val performer = Performer.getPerformerByID(id)
            performer.map { j => HttpResponse(StatusCodes.OK, j.toJson.compactPrint)}.
              getOrElse(HttpResponse(StatusCodes.NotFound)): HttpResponse
        }
      }
    } ~
    path("performers") {
      get {
          complete {
            Performer.getPerformerIds.toJson.compactPrint
        }
      }
    } ~
    path("skills") {
      get {
        complete {
          Skill.allPerformerSkills().toJson.compactPrint
        }
      }
    }
  }
}
