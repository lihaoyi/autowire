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
   * Example API method call: com.example.SampleApi.foo.bar()
   *
   * @param outerPath The fully qualified path of the
   *             enclosing trait Seq(com,example,SampleApi)
   * @param innerPath The path to the method that is called from within
   *                  the top-level api trait Seq(foo,bar)
   * @param args Serialized arguments for the method that was called. Kept
   *             as a Map of arg-name -> serialized value. Values which
   *             exactly match the default value are omitted, and are
   *             simply re-constituted by the receiver.
   */
  case class Request[PickleType](outerPath : Seq[String], innerPath : Seq[String], args: Map[String, PickleType]) {
    def path = outerPath ++ innerPath
  }
}

trait Error extends Exception
object Error{
  /**
   * Signifies that something went wrong when de-serializing the
   * raw input into structured data.
   *
   * This can contain multiple exceptions, one for each parameter.
   */
  case class InvalidInput(exs: Param*) extends Exception with Error
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


