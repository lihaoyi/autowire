package autowire
import utest._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import utest.ExecutionContext.RunNow
import upickle.Implicits._
import scala.annotation.Annotation



// A trivial little system of annotation/controller/router/handler
// that can be used to test out the serialization/deserialization
// properties of autowire, but with everything running locally.
final class rpc extends Annotation

object Controller{
  var r = 1
  @rpc def multiply(x: Double, y: Double): String = s"$x*$y"
  @rpc def add(x: Int, y: Int = r + 1, z: Int = 10): String = s"$x+$y+$z"
  def subtract(x: Int, y: Int = r + 1): String = s"$x-$y"
}

object Handler extends autowire.Handler[rpc]{
  val router = Macros.route[rpc](Controller)
  case class NoSuchRoute(msg: String) extends Exception(msg)
  def callRequest(r: Request) = {
    router.lift(r)
      .fold[Future[String]](Future.failed(new NoSuchRoute("nope!")))(Future.successful)
  }
}


object Tests extends TestSuite{

  def await[T](f: Future[T]) = Await.result(f, 10 seconds)

  val tests = TestSuite{
    "basicCalls" - {
      val res1 = await(Handler.call(Controller.add(1, z = 3)))
      val res2 = await(Handler.call(Controller.add(1)))
      val res3 = await(Handler.call(Controller.add(1, 2)))
      val res4 = await(Handler.call(Controller.multiply(x = 1.2, 2.3)))

      assert(
        res1 == "1+2+3",
        res2 == "1+2+10",
        res3 == "1+2+10",
        res4 == "1.2*2.3"
      )
    }
    "notWebFails" - {
      import shapeless.test.illTyped
      illTyped { """Handler.call(Controller.subtract(1, 2))""" }
    }
    "notSimpleCallFails" - {
      import shapeless.test.illTyped
      illTyped { """Handler.call(1 + 1 + "")""" }
      illTyped { """Handler.call(1)""" }
      illTyped { """Handler.call(Thread.sleep(lols))""" }
    }
  }
}
