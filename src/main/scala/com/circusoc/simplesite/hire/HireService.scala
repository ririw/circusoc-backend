package com.circusoc.simplesite.hire

import spray.routing.HttpService
import com.circusoc.simplesite.Core
import spray.json._
import DefaultJsonProtocol._
import spray.httpx.SprayJsonSupport

// if you don't supply your own Protocol (see below)

trait HireService extends HttpService with SprayJsonSupport {
  this: Core =>
  val hireRoutes = {
    path("hire") {
      (put | post) {
        import HireRequestJsonSupport._
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