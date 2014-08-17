package autowire

import scala.concurrent.Future
import language.experimental.macros
import scala.annotation.compileTimeOnly

/**
 * Holds a bunch of implementation details, which need to be public
 * for various reasons, but really shouldn't be used directly.
 */
object Internal{

  /**
   * Low priority call-anything extension-method-holding trait, to give the
   * call-Future extension method a chance to run first
   */
  trait LowPri {
    implicit def clientCallable[T](t: T) = new Internal.ClientCallable[T]
  }

  /**
   * A synthetic type purely meant to hold the `.call()` macro; gets
   * erased completely when the macro-implementation of `.call()` runs
   */
  class ClientCallable[T]{
    @compileTimeOnly(
      ".call() method is synthetic and should not be used directly"
    )
    def call(): Future[T] = macro Macros.clientMacro[T]
  }

  val invalidHandler: PartialFunction[Throwable, Nothing] = {
    case e => throw InputError("Invalid Input", e)
  }
}