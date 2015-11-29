import scala.concurrent.Future
import scalaz.{Monad, Functor}

/**
 * Created by yaroslav on 24.10.2015.
 */
//package object steroids {
//  implicit val FutureFunctor = new Functor[Future] {
//    def map[A, B](a: Future[A])(f: A => B): Future[B] = a map f
//  }
//
//  implicit val FutureMonad = new Monad[Future] {
//    def point[A](a: => A): Future[A] = Future(a)
//    def bind[A, B](fa: Future[A])(f: (A) => Future[B]): Future[B] = fa flatMap f
//  }
//}
