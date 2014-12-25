package com.circusoc.simplesite.services

import com.circusoc.simplesite.Core
import com.circusoc.simplesite.tracking.{PageActionClientEvent, PageViewClientEvent, PageViewJsonReaders, TrackedEvent}
import spray.http.{HttpResponse, StatusCodes}
import spray.httpx.SprayJsonSupport
import spray.routing.HttpService

trait TrackingEventService extends HttpService with SprayJsonSupport {
  this: Core =>
  val trackingRoutes = {
    import com.circusoc.simplesite.tracking.PageViewJsonReaders._
    post {
      path("tracking" / "pageview") {
        entity(as[PageViewClientEvent]) {
          view =>
            complete {
              TrackedEvent.trackEvent(view.pageView)
              HttpResponse(StatusCodes.Created)
            }
        }
      } ~
      path("tracking" / "pageaction") {
        entity(as[PageActionClientEvent]) {
          view =>
            complete {
              TrackedEvent.trackEvent(view.pageAction)
              HttpResponse(StatusCodes.Created)
            }
        }
      }
    }
  }
}


