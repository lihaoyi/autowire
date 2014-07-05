import scala.annotation.Annotation
import scala.concurrent.Future
import language.experimental.macros

package object autowire {


  type RouteType = PartialFunction[Request, String]
  case class Request(path: Seq[String], args: Seq[String])


  abstract class Handler[T]{
    def call[R: upickle.Reader](f: R): Future[R] = macro Macros.ajaxMacro[R]
    def callRequest(req: Request): Future[String]
  }

  trait Futurizable[T]{
    def apply(t: T): Future[_]
  }
  object Futurizable extends LowPriorityImplicits{
    implicit def futureFuturizable[T] = new Futurizable[Future[T]]{
      def apply(t: Future[T]) = t
    }
  }
  trait LowPriorityImplicits{
    implicit def bareFuturizable[T] = new Futurizable[T] {
      def apply(t: T) = Future.successful(t)
    }
  }

  def futurize[T: Futurizable](t: T) = implicitly[Futurizable[T]].apply(t)
}

