package com.circusoc.simplesite.tracking

import spray.routing.HttpService
import com.circusoc.simplesite.Core
import spray.http.StatusCodes
import spray.http.HttpResponse
import spray.httpx.SprayJsonSupport

trait TrackingEventService extends HttpService with SprayJsonSupport {
  this: Core =>
  val trackingRoutes = {
    import PageViewJsonReaders._
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


