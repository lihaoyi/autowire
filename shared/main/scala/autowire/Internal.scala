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
    case e => throw Error.InvalidInput(e)
  }
  def checkKeys(keySet: Set[String], requiredArgs: Array[String]) = {
    val missing = requiredArgs.filterNot(keySet.contains)
    if (!missing.isEmpty)
      throw new autowire.Error.MissingParams(missing)
  }
  sealed trait HList{
    def #:[H](h : H) = Internal.#:(h, this)
  }


  final case class #:[+H, +T <: HList](head: H, tail: T) extends HList {
    override def toString = head+" #: "+tail.toString
  }
  case object HNil extends HList


  def validate[T <: HList](current: T): Either[List[Throwable], T] = current match {
    case #:(first, rest) =>
      (first, validate(rest)) match {
        case (util.Success(_), Left(errors)) => Left(errors)
        case (util.Success(success), Right(successes)) => Right((success #: successes).asInstanceOf[T])
        case (util.Failure(error), Left(errors)) => Left(error :: errors)
        case (util.Failure(error), Right(successes)) => Left(error :: Nil)
      }
    case HNil => Right(HNil.asInstanceOf[T])
  }
}