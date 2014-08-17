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

  /**
   * A very small HList, with the members forced to be wrapped in some
   * higher-kinded [[Wrapper]] so we can easily do transformations on them.
   */
  sealed trait HList[Wrapper[+_]]{
    def #:[H](h : Wrapper[H]) = Internal.#:(h, this)
  }

  final case class #:[+H, +T <: HList[Wrapper], Wrapper[+_]](head: Wrapper[H], tail: T) extends HList[Wrapper] {
    override def toString = head+" #: "+tail.toString
  }
  case class HNil[Wrapper[+_]]() extends HList[Wrapper]
  type Identity[+T] = T
  type FailMaybe[+T] = Either[Error.Param, T]
  type FailAll[+T] = Either[List[Error.Param], T]
  def validate(current: HList[FailMaybe]): FailAll[HList[Identity]] = current match {
    case #:(first, rest) =>
      (first, validate(rest)) match {
        case (Right(_), Left(errors)) => Left(errors)
        case (Right(success), Right(successes)) => Right(success #: successes)
        case (Left(error), Left(errors)) => Left(error :: errors)
        case (Left(error), Right(successes)) => Left(error :: Nil)
      }
    case HNil() =>
      Right(HNil[Identity]())
  }

  def read[P, T](dict: Map[String, P], default: => FailMaybe[T], name: String, thunk: P => T): FailMaybe[T] = {
    dict.get(name).fold[Either[autowire.Error.Param, T]](default)( x =>
      util.Try(thunk(x)) match {
        case scala.util.Success(value) => Right(value)
        case scala.util.Failure(error) => Left(autowire.Error.Param.Invalid(name, error))
      }
    )
  }
}