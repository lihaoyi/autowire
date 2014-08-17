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
        compileError("Client[Api].add(1, 2, 3).toString.call()"),
        """
        compileError("Client[Api].add(1, 2, 3).toString.call()"),
                                                            ^
        """,
        "You can't call the .call() method",
        "add(1, 2, 3).toString()"
      )

      * - check(
        compileError("Client[Api].fail1().call()"),
        """
        compileError("Client[Api].fail1().call()"),
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
        'keysMissing {
          def check(input: Map[String, String], expectedMissing: Seq[String]) = {
            val badRequest = Core.Request[String](Seq("autowire", "Api", "multiply"), input)
            assert(Server.routes.isDefinedAt(badRequest))
            val e @ Error.MissingParams(params) = intercept[Error] {
              Server.routes(badRequest)
            }
            assert(params == expectedMissing)
            e
          }
          * - check(Map.empty, Seq("x", "ys"))
          * - check(Map("x" -> "123"), Seq("ys"))
          * - check(Map("ys" -> "[123]"), Seq("x"))

        }
        'keysInvalid {
          val badRequest = Core.Request(
            Seq("autowire", "Api", "multiply"),
            Map("x" -> "[]", "ys" -> "[1, 2]")
          )
          assert(Server.routes.isDefinedAt(badRequest))
          val e @ Error.InvalidInput(
          upickle.Invalid.Data(upickle.Js.Arr(), "Number")
          ) = intercept[Error] {
            Server.routes(badRequest)
          }
          e
        }
        'invalidJson{
          val badRequest = Core.Request(
            Seq("autowire", "Api", "multiply"),
            Map("x" -> "[", "ys" -> "[1, 2]")
          )
          assert(Server.routes.isDefinedAt(badRequest))
          val e @ Error.InvalidInput(
            upickle.Invalid.Json(_, _)
          ) = intercept[Error] {
            Server.routes(badRequest)
          }
          e
        }
      }
    }
  }
}
