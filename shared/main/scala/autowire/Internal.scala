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

  def checkKeys(keySet: Set[String], requiredArgs: Array[String]) = {
    val missing = requiredArgs.filterNot(keySet.contains)
    if (!missing.isEmpty)
      throw new autowire.Error.MissingParams(missing)
  }
  sealed trait HList[Wrapper[+_]]{
    def #:[H](h : Wrapper[H]) = Internal.#:(h, this)
  }

  final case class #:[+H, +T <: HList[Wrapper], Wrapper[+_]](head: Wrapper[H], tail: T) extends HList[Wrapper] {
    override def toString = head+" #: "+tail.toString
  }
  case class HNil[Wrapper[+_]]() extends HList[Wrapper]


  def validate(current: HList[util.Try]): Either[List[Throwable], HList[Some]] = current match {
    case #:(first, rest) =>
      val x = (first, validate(rest)) match {
        case (util.Success(_), Left(errors)) => Left(errors)
        case (util.Success(success), Right(successes)) => Right(Some(success) #: successes)
        case (util.Failure(error), Left(errors)) => Left(error :: errors)
        case (util.Failure(error), Right(successes)) => Left(error :: Nil)
      }
      println("VALIDATED " + x)
      x
    case HNil() =>
      println("VALIDATED " + HNil)
      Right(HNil[Some]())
  }
}