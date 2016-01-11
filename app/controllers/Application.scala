package controllers

import javax.inject.Inject

import _root_.utils.FormCleaner
import com.mohiva.play.silhouette.api.{Environment, Silhouette}

import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import model._
import play.api.Logger
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import play.api.mvc._
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection._
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}
import reactivemongo.api.commands.UpdateWriteResult
import reactivemongo.api.gridfs.ReadFile
import reactivemongo.bson._

import scala.concurrent.Future

class Application @Inject()(
                             val messagesApi: MessagesApi,
                             val env: Environment[model.User, CookieAuthenticator],
                             val reactiveMongoApi: ReactiveMongoApi)
  extends Controller with MongoController with ReactiveMongoComponents with Silhouette[User, CookieAuthenticator] {

  import MongoController.readFileReads

  val info = (x: String) => Logger.logger.info(x)

  type JSONReadFile = ReadFile[JSONSerializationPack.type, JsString]
  val fsParser = gridFSBodyParser(reactiveMongoApi.gridFS)

  def persons: JSONCollection = db.collection[JSONCollection]("persons")

  def jobfiles: JSONCollection = db.collection[JSONCollection]("jobfiles")

  def jobs: JSONCollection = db.collection[JSONCollection]("jobs")

  def filesCol: JSONCollection = db.collection[JSONCollection]("fs.files")

  def index = SecuredAction.async { (req: SecuredRequest[AnyContent]) =>
    getAllJobs.
      map(x => Ok(views.html.next.joblist(x)("Available jobs")(req.identity)))

  }

  def report(jobId: String) = SecuredAction.async {
    for {
      files <- getFilesWithStates(jobId, Seq())
    } yield {
      Ok {
        ("filename,whodone,filledform" +: files.collect {
          case x@JobFile(_, _, _, s: model.Done) => x.fileRecordId.filename + "," + s.userId + "," + s.form
        }).mkString("\n")
      }
    }
  }

  def takenByUserFiles() = SecuredAction.async { req =>
    val taken = getTakenFiles(req.identity.id)
    val jobids = taken.map(s => s.map(_.parentJobId).distinct)
    for {
      taken <- getTakenFiles(req.identity.id)
      jobs <- getAllJobs
    } yield {
      Ok(views.html.next.takenfilelist(taken.map(jobfile => (jobfile, jobs.find(_.id == jobfile.parentJobId).get)))(req.identity))
    }

  }


  def newjobpage() = SecuredAction { req =>
    Ok(views.html.next.createnewjob(req.identity))
  }

  def newjob = SecuredAction.async {
    req =>
      val future = Future.successful(req.identity.id)
      future.flatMap { userid =>
        createJob(Job(ownerid = userid,
          name = req.body.asFormUrlEncoded.get.get("name").get.head,
          description = req.body.asFormUrlEncoded.get.get("description").get.head,
          form = FormCleaner.clean(req.body.asFormUrlEncoded.get.get("form").get.head)))
      }.map {
        success =>
          if (success) {
            Redirect(routes.Application.currentUserJobs)
          } else {
            BadRequest("Some error while creating job")
          }
      }.recover {
        case x => InternalServerError
      }
  }

  def attachFilesToJob(jobid: String) = Action.async(fsParser) { request =>
    val futureFile = Future.sequence(request.body.files.map(_.ref))
    futureFile.flatMap { files =>
      Future.sequence(
        files.map(
          file => saveJobFile(
            JobFile.p(
              parentJobId = jobid,
              FileRecord(file.id.as[String],
                file.filename.getOrElse("Unknown"),
                file.length)
            ))
        )
      )
        .map(x => Redirect(routes.Application.currentUserJobs))
    }.recover {
      case e: Throwable =>
        InternalServerError(e.getMessage)
    }
  }

  def files(jobid: String) = SecuredAction.async { req =>
    for {
      files <- getFilesWithStates(jobid, Seq())
      jobs <- getAllJobs
    } yield {
      Ok(views.html.next.myfiles(files, jobs.find(_.id == jobid).get)(req.identity))
    }
  }

  def file(fileId: String) = Action.async { req =>
    serve[JsString, JSONReadFile](reactiveMongoApi.gridFS)(reactiveMongoApi.gridFS.find[BSONDocument, JSONReadFile](BSONDocument("_id" -> fileId)))
  }

  def currentUserJobs = SecuredAction.async {
    implicit req =>
      jobsByUserId(req.identity.id).map(x => Ok(views.html.next.joblist(x)("My jobs")(req.identity)))
  }

  def takeFile(fileId: String) = SecuredAction.async {
    req =>
      updateStatus(fileId, Free, Taken(req.identity.id)).
        map(i => Redirect(routes.Application.takenByUserFiles))
  }

  def submitForm(fileId: String) = SecuredAction.async(parse.urlFormEncoded) {
    req =>
      val jsonForm = Json.toJson(req.request.body).toString()
      val userId = req.identity.id
      updateStatus(fileId, Taken(userId), Done(userId, jsonForm))
        .map(x => Redirect(routes.Application.takenByUserFiles))
  }

  // -- queries ----

  import model.JsonFormats._
  import play.api.libs.json.Reads._
  import play.api.libs.json._
  import play.modules.reactivemongo.json._

  implicit val userFrmt = JsonMongoFormats.mongoReads(Json.format[model.User])
  implicit val jobFile: Format[JobFile] = JsonMongoFormats.mongoFormats(Json.format[JobFile])
  implicit val jobfileo: OFormat[JobFile] = OFormat.apply(jobFile.reads, jobFile.writes(_).as[JsObject])
  implicit val jomfmt = JsonMongoFormats.mongoFormats(Json.format[Job])

  def getAllJobs = jobs.
    find(Json.obj()).
    cursor[Job]().
    collect[List]()


  def updateStatus(jobFileid: String, prevState: State, state: State): Future[UpdateWriteResult] = {
    jobfiles.update(
      Json.obj("fileRecordId.id" -> jobFileid, "state" -> Json.toJson(prevState)),
      Json.obj("$set" -> BSONDocument("state" -> Json.toJson(state)))
    )
  }


  def jobsByUserId(userId: String) =
    jobs.find(BSONDocument("ownerid" -> userId))
      .cursor[Job]()
      .collect[Seq]()

  def getFilesWithStates(job: String, states: Seq[State]): Future[Seq[JobFile]] = {
    val `accepted states`: JsObject = states match {
      case Seq() => Json.obj()
      case Seq(state) => Json.obj("state" -> Json.toJsFieldJsValueWrapper(state))
      case _ => Json.obj("$or" -> states.map(x => Json.obj("state" -> Json.toJsFieldJsValueWrapper(x))))
    }
    jobfiles
      .find(BSONDocument("parentJobId" -> job) ++ BSON.writeDocument(`accepted states`))
      .cursor[JobFile]()
      .collect[Seq]()

  }

  def getTakenFiles(owner: String): Future[Seq[JobFile]] = jobfiles
    .find(BSONDocument("state" -> Json.toJson(Taken(owner))))
    .cursor[JobFile]()
    .collect[Seq]()

  def saveJobFile[A](jobFile: JobFile) = jobfiles.insert(jobFile).map(_.ok)

  def createJob(job: Job) =
    jobs.insert(
      Json.obj("name" -> job.name,
        "ownerid" -> job.ownerid,
        "description" -> job.description,
        "form" -> job.form)).map(_.ok)

}
