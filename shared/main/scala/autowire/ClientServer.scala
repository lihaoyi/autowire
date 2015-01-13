package autowire

import scala.concurrent.Future
import scala.language.experimental.macros
/**
 * A client to make autowire'd function calls to a particular interface.
 * A single client can only make calls to one interface, but it's not a
 * huge deal. Just make a few clients (they can all inherit/delegate the
 * `callRequest` method) if you want multiple targets.
 */
trait Client[PickleType, Reader[_], Writer[_]] extends Serializers[PickleType, Reader, Writer] {
  type Request = Core.Request[PickleType]
  /**
   * Actually makes a request
   *
   * @tparam Trait The interface that this autowire client makes its requests
   *               against.
   */
  def apply[Trait] = ClientProxy[Trait, PickleType, Reader, Writer](this)

  /**
   * A method for you to override, that actually performs the heavy
   * lifting to transmit the marshalled function call from the [[Client]]
   * all the way to the [[Core.Router]]
   */
  def doCall(req: Request): Future[PickleType]

}

/**
 * Proxy type that you can call methods from `Trait` on, which (when
 * followed by a `.call()` call) will turn into an RPC using the original
 * [[Client]]
 */
case class ClientProxy[Trait,
                       PickleType,
                       Reader[_],
                       Writer[_]]
                      (self: Client[PickleType, Reader, Writer])

trait Server[PickleType, Reader[_], Writer[_]] extends Serializers[PickleType, Reader, Writer] {
  type Request = Core.Request[PickleType]
  type Router = Core.Router[PickleType]
  /**
   * A macro that generates a `Router` PartialFunction which will dispatch incoming
   * [[Requests]] to the relevant method on [[Trait]]
   */
  def route[Trait](target: Trait): Router = macro Macros.routeMacro[Trait, PickleType]
  def innerRoute[Trait](target: Trait): Router = macro Macros.innerRouteMacro[Trait, PickleType]

}


trait Serializers[PickleType, Reader[_], Writer[_]] {
  def read[Result: Reader](p: PickleType): Result
  def write[Result: Writer](r: Result): PickleType
}