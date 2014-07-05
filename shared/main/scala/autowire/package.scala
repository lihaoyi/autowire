import scala.annotation.Annotation
import scala.concurrent.Future
import language.experimental.macros

package object autowire {


  case class InputError(ex: Exception) extends Exception

  def wrapInvalid[T](f: => T): T = {
    try { f }
    catch {
      case e: upickle.Invalid.Data => throw InputError(e)
      case e: upickle.Invalid.Json => throw InputError(e)
    }
  }

  type RouteType = PartialFunction[Request, Future[String]]
  case class Request(path: Seq[String], args: Map[String, String])

  abstract class Handler[T]{
    def apply[R: upickle.Reader](f: R): Future[R] = macro Macros.ajaxMacro[R]
    def apply[R: upickle.Reader](f: Future[R]): Future[R] = macro Macros.ajaxMacro[R]
    def callRequest(req: Request): Future[String]
  }
}

