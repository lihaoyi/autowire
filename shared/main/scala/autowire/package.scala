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

  sealed trait Func[T, A]{
    def apply[R: upickle.Reader](f: T => R): Future[R] = macro Macros.ajaxMacro[R]
  }

  abstract class Handler[R]{
    @compileTimeOnly("Lols")
    def apply[T]: Func[T, R] = ???
    def callRequest(req: Request): Future[String]
  }
}

