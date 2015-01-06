package com.circusoc.simplesite.services

import com.circusoc.simplesite.{Core, CorsAcceptHeadersHeader, CorsMethodHeader, CorsOriginHeader}
import spray.http.HttpResponse
import spray.routing.HttpService

trait CorsService extends HttpService {
  this: Core =>
  val corsRoutes = {
    options {
      headerValueByName("Access-Control-Request-Headers") {headers =>
        respondWithHeaders(new CorsOriginHeader(config.paths.cdnUrl), new CorsMethodHeader(), new CorsAcceptHeadersHeader(headers)) {
          complete {
            HttpResponse()
          }
        }
      } ~
      respondWithHeaders(new CorsOriginHeader(config.paths.cdnUrl), new CorsMethodHeader()) {
        complete {
          HttpResponse()
        }
      }
    }
  }
}
