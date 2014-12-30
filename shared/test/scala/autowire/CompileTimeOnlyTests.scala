package autowire

import scala.concurrent.Future
import autowire._
import scala.util.Properties
import utest._
import upickle._
object CompileTimeOnlyTests extends TestSuite{
  import scala.concurrent.ExecutionContext.Implicits.global
  // client-side implementation, and call-site
  object MyClient extends autowire.Client[String, upickle.Reader, upickle.Writer]{
    def write[Result: Writer](r: Result) = upickle.write(r)
    def read[Result: Reader](p: String) = upickle.read[Result](p)
    override def doCall(req: Request) = {
      Future(req.args("x"))
    }
  }

  trait MyApi {
    def id(x: Int) : Int
  }

 val tests = TestSuite{
   'compileTimeOnly {
     MyClient[MyApi].id(10)
//     val x = compileError("MyClient[MyApi].id(10)")

//     val x = compileError("import autowire._; autowire.CompileTimeOnlyTests.MyClient[autowire.CompileTimeOnlyTests.MyApi].id(5)")
//     println("AAAA"+x)

   }
 }
}