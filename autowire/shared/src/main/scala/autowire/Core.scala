package autowire

import scala.concurrent.Future
import acyclic.file

object Core {
  /**
   * The type returned by the [[Server.route]] macro; aliased for
   * convenience, but it's really just a normal `PartialFunction`
   * and can be combined/queried/treated like any other.
   */
  type Router[PickleType] = PartialFunction[Request[PickleType], Future[PickleType]]

  /**
   * A marshalled autowire'd function call.
   *
   * @param path A series of path segments which illustrate which method
   *             to call, typically the fully qualified path of the
   *             enclosing trait plus the path to the member
   * @param args Serialized arguments for the method that was called. Kept
   *             as a Map of arg-name -> serialized value. Values which
   *             exactly match the default value are omitted, and are
   *             simply re-constituted by the receiver.
   */
  case class Request[PickleType](path : Seq[String], args: Map[String, PickleType])
}

trait Error extends Exception
object Error{
  /**
   * Signifies that something went wrong when de-serializing the
   * raw input into structured data.
   *
   * This can contain multiple exceptions, one for each parameter.
   */
  case class InvalidInput(exs: Param*) extends Exception with Error {
    override def toString() = exs.mkString("InvalidInput(\n  ", "\n  ", "\n)")
  }
  sealed trait Param
  object Param{

    /**
     * Some parameter was missing from the input.
     */
    case class Missing(param: String) extends Param

    /**
     * Something went wrong trying to de-serialize the input parameter;
     * the thrown exception is stored in [[ex]]
     */
    case class Invalid(param: String, ex: Throwable) extends Param
  }
}


