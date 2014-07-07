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

  class Func[T, A](h: Handler[A]){
    def apply[R: upickle.Reader](f: T => R): Future[R] = macro Macros.ajaxMacro[R]
    def callRequest(req: Request): Future[String] = h.callRequest(req)
  }

  abstract class Handler[R]{
    def apply[T]: Func[T, R] = new Func(this)
    def callRequest(req: Request): Future[String]
  }
}

