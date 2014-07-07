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
class Rpc extends Annotation

@Rpc
trait Api{
  def multiply(x: Double, ys: Seq[Double]): String
  def add(x: Int, y: Int = 1 + 1, z: Int = 10): String
  def sloww(s: Seq[String]): Future[Seq[Int]]
}
object Controller extends Api{
  def multiply(x: Double, ys: Seq[Double]): String = x + ys.map("*"+_).mkString
  def add(x: Int, y: Int = 1 + 1, z: Int = 10): String = s"$x+$y+$z"
  def sloww(s: Seq[String]): Future[Seq[Int]] = Future.successful(s.map(_.length))
  def subtract(x: Int, y: Int = 1 + 1): String = s"$x-$y"
}

object Handler extends autowire.Handler[Rpc]{
  val router = Macros.route[Rpc](Controller)
  case class NoSuchRoute(msg: String) extends Exception(msg)

  def callRequest(r: Request) = {
    router.lift(r)
          .getOrElse(Future.failed(new NoSuchRoute("nope!")))
  }
}


object Tests extends TestSuite{

  def await[T](f: Future[T]) = Await.result(f, 10 seconds)

  val tests = TestSuite{
    'basicCalls{

      val res1 = await(Handler[Api](_.add(1, 2, 3)))
      val res2 = await(Handler[Api](_.add(1)))
      val res3 = await(Handler[Api](_.add(1, 2)))
      val res4 = await(Handler[Api](_.multiply(x = 1.2, Seq(2.3))))
      val res5 = await(Handler[Api](_.multiply(x = 1.1, ys = Seq(2.2, 3.3, 4.4))))

      assert(
        res1 == "1+2+3",
        res2 == "1+2+10",
        res3 == "1+2+10",
        res4 == "1.2*2.3",
        res5 == "1.1*2.2*3.3*4.4"
      )
    }
    'aliased{
      val api = Handler[Api]
      val res = await(api(_.add(1, 2, 4)))
      assert(res == "1+2+4")
    }
//    'async{
//      val res5 = await(Handler[Api](_.sloww(Seq("omgomg", "wtf"))))
//      assert(res5 == Seq(6, 3))
//    }
    'compilationFailures{
      'notWebFails{
        import shapeless.test.illTyped
        illTyped { """Handler[Api](x => Controller.subtract(1, 2))""" }
      }
      'notSimpleCallFails{
        import shapeless.test.illTyped
        illTyped { """Handler[Api](x => 1 + 1 + "")""" }
        illTyped { """Handler[Api](x => 1)""" }
        illTyped { """Handler[Api](x => Thread.sleep(lols))""" }
      }
    }
    'runtimeFailures{
      'noSuchRoute{
        val badRequest = Request(Seq("omg", "wtf", "bbq"), Map.empty)
        assert(!Handler.router.isDefinedAt(badRequest))
        intercept[MatchError] {
          Handler.router(badRequest)
        }
      }
      'inputError{
        'keysMissing {
          val badRequest = Request(Seq("autowire", "Api", "multiply"), Map.empty)
          assert(Handler.router.isDefinedAt(badRequest))
          intercept[InputError] {
            Handler.router(badRequest)
          }
        }
        'keysInvalid{
          val badRequest = Request(
            Seq("autowire", "Api", "multiply"),
            Map("x" -> "[]", "ys" -> "[1, 2]")
          )
          assert(Handler.router.isDefinedAt(badRequest))
          val InputError(
            upickle.Invalid.Data(upickle.Js.Array(Nil), "Number")
          ) = intercept[InputError] {
            Handler.router(badRequest)
          }
        }
        'invalidJson{
          val badRequest = Request(
            Seq("autowire", "Api", "multiply"),
            Map("x" -> "[", "ys" -> "[1, 2]")
          )
          assert(Handler.router.isDefinedAt(badRequest))
          val InputError(
            upickle.Invalid.Json(_, _, _, _)
          ) = intercept[InputError] {
            Handler.router(badRequest)
          }
        }
      }
    }
  }
}
