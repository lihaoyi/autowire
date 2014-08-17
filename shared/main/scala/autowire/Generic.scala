package autowire

import scala.concurrent.Future
import acyclic.file
/**
 * Created by haoyi on 8/16/14.
 */
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
   *             enclosing trait followed by the name of the method
   * @param args Serialized arguments for the method that was called. Kept
   *             as a Map of arg-name -> serialized value. Values which
   *             exactly match the default value are omitted, and are
   *             simply re-constituted by the receiver.
   */
  case class Request[PickleType](path: Seq[String], args: Map[String, PickleType])
}

/**
 * Signifies that something went wrong when de-serializing the
 * raw input into structured data. The original exception is
 * preserved so you can see what happened.
 */
case class InputError(msg: String, ex: Throwable) extends Exception(msg, ex)