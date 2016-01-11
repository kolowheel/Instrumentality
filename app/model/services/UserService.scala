package model.services

import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.impl.providers.CommonSocialProfile

import scala.concurrent.Future

trait UserService extends IdentityService[model.User] {

  def save(user: model.User): Future[model.User]

  def save(profile: CommonSocialProfile): Future[model.User]
}
