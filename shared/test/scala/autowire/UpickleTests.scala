package autowire
import utest._
import utest.ExecutionContext.RunNow
import upickle._
import acyclic.file


object UpickleTests extends TestSuite{
  object Bundle extends GenericClientServerBundle[String, upickle.Reader, upickle.Writer]{
    def write[T: upickle.Writer](t: T) = upickle.write(t)
    def read[T: upickle.Reader](t: String) = upickle.read[T](t)
    def routes = Server.route[Api](Controller)
  }
  import Bundle.{Client, Server}

  import utest.PlatformShims.await

  val tests = TestSuite{
    'example{
      import upickle._

      // shared API interface
      trait MyApi{
        def doThing(i: Int, s: String): Seq[String]
      }

      // server-side implementation, and router
      object MyApiImpl extends MyApi{
        def doThing(i: Int, s: String) = Seq.fill(i)(s)
      }
      object MyServer extends autowire.Server[String, upickle.Reader, upickle.Writer]{
        def write[Result: Writer](r: Result) = upickle.write(r)
        def read[Result: Reader](p: String) = upickle.read[Result](p)

        val routes = MyServer.route[MyApi](MyApiImpl)
      }

      // client-side implementation, and call-site
      object MyClient extends autowire.Client[String, upickle.Reader, upickle.Writer]{
        def write[Result: Writer](r: Result) = upickle.write(r)
        def read[Result: Reader](p: String) = upickle.read[Result](p)

        override def doCall(req: Request) = {
          println(req)
          MyServer.routes.apply(req)
        }
      }

      MyClient[MyApi].doThing(3, "lol").call().foreach(println)
    }
    'basicCalls{
      val res1 = await(Client[Api].add(1, 2, 3).call())
      val res2 = await(Client[Api].add(1).call())
      val res3 = await(Client[Api].add(1, 2).call())
      val res4 = await(Client[Api].multiply(x = 1.2, Seq(2.3)).call())
      val res5 = await(Client[Api].multiply(x = 1.1, ys = Seq(2.2, 3.3, 4.4)).call())
      val res6 = await(Client[Api].sum(Point(1, 2), Point(10, 20)).call())
      assert(
        res1 == "1+2+3",
        res2 == "1+2+10",
        res3 == "1+2+10",
        res4 == "1.2*2.3",
        res5 == "1.1*2.2*3.3*4.4",
        res6 == Point(11, 22)
      )
      Bundle.transmitted.last
    }
    'aliased{
      val api = Client[Api]
      val res = await(api.add(1, 2, 4).call())
      assert(res == "1+2+4")
    }
    'async{
      val res5 = await(Client[Api].sloww(Seq("omgomg", "wtf")).call())
      assert(res5 == Seq(6, 3))
    }
    'compilationFailures{

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
    'runtimeFailures{
      'noSuchRoute{
        val badRequest = Core.Request[String](Seq("omg", "wtf", "bbq"), Map.empty)
        assert(!Server.routes.isDefinedAt(badRequest))
        intercept[MatchError] {
          Server.routes(badRequest)
        }
      }
      'inputError{
        def check(input: Map[String, String])(expectedError: PartialFunction[Throwable, Unit]) = {
          val badRequest = Core.Request(
            Seq("autowire", "Api", "multiply"),
            input
          )
          assert(Server.routes.isDefinedAt(badRequest))
          val error = intercept[Error] { Server.routes(badRequest) }
          assert(expectedError.isDefinedAt(error))
        }

        'keysMissing {
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
        'keysInvalid - {
          * - check(Map("x" -> "[]", "ys" -> "234")) {
            case Error.InvalidInput(
              Error.Param.Invalid("x", upickle.Invalid.Data(Js.Arr(), _)),
              Error.Param.Invalid("ys", upickle.Invalid.Data(Js.Num(234), _))
            ) =>
          }
          * - check(Map("x" -> "123", "ys" -> "234")) {
            case Error.InvalidInput(
              Error.Param.Invalid("ys", upickle.Invalid.Data(Js.Num(234), _))
            ) =>
          }
          * - check(Map("x" -> "[]", "ys" -> "[234]")) {
            case Error.InvalidInput(
              Error.Param.Invalid("x", upickle.Invalid.Data(Js.Arr(), _))
            ) =>
          }
        }

        'invalidJson - {
          * - check(Map("x" -> "]", "ys" -> "2}34")) {
            case Error.InvalidInput(
              Error.Param.Invalid("x", upickle.Invalid.Json(_, "]")),
              Error.Param.Invalid("ys", upickle.Invalid.Json(_, "2}34"))
            ) =>
          }
          * - check(Map("x" -> "1", "ys" -> "2}34")) {
            case Error.InvalidInput(
              Error.Param.Invalid("ys", upickle.Invalid.Json(_, "2}34"))
            ) =>
          }
          * - check(Map("x" -> "]", "ys" -> "[234]")) {
            case Error.InvalidInput(
              Error.Param.Invalid("x", upickle.Invalid.Json(_, "]"))
            ) =>
          }
        }

        'mix - {
          * - check(Map("x" -> "]")) {
            case Error.InvalidInput(
              Error.Param.Invalid("x", upickle.Invalid.Json(_, "]")),
              Error.Param.Missing("ys")
            ) =>
          }
          * - check(Map("x" -> "[1]", "ys" -> "2}34")) {
            case Error.InvalidInput(
              Error.Param.Invalid("x", upickle.Invalid.Data(Js.Arr(Js.Num(1)), _)),
              Error.Param.Invalid("ys", upickle.Invalid.Json(_, "2}34"))
            ) =>
          }
        }
      }
    }
    'classImpl - {
      trait MyApi{
        def doThing(i: Int, s: String): Seq[String]
        def doThingTwo(i: Int, s: String = "aa"): Seq[String]
      }

      class MyOtherApiImpl(meaningOfLife: Int) extends MyApi{
        def doThing(i: Int, s: String) = Seq.fill(i)(s)
        def doThingTwo(i: Int, s: String) = Seq.fill(i)(s)
      }

      object MyServer extends autowire.Server[String, upickle.Reader, upickle.Writer]{
        def write[Result: Writer](r: Result) = upickle.write(r)
        def read[Result: Reader](p: String) = upickle.read[Result](p)

        val routes = MyServer.route[MyApi](new MyOtherApiImpl(42))
      }

      // client-side implementation, and call-site
      object MyClient extends autowire.Client[String, upickle.Reader, upickle.Writer]{
        def write[Result: Writer](r: Result) = upickle.write(r)
        def read[Result: Reader](p: String) = upickle.read[Result](p)

        override def doCall(req: Request) = {
          println(req)
          MyServer.routes.apply(req)
        }
      }

      MyClient[MyApi].doThingTwo(3).call().foreach(println)
    }
  }
}
