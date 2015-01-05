package com.circusoc.simplesite.services

import com.circusoc.simplesite.{CorsAcceptHeadersHeader, CorsMethodHeader, CorsOriginHeader}
import spray.http.HttpResponse
import spray.routing.HttpService

trait CorsService extends HttpService {
  val corsRoutes = {
    options {
      headerValueByName("Access-Control-Request-Headers") {headers =>
        respondWithHeaders(new CorsOriginHeader(), new CorsMethodHeader(), new CorsAcceptHeadersHeader(headers)) {
          complete {
            HttpResponse()
          }
        }
      } ~
      respondWithHeaders(new CorsOriginHeader(), new CorsMethodHeader()) {
        complete {
          HttpResponse()
        }
      }
    }
  }
}
