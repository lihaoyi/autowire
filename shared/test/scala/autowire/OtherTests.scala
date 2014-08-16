package autowire
import utest._
import scala.concurrent.Future
import java.io.{ObjectInputStream, ByteArrayInputStream, ObjectOutputStream, ByteArrayOutputStream}
import utest.util.Tree
import utest.framework.Test
import scala.reflect.ClassTag

object ReflectionClient extends autowire.Client[ClassTag, ClassTag]{
//  val router = Macros.route[Api, ClassTag, ClassTag](Controller)
//  case class NoSuchRoute(msg: String) extends Exception(msg)
  def write[T: ClassTag](t: T) = {
//    val bo = new ByteArrayOutputStream()
//    val so = new ObjectOutputStream(bo)
//    so.writeObject(t)
//    so.flush()
//    bo.toString
    ???
  }
  def read[T: ClassTag](s: String) = {
//    val b = s.getBytes
//    val bi = new ByteArrayInputStream(b)
//    val si = new ObjectInputStream(bi)
//    si.readObject().asInstanceOf[T]
    ???
  }
  def callRequest(r: Request) = {
    ???
//    router.lift(r)
//      .getOrElse(Future.failed(new NoSuchRoute("nope!")))
  }
}
//object OtherTests extends TestSuite{
//  val tests = {
//    println("Hello World")
//  }
//}
