package model.services


import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile
import model.{JsonMongoFormats, User}
import play.api.libs.json.{Json, _}
import play.modules.reactivemongo.ReactiveMongoApi

import play.api.libs.json._

import play.modules.reactivemongo.json.ImplicitBSONHandlers._
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.bson.BSONObjectID


import scala.concurrent.Future

class UserServiceImpl @Inject()(mongoApi: ReactiveMongoApi) extends UserService {

  implicit val userFrmt = JsonMongoFormats.mongoFormats(Json.format[model.User])

  import LoginInfo._

  def users: JSONCollection = mongoApi.db.collection[JSONCollection]("persons")


  def retrieve(loginInfo: LoginInfo): Future[Option[model.User]] = {
    users.find(
      Json.obj("loginInfo.providerKey" -> loginInfo.providerKey, "loginInfo.providerID" -> loginInfo.providerID)).
      one[User].map(opt => {
      println(opt)
      opt
    })
  }

  def save(user: model.User) = {
    users.save(user).map(res => {
      res.originalDocument
      if (res.ok)
        user
      else
        throw new Exception()
    })
  }

  def save(profile: CommonSocialProfile) =
    if (profile.fullName.isDefined && profile.email.isDefined) {
      val user = User(BSONObjectID.generate.stringify, profile.fullName.get, profile.loginInfo, profile.email.get)
      users.find(Json.obj("name" -> profile.fullName.get, "mail" -> profile.email.get)).one[model.User]
        .flatMap {
        case None => users.save(user).map(res => {
          res.originalDocument
          if (res.ok)
            user
          else
            throw new Exception()
        })
        case Some(x) => Future.successful(x)
      }

    } else {
      Future.failed(new Exception("some error"))
    }

}
