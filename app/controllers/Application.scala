package controllers

import javax.inject.Inject

import play.api.mvc.MultipartFormData.FilePart
import reactivemongo.api.CursorProducer
import reactivemongo.api.ReadPreference.Primary
import reactivemongo.api.gridfs.{GridFS, ReadFile}
import reactivemongo.bson._

import scala.concurrent.Future

import play.api.Logger
import play.api.mvc.{Action, Result, Controller}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.util.{Failure, Try, Success}

import play.modules.reactivemongo.{// ReactiveMongo Play2 plugin
MongoController,
ReactiveMongoApi,
ReactiveMongoComponents
}

import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._

class Application @Inject()(val reactiveMongoApi: ReactiveMongoApi)
  extends Controller with MongoController with ReactiveMongoComponents {

  import MongoController.readFileReads

  type JSONReadFile = ReadFile[JSONSerializationPack.type, JsString]
  val fsParser = gridFSBodyParser(reactiveMongoApi.gridFS)

  def persons: JSONCollection = db.collection[JSONCollection]("persons")

  def index = Action { req =>
    Ok(views.html.main()).withSession(req.session +("user_id", "5623fa878465af3d073eb1ea"))
  }

  def redirected = Action { req =>
    req.queryString.get("msg") match {
      case Some(Seq("OK")) => Ok("successful uploaded")
      case _ => Ok("some errors")
    }
  }

  def files = Action.async { req =>
    val userId = req.session.get("user_id").get
    getFileIds(userId)
      .map(_.flatMap(x => Future(Ok(x.mkString("\n")))))
      .getOrElse(Future(BadRequest("")))

  }

  def file(fileId: String) = Action.async { req =>
    isHasFile(req.session.get("user_id").get, fileId).flatMap {
      allow =>
        if (allow) {
          val file = reactiveMongoApi.gridFS
            .find[BSONDocument, JSONReadFile](BSONDocument("_id" -> fileId))
          serve[JsString, JSONReadFile](reactiveMongoApi.gridFS)(file, CONTENT_DISPOSITION_INLINE)
        } else {
          Future(Ok("Not allowed"))
        }
    }
  }


  def upload = Action.async(fsParser) { request =>
    val futureFile =
      Future.sequence(request.body.files.map(_.ref))
    futureFile.flatMap { files =>
      request.session.get("user_id").map { userId =>
        updateQuery(userId, files.map(_.id.as[String]))
      }.map {
        futureRes =>
          (for {
            result <- futureRes
          } yield {
            if (result.ok)
              Redirect("/uploaded", Map("msg" -> Seq("OK")))
            else {
              Redirect("/uploaded", Map("msg" -> Seq("Error")))
            }
          }).recover {
            case e: Throwable =>
              Redirect("/uploaded", Map("msg" -> Seq("Error")))
          }
      }.getOrElse(Future(Redirect("/uploaded")))
    }.recover {
      case e: Throwable => InternalServerError(e.getMessage)
    }
  }


  // -- queries ----
  def isHasFile(userId: String, fileId: String) = {
    persons.find(BSONDocument("_id" -> BSONObjectID(userId),
      "pendingFiles" -> BSONDocument(
        "$elemMatch" -> BSONDocument("$eq" -> BSONString(fileId))
      ))).one[BSONValue].map(_.isDefined)
  }
  
  def getFiles(ids:Seq[String]) = {

  }

  def getFileIds(userId: String) = {
    import play.modules.reactivemongo.json._
    BSONObjectID.parse(userId).map {
      id => persons.find(BSONDocument("_id" -> id), BSONDocument("pendingFiles" -> 1))
        .one[JsValue](Primary)
        .map(_.map(x => (x \ "pendingFiles").as[Seq[String]]).getOrElse(Seq()))
    }
  }

  def updateQuery(userId: String, fileIds: Seq[String]) = {
    val selector = BSONDocument("_id" -> BSONObjectID(userId))
    val modifier = BSONDocument(
      "$addToSet" -> BSONDocument(
        "pendingFiles" -> BSONDocument(
          "$each" -> fileIds
        )
      )
    )
    persons.update(selector, modifier)
  }
}
