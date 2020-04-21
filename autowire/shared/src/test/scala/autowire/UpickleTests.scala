package autowire
import utest._
import utest.framework._
import utest.framework.ExecutionContext.RunNow
import ujson.Value._
import upickle.default._
import acyclic.file


object UpickleTests extends TestSuite{
  object Bundle extends GenericClientServerBundle[String, upickle.default.Reader, upickle.default.Writer]{
    def write[T: upickle.default.Writer](t: T) = upickle.default.write(t)
    def read[T: upickle.default.Reader](t: String) = upickle.default.read[T](t)
    def routes = Server.route[Api](Controller)
  }
  import Bundle.{Client, Server}

  import utest.PlatformShims.await

  val tests = utest.Tests{
    test("example"){
      import upickle._

      // shared API interface
      trait MyApi{
        def doThing(i: Int, s: String): Seq[String]
      }

      // server-side implementation, and router
      object MyApiImpl extends MyApi{
        def doThing(i: Int, s: String) = Seq.fill(i)(s)
      }
      object MyServer extends autowire.Server[String, upickle.default.Reader, upickle.default.Writer]{
        def write[Result: Writer](r: Result) = upickle.default.write(r)
        def read[Result: Reader](p: String) = upickle.default.read[Result](p)

        val routes = MyServer.route[MyApi](MyApiImpl)
      }

      // client-side implementation, and call-site
      object MyClient extends autowire.Client[String, upickle.default.Reader, upickle.default.Writer]{
        def write[Result: Writer](r: Result) = upickle.default.write(r)
        def read[Result: Reader](p: String) = upickle.default.read[Result](p)

        override def doCall(req: Request) = {
          println(req)
          MyServer.routes.apply(req)
        }
      }

      MyClient[MyApi].doThing(3, "lol").call().foreach(println)
    }
    test("inheritedTraits"){
      import upickle._

      // It should also be possible to separate the API into several controllers that
      // only implement the logic of their corresponding protocols. The controllers are
      // combined using the Cake pattern.

      trait BookProtocol {
        def bookList(): Seq[String]
      }

      trait ArticleProtocol {
        def articleList(): Seq[String]
      }

      trait Protocol extends BookProtocol with ArticleProtocol

      trait BookController extends BookProtocol {
        def bookList(): Seq[String] = Nil
      }

      trait ArticleController extends ArticleProtocol {
        def articleList(): Seq[String] = Nil
      }

      object Controller extends Protocol
        with BookController
        with ArticleController

      object MyServer extends autowire.Server[String, upickle.default.Reader, upickle.default.Writer]{
        def write[Result: Writer](r: Result) = upickle.default.write(r)
        def read[Result: Reader](p: String) = upickle.default.read[Result](p)

        val routes = MyServer.route[Protocol](Controller)
      }

      object MyClient extends autowire.Client[String, upickle.default.Reader, upickle.default.Writer]{
        def write[Result: Writer](r: Result) = upickle.default.write(r)
        def read[Result: Reader](p: String) = upickle.default.read[Result](p)

        override def doCall(req: Request) = {
          println(req)
          MyServer.routes.apply(req)
        }
      }

      MyClient[Protocol].bookList().call().foreach(println)
    }
    test("basicCalls"){
      val res1 = await(Client[Api].add(1, 2, 3).call())
      val res2 = await(Client[Api].add(1).call())
      val res3 = await(Client[Api].add(1, 2).call())
      val res4 = await(Client[Api].multiply(x = 1.2, Seq(2.3)).call())
      val res5 = await(Client[Api].multiply(x = 1.1, ys = Seq(2.2, 3.3, 4.4)).call())
   //   val res6 = await(Client[Api].sum(Point(1, 2), Point(10, 20)).call())
      assert(
        res1 == "1+2+3",
        res2 == "1+2+10",
        res3 == "1+2+10",
        res4 == "1.2*2.3",
        res5 == "1.1*2.2*3.3*4.4"
     //   res6 == Point(11, 22)
      )
      Bundle.transmitted.last
    }
    test("aliased"){
      val api = Client[Api]
      val res = await(api.add(1, 2, 4).call())
      assert(res == "1+2+4")
    }
    test("async"){
      val res5 = await(Client[Api].sloww(Seq("omgomg", "wtf")).call())
      assert(res5 == Seq(6, 3))
    }
    test("compilationFailures"){

      * - compileError("123.call()").check(
        """
      * - compileError("123.call()").check(
                        ^
        """,
        "You can't call the .call() method on 123"
      )

      * - compileError("Client[Api].add(1, 2, 3).toString.call()").check(
        """
      * - compileError("Client[Api].add(1, 2, 3).toString.call()").check(
                                                              ^
        """,
        "You can't call the .call() method",
        "add(1, 2, 3).toString()"
      )

      * - compileError("Client[Api].fail1().call()").check(
        """
      * - compileError("Client[Api].fail1().call()").check(
                                    ^
        """.stripMargin,
        "value fail1 is not a member of autowire.ClientProxy"
      )
    }
    test("runtimeFailures"){
      test("noSuchRoute"){
        val badRequest = Core.Request[String](Seq("omg", "wtf", "bbq"), Map.empty)
        assert(!Server.routes.isDefinedAt(badRequest))
        intercept[MatchError] {
          Server.routes(badRequest)
        }
      }
      test("inputError"){
        def check(input: Map[String, String])(expectedError: PartialFunction[Throwable, Unit]) = {
          val badRequest = Core.Request(
            Seq("autowire", "Api", "multiply"),
            input
          )
          assert(Server.routes.isDefinedAt(badRequest))
          val error = intercept[Error] { Server.routes(badRequest) }
          assert(expectedError.isDefinedAt(error))
        }

        test("keysMissing") {
          * - check(Map.empty){
            case Error.InvalidInput(
              Error.Param.Missing("x"),
              Error.Param.Missing("ys")
            ) =>
          }
          * - check(Map("x" -> "123")){
            case Error.InvalidInput(Error.Param.Missing("ys")) =>
          }
          * - check(Map("ys" -> "[123]")){
            case Error.InvalidInput(Error.Param.Missing("x")) =>
          }

        }
        test("keysInvalid") - {
          * - check(Map("x" -> "[]", "ys" -> "234")) {
            case Error.InvalidInput(
              Error.Param.Invalid("x", InvalidData(Arr(Seq()), _)),
              Error.Param.Invalid("ys", InvalidData(Num(234), _))
            ) =>
          }
          * - check(Map("x" -> "123", "ys" -> "234")) {
            case Error.InvalidInput(
              Error.Param.Invalid("ys", InvalidData(Num(234), _))
            ) =>
          }
          * - check(Map("x" -> "[]", "ys" -> "[234]")) {
            case Error.InvalidInput(
              Error.Param.Invalid("x", InvalidData(Arr(Seq()), _))
            ) =>
          }
        }

        test("invalidJson") - {
          * - check(Map("x" -> "]", "ys" -> "2}34")) {
            case Error.InvalidInput(
              Error.Param.Invalid("x", InvalidData(_, "]")),
              Error.Param.Invalid("ys", InvalidData(_, "2}34"))
            ) =>
          }
          * - check(Map("x" -> "1", "ys" -> "2}34")) {
            case Error.InvalidInput(
              Error.Param.Invalid("ys", InvalidData(_, "2}34"))
            ) =>
          }
          * - check(Map("x" -> "]", "ys" -> "[234]")) {
            case Error.InvalidInput(
              Error.Param.Invalid("x", InvalidData(_, "]"))
            ) =>
          }
        }

        test("mix") - {
          * - check(Map("x" -> "]")) {
            case Error.InvalidInput(
              Error.Param.Invalid("x", InvalidData(_, "]")),
              Error.Param.Missing("ys")
            ) =>
          }
          * - check(Map("x" -> "[1]", "ys" -> "2}34")) {
            case Error.InvalidInput(
              Error.Param.Invalid("x", InvalidData(Arr(Seq(Num(1))), _)),
              Error.Param.Invalid("ys", InvalidData(_, "2}34"))
            ) =>
          }
        }
      }
    }
    test("classImpl") - {
      // Make sure you can pass things other than `object`s (e.g. instances)
      // to the autowire router, and that it still works
      trait MyApi{
        def doThing(i: Int, s: String): Seq[String]
        def doThingTwo(i: Int, s: String = "A"): Seq[String]
      }

      class MyOtherApiImpl(meaningOfLife: Int) extends MyApi{
        def doThing(i: Int, s: String) = Seq.fill(i)(s+meaningOfLife.toString)
        def doThingTwo(i: Int, s: String) = Seq.fill(i)(s+meaningOfLife.toString)
      }

      object MyServer extends autowire.Server[String, upickle.default.Reader, upickle.default.Writer]{
        def write[Result: Writer](r: Result) = upickle.default.write(r)
        def read[Result: Reader](p: String) = upickle.default.read[Result](p)

        val routes1 = MyServer.route[MyApi](new MyOtherApiImpl(42))

        val anApi = new MyOtherApiImpl(1)
        val routes2 = MyServer.route[MyApi](anApi)

        def anApiDef(inp: Int) = new MyOtherApiImpl(inp)
        val routes3 = MyServer.route[MyApi](anApiDef(2))
      }
      class UpickleClient(pf: PartialFunction[MyServer.Request, concurrent.Future[String]]) extends autowire.Client[String, upickle.default.Reader, upickle.default.Writer]{
        def write[Result: Writer](r: Result) = upickle.default.write(r)
        def read[Result: Reader](p: String) = upickle.default.read[Result](p)
        def doCall(req: Request) = pf(req)
      }
      object Client1 extends UpickleClient(MyServer.routes1)

      object Client2 extends UpickleClient(MyServer.routes2)
      object Client3 extends UpickleClient(MyServer.routes3)

      val res1 = await(Client1[MyApi].doThingTwo(3).call())
      val res2 = await(Client1[MyApi].doThingTwo(3,"B").call())
      val res3 = await(Client2[MyApi].doThingTwo(2).call())
      val res4 = await(Client3[MyApi].doThingTwo(3,"C").call())
      assert(
        res1 == List("A42","A42","A42"),
        res2 == List("B42","B42","B42"),
        res3 == List("A1","A1"),
        res4 == List("C2","C2","C2")
      )
    }
  }
}
