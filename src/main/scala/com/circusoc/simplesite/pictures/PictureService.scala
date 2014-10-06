package com.circusoc.simplesite.pictures

import spray.routing.HttpService
import com.circusoc.simplesite.Core
import spray.http._
import spray.http.HttpResponse
import com.circusoc.simplesite.users.Auth
import com.circusoc.simplesite.users.permissions.ModifyImagesPermission
import scala.concurrent.ExecutionContext.Implicits.global
import java.io.ByteArrayInputStream
import spray.json._
import java.nio.file.Files
import scala.util.{Try, Success, Failure}

trait PictureService extends HttpService {
  this: Core with Auth =>
  implicit val picFormatter = new PictureJsonFormatter()
  val pictureRoutes = {
    path("picture") {
      post {
        authenticate(authenticateUser) { user =>
          authorize(user.hasPermission(ModifyImagesPermission())) {
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
                          case Success(upload) =>
                            val uploaded: Picture = Picture.putPicture(upload, user)
                            HttpResponse(StatusCodes.Created, uploaded.toJson.compactPrint)
                          case Failure(MediaTypeException(_)) =>
                            HttpResponse(StatusCodes.BadRequest,
                              JsObject("error" -> JsString("Invalid file type")).compactPrint)
                          case Failure(e) => throw e // throw the exception
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
      (get & autoChunk(2048)) {
          complete {
            val r: HttpResponse = Picture(id).get().map { picture =>
              HttpResponse(StatusCodes.OK, picture.data).withHeaders(HttpHeaders.`Content-Type`(picture.mediaType))
            }.getOrElse(HttpResponse(StatusCodes.NotFound))
            r
          }
        } ~
      delete {
        respondWithMediaType(MediaTypes.`application/json`) {
          authenticate(authenticateUser) { user =>
            authorize(user.hasPermission(ModifyImagesPermission())) {
                Picture.deletePicture(Picture(id), user) match {
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
