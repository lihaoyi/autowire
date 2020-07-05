package autowire

import upickle.default._
import utest._
import scala.concurrent.Future

object CompileTimeOnlyTests extends TestSuite {

  import scala.concurrent.ExecutionContext.Implicits.global

  // client-side implementation, and call-site
  object MyClient extends autowire.Client[String, upickle.default.Reader, upickle.default.Writer] {
    def write[Result: Writer](r: Result): String = upickle.default.write(r)
    def read[Result: Reader](p: String): Result = upickle.default.read[Result](p)
    override def doCall(req: Request): Future[String] = {
      Future(req.args("x"))
    }
  }

  trait MyApi {
    def id(x: Int): Int
  }

  val tests: Tests = utest.Tests {
    test("compileTimeOnly") {

      //uncomment this out to verify compile error when call is not present
      //MyClient[MyApi].id(10)

      //TODO Re-eanable when compileError runs late enough in the compilation process to catch this
      MyClient[MyApi]
      //    assert(x.msg.contains("You have forgotten to append .call() to the end of an autowire call."))
    }
  }
}
