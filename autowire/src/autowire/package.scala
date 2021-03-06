import autowire.Macros

import scala.annotation.compileTimeOnly
import scala.concurrent.Future
import language.experimental.macros
import scala.language.{higherKinds, implicitConversions}

package object autowire extends autowire.Internal.LowPri {
  /**
   * A `PartialFunction` (usually generated by the [[Macros.route]] macro)
   * that takes in [[Request]] objects and spits out serialized
   * `Future[String]`s in response.
   *
   * It is not concerned with how the [[Request]] objects get to it, or
   * how the marshalled `Future[String]` will be transmitted back to the
   * client: it simply calls the function described by the [[Request]]
   * on the object that it was created with.
   *
   * Being a normal `PartialFunction`, they can be manipulated and chained
   * (e.g. via `orElse` or `andThen`) like `PartialFunction`s normally are.
   */

  /**
   * Provides the `.call()` syntax, that is used to mark a "remote"
   * method-call and turn it into a real RPC.
   */

  implicit def clientFutureCallable[T](t: Future[T]): Internal.ClientCallable[T] =
    new Internal.ClientCallable[T]
  /**
   * Helper implicit to make sure that any calls to methods on [[ClientProxy]]
   * are immediately followed by a `.call()` call
   */

  @compileTimeOnly("You have forgotten to append .call() to the end of an autowire call.")
  implicit def unwrapClientProxy[Trait, PickleType, Reader[_], Writer[_]]
  (w: ClientProxy[Trait, PickleType, Reader, Writer]): Trait = ???
}

