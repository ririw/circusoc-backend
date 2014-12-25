package com.circusoc.simplesite.services

import com.circusoc.simplesite.Core
import com.circusoc.simplesite.hire._
import spray.httpx.SprayJsonSupport
import spray.json._
import spray.routing.HttpService

trait HireService extends HttpService with SprayJsonSupport {
  this: Core =>
  val hireRoutes = {
    path("hire") {
      (put | post) {
        import com.circusoc.simplesite.hire.HireRequestJsonSupport._
        entity(as[HireRequest]) { hire =>
          complete {
            Hire.hire(EmailAddress(hire.email), hire.location.map(Location), hire.skills)
            JsObject("result" -> JsString("done"))
          }
        }
      }
    }
  }
}