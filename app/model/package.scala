import com.mohiva.play.silhouette.api.{LoginInfo, Identity}
import reactivemongo.bson.BSONObjectID


package object model {
  val DefaultId = "default-id"
  type ID = String
  type FileID = String
  type UserID = String
  type JobID = String

  sealed trait State


  case object Free extends State

  case class Taken(userId: String) extends State

  case class Done(userId: String, form: String) extends State

  case class User(id: ID = DefaultId, name: String,loginInfo: LoginInfo,mail:String) extends Identity

  case class FileRecord(id: ID = DefaultId, filename: String, length: Long)

  case class Job(id: ID = DefaultId, ownerid: UserID, name: String, description: String, form: String)

  case class JobFile(id: ID,
                     parentJobId: JobID,
                     fileRecordId: FileRecord,
                     state: State)

  object JobFile {

    def p(parentJobId: JobID, fileRecord: FileRecord) = {
      JobFile(BSONObjectID.generate.stringify, parentJobId, fileRecord, Free)
    }
  }

}
