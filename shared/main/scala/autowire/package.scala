import scala.annotation.{compileTimeOnly, Annotation}
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

  class Wrapper[W, R](val r: upickle.Reader[R])
  object Wrapper {
    implicit def future[T: upickle.Reader] = new Wrapper[Future[T], T](implicitly[upickle.Reader[T]])
    implicit def normal[T: upickle.Reader] = new Wrapper[T, T](implicitly[upickle.Reader[T]])
  }

  abstract class Client[T]{
    def apply[R, W]
             (f: T => W)
             (implicit wrapper: Wrapper[W, R]): Future[R] = macro Macros.clientMacro[R, W]
    def callRequest(req: Request): Future[String]
  }
}

