package autowire

import autowire.Core.Request
import ujson.ParseException
import upickle.core.AbortException
import upickle.default._
import utest.framework.ExecutionContext.RunNow
import utest._
import scala.concurrent.Future
import acyclic.file
import utest.PlatformShims.await

object UpickleTests extends TestSuite {

  object Bundle extends GenericClientServerBundle[String, upickle.default.Reader, upickle.default.Writer] {
    def write[T: upickle.default.Writer](t: T): String = upickle.default.write(t)
    def read[T: upickle.default.Reader](t: String): T = upickle.default.read[T](t)
    def routes: Bundle.Server.Router = Server.route[Api](Controller)
  }
  import Bundle.{Client, Server}

  val tests: Tests = utest.Tests {
    test("example") - {
      // shared API interface
      trait MyApi {
        def doThing(i: Int, s: String): Seq[String]
      }

      // server-side implementation, and router
      object MyApiImpl extends MyApi {
        def doThing(i: Int, s: String): Seq[String] = Seq.fill(i)(s)
      }
      object MyServer extends autowire.Server[String, upickle.default.Reader, upickle.default.Writer] {
        def write[Result: Writer](r: Result): String = upickle.default.write(r)
        def read[Result: Reader](p: String): Result = upickle.default.read[Result](p)

        val routes: MyServer.Router = MyServer.route[MyApi](MyApiImpl)
      }

      // client-side implementation, and call-site
      object MyClient extends autowire.Client[String, upickle.default.Reader, upickle.default.Writer] {
        def write[Result: Writer](r: Result): String = upickle.default.write(r)
        def read[Result: Reader](p: String): Result = upickle.default.read[Result](p)

        override def doCall(req: Request): Future[String] = {
          req ==> Request(
            List("autowire", "UpickleTests", "MyApi", "doThing"),
            Map("i" -> "3", "s" -> "\"lol\"")
          )
          MyServer.routes.apply(req)
        }
      }

      await(MyClient[MyApi].doThing(3, "lol").call()) ==> List("lol", "lol", "lol")
    }

    test("inheritedTraits") - {
      // It should also be possible to separate the API into several controllers that
      // only implement the logic of their corresponding protocols. The controllers are
      // combined using the Cake pattern.

      trait BookProtocol {
        // Allow dummy implementations in API to allow extending API from Client
        // without having to implement methods or being abstract
        // This reverses pull request #58 (https://github.com/lihaoyi/autowire/pull/58)
        def bookList(): Seq[String] = ???
      }

      trait ArticleProtocol {
        // Accessor method (without parenthesis)
        def articleList: Seq[String]
      }

      trait Protocol extends BookProtocol with ArticleProtocol

      trait BookController extends BookProtocol {
        override def bookList(): Seq[String] = Seq("Book")
      }

      trait ArticleController extends ArticleProtocol {
        def articleList: Seq[String] = Seq("Article")
      }

      object Controller extends Protocol
        with BookController
        with ArticleController

      object MyServer extends autowire.Server[String, upickle.default.Reader, upickle.default.Writer] {
        def write[Result: Writer](r: Result): String = upickle.default.write(r)
        def read[Result: Reader](p: String): Result = upickle.default.read[Result](p)

        val routes: MyServer.Router = MyServer.route[Protocol](Controller)
      }

      object MyClient extends autowire.Client[String, upickle.default.Reader, upickle.default.Writer] {
        def write[Result: Writer](r: Result): String = upickle.default.write(r)
        def read[Result: Reader](p: String): Result = upickle.default.read[Result](p)

        override def doCall(req: Request): Future[String] = {
          req.path.last match {
            case "bookList" => req ==> Request(
              List("autowire", "UpickleTests", "Protocol", "bookList"),
              Map()
            )
            case "articleList" => req ==> Request(
              List("autowire", "UpickleTests", "Protocol", "articleList"),
              Map()
            )
          }
          MyServer.routes.apply(req)
        }
      }

      await(MyClient[Protocol].bookList().call()) ==> Seq("Book")
      await(MyClient[Protocol].articleList.call()) ==> Seq("Article")
    }

    test("basicCalls") - {
      val res1 = await(Client[Api].add(1, 2, 3).call())
      val res2 = await(Client[Api].add(1).call())
      val res3 = await(Client[Api].add(1, 2).call())
      val res4 = await(Client[Api].multiply(x = 1.2, Seq(2.3)).call())
      val res5 = await(Client[Api].multiply(x = 1.1, ys = Seq(2.2, 3.3, 4.4)).call())
      //      val res6 = await(Client[Api].sum(Point(1, 2), Point(10, 20)).call())
      assert(
        res1 == "1+2+3",
        res2 == "1+2+10",
        res3 == "1+2+10",
        res4 == "1.2*2.3",
        res5 == "1.1*2.2*3.3*4.4"
        //        res6 == Point(11, 22)
      )
      Bundle.transmitted.last
    }

    test("aliased") - {
      val api = Client[Api]
      val res = await(api.add(1, 2, 4).call())
      assert(res == "1+2+4")
    }

    test("async") - {
      val res5 = await(Client[Api].sloww(Seq("omgomg", "wtf")).call())
      assert(res5 == Seq(6, 3))
    }

    test("compilationFailures") - {
      test - compileError("123.call()").check(
        """
          |      test - compileError("123.call()").check(
          |                           ^
        """.stripMargin,
        "You can't call the .call() method on 123"
      )
      test - compileError("Client[Api].add(1, 2, 3).toString.call()").check(
        """
          |      test - compileError("Client[Api].add(1, 2, 3).toString.call()").check(
          |                                                                 ^
        """.stripMargin,
        "You can't call the .call() method",
        "add(1, 2, 3).toString()"
      )
      test - compileError("Client[Api].fail1().call()").check(
        """
          |      test - compileError("Client[Api].fail1().call()").check(
          |                                       ^
        """.stripMargin,
        "value fail1 is not a member of autowire.ClientProxy"
      )
    }

    test("runtimeFailures") - {
      test("noSuchRoute") {
        val badRequest = Core.Request[String](Seq("omg", "wtf", "bbq"), Map.empty)
        assert(!Server.routes.isDefinedAt(badRequest))
        intercept[MatchError] {
          Server.routes(badRequest)
        }
      }

      test("inputError") - {
        def check(input: Map[String, String])(expectedError: PartialFunction[Throwable, Unit]): Unit = {
          val badRequest = Core.Request(
            Seq("autowire", "Api", "multiply"),
            input
          )
          assert(Server.routes.isDefinedAt(badRequest))
          val error = intercept[Error] {
            Server.routes(badRequest)
          }
          assert(expectedError.isDefinedAt(error))
        }

        test("keysMissing") - {
          test - check(Map.empty) {
            case Error.InvalidInput(
            Error.Param.Missing("x"),
            Error.Param.Missing("ys")
            ) =>
          }
          test - check(Map("x" -> "123")) {
            case Error.InvalidInput(Error.Param.Missing("ys")) =>
          }
          test - check(Map("ys" -> "[123]")) {
            case Error.InvalidInput(Error.Param.Missing("x")) =>
          }
        }

        test("keysInvalid") - {
          test - check(Map("x" -> "[]", "ys" -> "234")) {
            case Error.InvalidInput(
            Error.Param.Invalid("x", AbortException("expected number got sequence", _, _, _, _)),
            Error.Param.Invalid("ys", AbortException("expected sequence got number", _, _, _, _))
            ) =>
          }
          test - check(Map("x" -> "123", "ys" -> "234")) {
            case Error.InvalidInput(
            Error.Param.Invalid("ys", AbortException("expected sequence got number", _, _, _, _))
            ) =>
          }
          test - check(Map("x" -> "[]", "ys" -> "[234]")) {
            case Error.InvalidInput(
            Error.Param.Invalid("x", AbortException("expected number got sequence", _, _, _, _))
            ) =>
          }
        }

        test("invalidJson") - {
          test - check(Map("x" -> "]", "ys" -> "2}34")) {
            case Error.InvalidInput(
            Error.Param.Invalid("x", ParseException("expected json value got ] (line 1, column 1)", _)),
            Error.Param.Invalid("ys", AbortException("expected sequence got number", _, _, _, _))
            ) =>
          }
          test - check(Map("x" -> "1", "ys" -> "2}34")) {
            case Error.InvalidInput(
            Error.Param.Invalid("ys", AbortException("expected sequence got number", _, _, _, _))
            ) =>
          }
          test - check(Map("x" -> "]", "ys" -> "[234]")) {
            case Error.InvalidInput(
            Error.Param.Invalid("x", ParseException("expected json value got ] (line 1, column 1)", _))
            ) =>
          }
        }

        test("mix") - {
          test - check(Map("x" -> "]")) {
            case Error.InvalidInput(
            Error.Param.Invalid("x", ParseException("expected json value got ] (line 1, column 1)", _)),
            Error.Param.Missing("ys")
            ) =>
          }
          test - check(Map("x" -> "[1]", "ys" -> "2}34")) {
            case Error.InvalidInput(
            Error.Param.Invalid("x", AbortException("expected number got sequence", _, _, _, _)),
            Error.Param.Invalid("ys", AbortException("expected sequence got number", _, _, _, _))
            ) =>
          }
        }
      }
    }

    test("classImpl") - {
      // Make sure you can pass things other than `object`s (e.g. instances)
      // to the autowire router, and that it still works
      trait MyApi {
        def doThing(i: Int, s: String): Seq[String]
        def doThingTwo(i: Int, s: String = "A"): Seq[String]
      }

      class MyOtherApiImpl(meaningOfLife: Int) extends MyApi {
        def doThing(i: Int, s: String): Seq[String] = Seq.fill(i)(s + meaningOfLife.toString)
        def doThingTwo(i: Int, s: String): Seq[String] = Seq.fill(i)(s + meaningOfLife.toString)
      }

      object MyServer extends autowire.Server[String, upickle.default.Reader, upickle.default.Writer] {
        def write[Result: Writer](r: Result): String = upickle.default.write(r)
        def read[Result: Reader](p: String): Result = upickle.default.read[Result](p)

        val routes1: MyServer.Router = MyServer.route[MyApi](new MyOtherApiImpl(42))
        val anApi  : MyOtherApiImpl  = new MyOtherApiImpl(1)
        val routes2: MyServer.Router = MyServer.route[MyApi](anApi)

        def anApiDef(inp: Int) = new MyOtherApiImpl(inp)
        val routes3: MyServer.Router = MyServer.route[MyApi](anApiDef(2))
      }
      class UpickleClient(pf: PartialFunction[MyServer.Request, concurrent.Future[String]])
        extends autowire.Client[String, upickle.default.Reader, upickle.default.Writer] {
        def write[Result: Writer](r: Result): String = upickle.default.write(r)
        def read[Result: Reader](p: String): Result = upickle.default.read[Result](p)
        def doCall(req: Request): Future[String] = pf(req)
      }
      object Client1 extends UpickleClient(MyServer.routes1)

      object Client2 extends UpickleClient(MyServer.routes2)
      object Client3 extends UpickleClient(MyServer.routes3)

      val res1 = await(Client1[MyApi].doThingTwo(3).call())
      val res2 = await(Client1[MyApi].doThingTwo(3, "B").call())
      val res3 = await(Client2[MyApi].doThingTwo(2).call())
      val res4 = await(Client3[MyApi].doThingTwo(3, "C").call())
      assert(
        res1 == List("A42", "A42", "A42"),
        res2 == List("B42", "B42", "B42"),
        res3 == List("A1", "A1"),
        res4 == List("C2", "C2", "C2")
      )
    }
  }
}
