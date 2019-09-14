package autowire
import acyclic.file
/**
 * Utility classes to fit 0 or 2 context bounds into 1
 */
object Bounds{
  /**
   * Type representing two contexts bounds, in order to squeeze them into
   * one. Can be used as a context bound via `: Bounds.Two[TypeA, TypeB]`
   * and the implicits inside extracted via
   * `implicit (t1, t2) = Bounds.Two()`
   */
  class Two[T, T1[_], T2[_]]()(implicit val t1: T1[T], val t2: T2[T])
  object Two{
    implicit def twoBounds[T, T1[_], T2[_]](implicit t1: T1[T], t2: T2[T]) = new Two()(t1, t2)

    def apply[T, T1[_], T2[_]]()(implicit two: Two[T, T1, T2]) = (two.t1, two.t2)
  }

  /**
   * Type representing the lack-of a context-bound. Can be used as a
   * context bound via `: Bounds.None` an will always be satisfied.
   */
  class None[T]
  object None{
    implicit def noBound[T] = new None[T]
  }
}
