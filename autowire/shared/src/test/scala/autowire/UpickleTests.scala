package autowire
import utest._
import utest.ExecutionContext.RunNow
import upickle._
import acyclic.file

object UpickleTests extends TestSuite{

  abstract class UpickleBundle
    extends GenericClientServerBundle[String, Reader, Writer] {
    override def write[Result: Writer](r: Result) = upickle.write(r)
    override def read[Result: Reader](p: String) = upickle.read[Result](p)
  }

  object ApiBundle extends UpickleBundle{
    override def routes = Server.route[Api](Controller)
  }
  import utest.PlatformShims.await

  val tests = TestSuite{
    'example{
      // shared API interface
      trait MyApi{
        def doThing(i: Int, s: String): Seq[String]
      }

      // server-side implementation, and router
      object MyApiImpl extends MyApi{
        def doThing(i: Int, s: String) = Seq.fill(i)(s)
      }
      object Bundle extends UpickleBundle{
        override val routes = Server.route[MyApi](MyApiImpl)
      }

      Bundle.Client[MyApi].doThing(3, "lol").call().foreach(println)
    }
    'inheritedTraits{
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

      object Bundle extends UpickleBundle{
        override val routes = Server.route[Protocol](Controller)
      }

      Bundle.Client[Protocol].bookList().call().foreach(println)
    }
    'basicCalls{
      val res1 = await(ApiBundle.Client[Api].add(1, 2, 3).call())
      val res2 = await(ApiBundle.Client[Api].add(1).call())
      val res3 = await(ApiBundle.Client[Api].add(1, 2).call())
      val res4 = await(ApiBundle.Client[Api].multiply(x = 1.2, Seq(2.3)).call())
      val res5 = await(ApiBundle.Client[Api].multiply(x = 1.1, ys = Seq(2.2, 3.3, 4.4)).call())
      val res6 = await(ApiBundle.Client[Api].sum(Point(1, 2), Point(10, 20)).call())
      assert(
        res1 == "1+2+3",
        res2 == "1+2+10",
        res3 == "1+2+10",
        res4 == "1.2*2.3",
        res5 == "1.1*2.2*3.3*4.4",
        res6 == Point(11, 22)
      )
      ApiBundle.transmitted.last
    }
    'aliased{
      val res = await(ApiBundle.Client[Api].add(1, 2, 4).call())
      assert(res == "1+2+4")
    }
    'async{
      val res5 = await(ApiBundle.Client[Api].sloww(Seq("omgomg", "wtf")).call())
      assert(res5 == Seq(6, 3))
    }
    'compilationFailures{
      import ApiBundle.Client

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
        assert(!ApiBundle.Server.routes.isDefinedAt(badRequest))
        intercept[MatchError] {
          ApiBundle.Server.routes(badRequest)
        }
      }
      'inputError{
        def check(input: Map[String, String])(expectedError: PartialFunction[Throwable, Unit]) = {
          val badRequest = Core.Request(
            Seq("autowire", "Api", "multiply"),
            input
          )
          assert(ApiBundle.Server.routes.isDefinedAt(badRequest))
          val error = intercept[Error] { ApiBundle.Server.routes(badRequest) }
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

      object Bundle1 extends UpickleBundle{
        override val routes = Server.route[MyApi](new MyOtherApiImpl(42))
      }
      object Bundle2 extends UpickleBundle{
        val anApi = new MyOtherApiImpl(1)
        override val routes = Server.route[MyApi](anApi)
      }
      object Bundle3 extends UpickleBundle{
        def anApiDef(inp: Int) = new MyOtherApiImpl(inp)
        override val routes = Server.route[MyApi](anApiDef(2))
      }

      val res1 = await(Bundle1.Client[MyApi].doThingTwo(3).call())
      val res2 = await(Bundle1.Client[MyApi].doThingTwo(3,"B").call())
      val res3 = await(Bundle2.Client[MyApi].doThingTwo(2).call())
      val res4 = await(Bundle3.Client[MyApi].doThingTwo(3,"C").call())
      assert(
        res1 == List("A42","A42","A42"),
        res2 == List("B42","B42","B42"),
        res3 == List("A1","A1"),
        res4 == List("C2","C2","C2")
      )
    }
    'accessors {
      trait T1 {
        def g: List[Int]
      }
      trait T2 {
        def g: Unit //works with emptyparens, included it for the different compile error
      }
      trait T3 {
        val g: List[String]
      }

      object O1 extends T1 {
        override def g: List[Int] = Nil
      }
      object O2 extends T2 {
        override def g = ()
      }
      object O3 extends T3 {
        override val g: List[String] = "1" :: Nil
      }

      object Bundle extends UpickleBundle {
        //Error:(268, 47) not enough arguments for method apply: (n: Int)Int in trait LinearSeqOptimized.
        // Unspecified value parameter n.
        val r1 = Server.route[T1](O1)
        // Error: Unit does not take parameters
        val r2 = Server.route[T2](O2)
        //Error: diverging implicit expansion for type upickle.Writer[List[Nothing]]
        // starting with method SeqishW in trait Implicits
        //I included it because it's looking for List[Nothing], which is weird
        val r3 = Server.route[T3](O3)

        override val routes = r1 orElse r2 orElse r3
      }

      assert(
        await(Bundle.Client[T1].g.call()) == Nil,
        await(Bundle.Client[T2].g.call()) == (),
        await(Bundle.Client[T3].g.call()) == "1" :: Nil
      )
    }
  }
}
