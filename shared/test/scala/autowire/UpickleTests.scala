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
    'aliased{
      val api = uClient[Api]
      val res = await(api.add(1, 2, 4).call())
      assert(res == "1+2+4")
    }
//    'async{
//      val res5 = await(uClient[Api].sloww(Seq("omgomg", "wtf")).call())
//      assert(res5 == Seq(6, 3))
//    }
    'compilationFailures{
      def check(error: CompileError, errorPos: String, msgs: String*) = {
        val stripped = errorPos.reverse.dropWhile("\n ".toSet.contains).reverse
        val pos = "\n" + error.pos
        assert(pos == stripped)
        for(msg <- msgs){
          assert(error.msg.contains(msg))
        }
      }
      * - check(
        compileError("123.call()"),
        """
        compileError("123.call()"),
                      ^
        """,
        "You can't call the .call() method on 123"
      )

      * - check(
        compileError("uClient[Api].add(1, 2, 3).toString.call()"),
        """
        compileError("uClient[Api].add(1, 2, 3).toString.call()"),
                                                             ^
        """,
        "You can't call the .call() method",
        "add(1, 2, 3).toString()"
      )
    }
    'runtimeFailures{
      'noSuchRoute{
        val badRequest = Request(Seq("omg", "wtf", "bbq"), Map.empty)
        assert(!uServer.routes.isDefinedAt(badRequest))
        intercept[MatchError] {
          uServer.routes(badRequest)
        }
      }
      'inputError{
        'keysMissing {
          val badRequest = Request(Seq("autowire", "Api", "multiply"), Map.empty)
          assert(uServer.routes.isDefinedAt(badRequest))
          intercept[InputError] {
            uServer.routes(badRequest)
          }
        }
        'keysInvalid{
          val badRequest = Request(
            Seq("autowire", "Api", "multiply"),
            Map("x" -> "[]", "ys" -> "[1, 2]")
          )
          assert(uServer.routes.isDefinedAt(badRequest))
          val InputError(
            upickle.Invalid.Data(upickle.Js.Arr(), "Number")
          ) = intercept[InputError] {
            uServer.routes(badRequest)
          }
        }
        'invalidJson{
          val badRequest = Request(
            Seq("autowire", "Api", "multiply"),
            Map("x" -> "[", "ys" -> "[1, 2]")
          )
          assert(uServer.routes.isDefinedAt(badRequest))
          val InputError(
            upickle.Invalid.Json(_, _)
          ) = intercept[InputError] {
            uServer.routes(badRequest)
          }
        }
      }
    }
  }
}
