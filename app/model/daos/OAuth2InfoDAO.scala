package model.daos

import javax.inject.Inject

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.daos.DelegableAuthInfoDAO
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoApi
import play.modules.reactivemongo.json.collection.JSONCollection

import scala.concurrent.Future


class OAuth2InfoDAO @Inject()(reactiveMongoApi: ReactiveMongoApi)
  extends DelegableAuthInfoDAO[OAuth2Info] {

  case class Container(oAuth2: OAuth2Info, loginInfo: LoginInfo)

  import play.modules.reactivemongo.json.ImplicitBSONHandlers._
  import LoginInfo.jsonFormat

  implicit val oAuth2InfoFmt = Json.format[OAuth2Info]
  implicit val oAuth2InfoLoginInfoFmt = Json.format[Container]

  val oAuth2Infos = reactiveMongoApi.db.collection[JSONCollection]("oAuth2Info")

  def find(loginInfo: LoginInfo): Future[Option[OAuth2Info]] = {
    oAuth2Infos
      .find(Json.obj("loginInfo" -> loginInfo))
      .one[Container]
      .map(_.map { con => con.oAuth2 })

  }

  def add(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[OAuth2Info] =
    oAuth2Infos.save(Container(authInfo, loginInfo)).flatMap {
      result => if (result.ok) {
        Future.successful(authInfo)
      } else {
        Future.failed[OAuth2Info](new Exception("error while adding new oaut2info"))
      }
    }


  def update(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[OAuth2Info] =
    oAuth2Infos.update(
      Json.obj("loginInfo" -> loginInfo),
      Json.obj("$set" -> Json.obj("oAuth2" -> authInfo))).flatMap {
      result => if (result.ok) {
        Future.successful(authInfo)
      } else {
        Future.failed[OAuth2Info](new Exception("error while adding new oaut2info"))
      }
    }

  def save(loginInfo: LoginInfo, authInfo: OAuth2Info): Future[OAuth2Info] =
    find(loginInfo).flatMap {
      case Some(_) => update(loginInfo, authInfo)
      case None => add(loginInfo, authInfo)
    }


  def remove(loginInfo: LoginInfo) =
    oAuth2Infos.remove(Json.obj("loginInfo" -> loginInfo)).map(x => Unit)

}

