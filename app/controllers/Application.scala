package controllers

import javax.inject.Inject

import model._
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc.{Action, Controller}
import play.json.extra.Variants
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.ReadPreference.Primary
import reactivemongo.api.gridfs.ReadFile
import reactivemongo.bson._

import scala.concurrent.Future
import scala.util.Try

class Application @Inject()(val reactiveMongoApi: ReactiveMongoApi)
  extends Controller with MongoController with ReactiveMongoComponents {

  import MongoController.readFileReads

  val info = (x: String) => Logger.logger.info(x)

  type JSONReadFile = ReadFile[JSONSerializationPack.type, JsString]
  val fsParser = gridFSBodyParser(reactiveMongoApi.gridFS)

  def persons: JSONCollection = db.collection[JSONCollection]("persons")


  def jobfiles: JSONCollection = db.collection[JSONCollection]("jobfiles")

  def jobs: JSONCollection = db.collection[JSONCollection]("jobs")

  def filesCol: JSONCollection = db.collection[JSONCollection]("fs.files")

  def index = Action.async { implicit req =>
    implicit val jomfmt = JsonMongoFormats.mongoFormats(Json.format[Job])
    jobs.
      find(Json.obj()).
      cursor[Job]().
      collect[List]().
      map(x => Ok(views.html.main(x)).withSession(req.session +("user_id", "5623fa878465af3d073eb1ea")))

  }

  def userPage(usrId: String) = Action.async {
    implicit val jomfmt = JsonMongoFormats.mongoFormats(Json.format[Job])
    val userjobs = jobs.
      find(Json.obj()).
      cursor[Job]().
      collect[List]()
    val user = personById(usrId)
    for {
      jobs <- userjobs
      usr <- user
    } yield {
      Ok(views.html.user(usr, jobs))
    }

  }

  // todo dont use in production
  def person(name: String) = Action.async({
    implicit req =>
      personByName(name).map { id =>
        Ok(views.html.main(Seq(Job("", "", "", "")))).withSession(req.session +("user_id", id))
      }.recover {
        case x => Ok("Cookie is not setted" + x.toString)
      }
  })


  def newjobpage() = Action {
    Ok(views.html.createjob())
  }

  def newjob = Action.async {
    req =>
      val future: Future[String] = req.session.get("user_id") match {
        case Some(userid) => Future.successful(userid)
        case None => Future.failed(new IllegalStateException("You are non logged in"))
      }
      future.flatMap { userid =>
        createJob(Job(ownerid = userid,
          name = req.body.asFormUrlEncoded.get.get("name").get.head,
          form = req.body.asFormUrlEncoded.get.get("form").get.head))
      }.map {
        success =>
          if (success) {
            Ok("Job has been created")
          } else {
            BadRequest("Some error while creating job")
          }
      }.recover {
        case x => InternalServerError
      }
  }

  def attachFilesToJob(jobid: String) = Action.async(fsParser) { request =>
    val futureFile =
      Future.sequence(request.body.files.map(_.ref))
    futureFile.flatMap { files =>
      info("files")
      Future
        .sequence(files.map(file => createJobFile(
        JobFile.p(
          parentJobId = jobid,
          FileRecord(file.id.as[String],
            file.filename.getOrElse("Unknown"),
            file.length)
        ))))
        .map(x => Redirect("/uploaded", Map("msg" -> Seq("OK"))))
        .recover {
        case e: Throwable =>
          println(e)
          Redirect("/uploaded", Map("msg" -> Seq("Error")))
      }
    }.recover {
      case e: Throwable =>
        InternalServerError(e.getMessage)
    }
  }


  def redirected = Action { req =>
    req.queryString.get("msg") match {
      case Some(Seq("OK")) => Ok("successful uploaded")
      case _ => Ok("some errors")
    }
  }

  def files(jobid: String) = Action.async { req =>
    Logger.logger.info("HERE")
    getFiles(jobid)
      .flatMap(x => {
      Future(Ok(views.html.files(x)))
    })
  }

  def file(fileId: String) = Action.async { req =>
    //    val file = reactiveMongoApi.gridFS
    //      .find[BSONDocument, JSONReadFile](BSONDocument("_id" -> fileId))
    //    (for {
    //      Some(x) <- getFile(fileId)
    //      served <- serve[JsString, JSONReadFile](reactiveMongoApi.gridFS)(file, CONTENT_DISPOSITION_INLINE)
    //    } yield {
    //        served.withHeaders("Content-lenght" -> x.length.toString)
    //      }).recover {
    //      case e: Throwable => Logger.logger.info("SOME ERROR"); Ok("ERROr")
    //    }
    //    serve[JsString, JSONReadFile](reactiveMongoApi.gridFS)(file, CONTENT_DISPOSITION_INLINE)

    println("DOWNLOADING")
    serve[JsString, JSONReadFile](reactiveMongoApi.gridFS)(reactiveMongoApi.gridFS.find[BSONDocument, JSONReadFile](BSONDocument("_id" -> fileId)))
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

  def userJobs(userid: String) = Action.async {
    jobsById(userid).map(x => Ok(views.html.joblist(x)))
  }

  def currentUserJobs = Action.async {
    req =>
      jobsById(req.session.get("user_id").get).map(x => Ok(views.html.joblist(x)))
  }

  def takeFile(fileid: String) = Action.async {
    req =>
      println(req.session.get("user_id"))
      updateStatus(fileid, Free, Taken(req.session.get("user_id").get)).map( i => Ok(i.toString))
  }

  def submitForm(fileid : String) = Action{
    req => 
  }


  // -- queries ----

  import play.api.libs.json.Reads._
  import play.api.libs.json._


  def updateStatus(jobFileid: String, prevState: State, state: State) = {

    import model.JsonFormats._
    import play.api.libs.json._
    import play.modules.reactivemongo.json._
    import play.modules.reactivemongo.json.collection._
    import play.modules.reactivemongo.json._
    jobfiles.update(
      BSONDocument("fileRecordId.id" -> jobFileid, "state" -> Json.toJson(prevState)),
      BSONDocument("$set" -> BSONDocument("state" -> Json.toJson(state)))
    )
  }

  def submitFormQuery(userid: String, jobfileid: String, form: String) = {

  }

  def jobsById(userid: String) = {
    jobs.find(BSONDocument("ownerid" -> userid))
      .cursor[JsValue]()
      .collect[Seq]()
      .map {
      jsvalues =>
        jsvalues.map {
          value =>
            Job((value \ "_id" \ "$oid").as[String],
              (value \ "ownerid").as[String],
              (value \ "name").as[String],
              (value \ "form").as[String])
        }
    }
  }


  def personById(id: String): Future[User] = {
    implicit val userFrmt = JsonMongoFormats.mongoReads(Json.format[model.User])
    persons.
      find(BSONDocument("_id" -> BSONObjectID(id))).
      one[model.User].flatMap {
      case Some(usr) => Future.successful(usr)
      case _ => Future.failed[model.User](new IllegalArgumentException())
    }
  }

  def personByName(name: String): Future[String] =
    persons.find(BSONDocument("name" -> "test1"))
      .one[JsValue]
      .flatMap {
      case Some(json) => Future.successful((json \ "_id" \ "$oid").as[String])
      case None => Future.failed(new NoSuchElementException(s"Element with name:test1 is not present"))
    }

  def isHasFile(userId: String, fileId: String) =
    persons.find(BSONDocument("_id" -> BSONObjectID(userId),
      "pendingFiles" -> BSONDocument(
        "$elemMatch" -> BSONDocument("$eq" -> BSONString(fileId))
      ))).one[BSONValue]


  def getFiles(job: String): Future[Seq[JobFile]] = {
    import model.JsonFormats._
    implicit val jobFile: Format[JobFile] = JsonMongoFormats.mongoFormats(Json.format[JobFile])
    jobfiles
      .find(BSONDocument("parentJobId" -> job))
      .cursor[JsValue]()
      .collect[Seq]()
      .map { jsList =>
      jsList.map(x => {
        x.asOpt[JobFile]
      }).collect { case Some(x) => x }
    }
  }


  def getFileIds(userId: String): Try[Future[Seq[String]]] = {

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

  def createJobFile(jobFile1: JobFile) = {
    //    import play.modules.reactivemongo.json._
    //    import play.modules.reactivemongo.json.collection._
    import model.JsonMongoFormats._
    import model.JsonFormats._
    //    import play.modules.reactivemongo.json._
    println(jobFile1)
    implicit val jobFile: Format[JobFile] = JsonMongoFormats.mongoFormats(Json.format[JobFile])
    implicit val jobfileo: OFormat[JobFile] = OFormat.apply(jobFile.reads, jobFile.writes(_).as[JsObject])
    jobfiles.insert(jobFile1).map(_.ok)
  }

  def createJob(job: Job) =
    jobs.insert(BSONDocument("name" -> job.name, "ownerid" -> job.ownerid, "form" -> job.form)).map(_.ok)


}
