package controllers

import javax.inject.Inject

import com.mohiva.play.silhouette.api.{Environment, LogoutEvent, Silhouette}
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import play.api.i18n.MessagesApi
import play.api.mvc.AnyContent

import scala.concurrent.Future

class SignInOutController @Inject() (
  val messagesApi: MessagesApi,
  val env: Environment[model.User, CookieAuthenticator],
  socialProviderRegistry: SocialProviderRegistry)
  extends Silhouette[model.User, CookieAuthenticator] {

  def signIn = UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) => Future.successful(Redirect(routes.Application.index()))
      case None => Future.successful(Ok(views.html.next.signin()))
    }
  }

  def signOut = SecuredAction.async { implicit request =>
    val result = Redirect(routes.Application.index())
    env.eventBus.publish(LogoutEvent(request.identity, request, request2Messages))
    env.authenticatorService.discard(request.authenticator, result)
  }
}
