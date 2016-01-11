package utils

import javax.inject.Inject

import com.mohiva.play.silhouette.api.SecuredErrorHandler
import controllers.routes
import play.api.http.DefaultHttpErrorHandler
import play.api.i18n.Messages
import play.api.mvc.Results._
import play.api.mvc.{ Result, RequestHeader }
import play.api.routing.Router
import play.api.{ OptionalSourceMapper, Configuration }

import scala.concurrent.Future


class ErrorHandler @Inject() (
                               env: play.api.Environment,
                               config: Configuration,
                               sourceMapper: OptionalSourceMapper,
                               router: javax.inject.Provider[Router])
  extends DefaultHttpErrorHandler(env, config, sourceMapper, router)
  with SecuredErrorHandler {


  override def onNotAuthenticated(request: RequestHeader, messages: Messages): Option[Future[Result]] =
    Some(Future.successful(Redirect(routes.SignInOutController.signIn())))


  override def onNotAuthorized(request: RequestHeader, messages: Messages): Option[Future[Result]] =
    Some(Future.successful(Redirect(routes.SignInOutController.signIn()).flashing("error" -> Messages("access.denied")(messages))))

}
