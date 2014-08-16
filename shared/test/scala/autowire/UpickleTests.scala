package autowire
import utest._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import utest.ExecutionContext.RunNow
import upickle._
import scala.annotation.Annotation




object uServer extends autowire.Server[upickle.Reader, upickle.Writer]{
  def write[T: upickle.Writer](t: T) = upickle.write(t)
  def read[T: upickle.Reader](s: String) = upickle.read[T](s)
  val routes = route[Api](Controller)
}

object uClient extends autowire.Client[upickle.Reader, upickle.Writer]{
  case class NoSuchRoute(msg: String) extends Exception(msg)
  def write[T: upickle.Writer](t: T) = upickle.write(t)
  def read[T: upickle.Reader](s: String) = upickle.read[T](s)
  def callRequest(r: Request) = {
    uServer.routes
           .lift(r)
           .getOrElse(Future.failed(new NoSuchRoute("No route found : " + r.path)))
  }
}

object UpickleTests extends TestSuite{
  import utest.PlatformShims.await
  println(utest.*)
  val tests = TestSuite{
    'basicCalls{
      val res1 = await(uClient[Api].add(1, 2, 3).call())
      val res2 = await(uClient[Api].add(1).call())
      val res3 = await(uClient[Api].add(1, 2).call())
      val res4 = await(uClient[Api].multiply(x = 1.2, Seq(2.3)).call())
      val res5 = await(uClient[Api].multiply(x = 1.1, ys = Seq(2.2, 3.3, 4.4)).call())

      assert(
        res1 == "1+2+3",
        res2 == "1+2+10",
        res3 == "1+2+10",
        res4 == "1.2*2.3",
        res5 == "1.1*2.2*3.3*4.4"
      )
    }
//    'aliased{
//      val api = UpickleClient
//      val res = await(api(_.add(1, 2, 4)))
//      assert(res == "1+2+4")
//    }
//    'async{
//      val res5 = await(UpickleClient(_.sloww(Seq("omgomg", "wtf"))))
//      assert(res5 == Seq(6, 3))
//    }
//    'compilationFailures{
//      'notSimpleCallFails{
////        await(Client(x => 1 + "omg" + 1 + ""))
////        * - compileError { """Client(x => 1 + 1 + "")""" }
////        * - compileError { """Client(x => 1)""" }
////        * - compileError { """Client(x => Thread.sleep(lols))""" }
//      }
//    }
//    'runtimeFailures{
//      'noSuchRoute{
//        val badRequest = Request(Seq("omg", "wtf", "bbq"), Map.empty)
//        assert(!UpickleServer.routes.isDefinedAt(badRequest))
//        intercept[MatchError] {
//          UpickleServer.routes(badRequest)
//        }
//      }
//      'inputError{
//        'keysMissing {
//          val badRequest = Request(Seq("autowire", "Api", "multiply"), Map.empty)
//          assert(UpickleServer.routes.isDefinedAt(badRequest))
//          intercept[InputError] {
//            UpickleServer.routes(badRequest)
//          }
//        }
//        'keysInvalid{
//          val badRequest = Request(
//            Seq("autowire", "Api", "multiply"),
//            Map("x" -> "[]", "ys" -> "[1, 2]")
//          )
//          assert(UpickleServer.routes.isDefinedAt(badRequest))
//          val InputError(
//            upickle.Invalid.Data(upickle.Js.Arr(), "Number")
//          ) = intercept[InputError] {
//            UpickleServer.routes(badRequest)
//          }
//        }
//        'invalidJson{
//          val badRequest = Request(
//            Seq("autowire", "Api", "multiply"),
//            Map("x" -> "[", "ys" -> "[1, 2]")
//          )
//          assert(UpickleServer.routes.isDefinedAt(badRequest))
//          val InputError(
//            upickle.Invalid.Json(_, _)
//          ) = intercept[InputError] {
//            UpickleServer.routes(badRequest)
//          }
//        }
//      }
//    }
  }
}
