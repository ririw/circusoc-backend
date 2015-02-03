package com.circusoc.simplesite.services

import java.io.ByteArrayInputStream

import com.circusoc.simplesite.Core
import com.circusoc.simplesite.pictures.{MediaTypeException, PictureReference, PictureJsonFormatter, PictureResult}
import com.circusoc.simplesite.users.permissions.ModifyImagesPermission
import spray.http.{HttpResponse, _}
import spray.json._
import spray.routing.HttpService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

trait PictureService extends HttpService {
  this: Core with AuthService =>
  implicit val picFormatter = new PictureJsonFormatter()
  val pictureRoutes = {
    path("picture") {
      post {
        authenticate(authenticateUser) { user =>
          authorize(user.hasPermission(ModifyImagesPermission)) {
            respondWithHeader(HttpHeaders.`Content-Type`(MediaTypes.`application/json`)) {
              entity(as[MultipartFormData]) { formData =>
                complete {
                    val _file = formData.fields.get("img")
                    _file match {
                      case None => HttpResponse(StatusCodes.BadRequest,
                        JsObject("error" -> JsString("You must upload an image, under 'img'")).compactPrint)
                      case Some(BodyPart(entity, headers)) =>
                        val file: ByteArrayInputStream = new ByteArrayInputStream(entity.buffer)
                        val pic = PictureResult(file)
                        pic match {
                          case Some(upload) =>
                            val uploaded: PictureReference = PictureReference.putPicture(upload, user)
                            HttpResponse(StatusCodes.Created, uploaded.toJson.compactPrint)
                          case None =>
                            HttpResponse(StatusCodes.BadRequest,
                              JsObject("error" -> JsString("Invalid file type")).compactPrint)
                        }
                    }
                  }
              }
            }
          }
        }
      }
    } ~
    path("picture" / LongNumber) {id =>
      get {
        val context = config.stats.pictureTime.time()
        val res = PictureReference(id).get().map { picture =>
          respondWithMediaType(picture.mediaType) {
            complete {
              HttpResponse(StatusCodes.OK, picture.data)
            }
          }
        }.getOrElse(complete{HttpResponse(StatusCodes.NotFound)})
        context.stop()
        context.close()
        res
      } ~
      delete {
        respondWithMediaType(MediaTypes.`application/json`) {
          authenticate(authenticateUser) { user =>
            authorize(user.hasPermission(ModifyImagesPermission)) {
                PictureReference.deletePicture(PictureReference(id), user) match {
                  case true => complete {
                    HttpResponse(StatusCodes.NoContent)
                  }
                  case false => complete {
                    HttpResponse(StatusCodes.NotFound)
                  }
                }
              }
          }
        }
      }
    }
  }
}
