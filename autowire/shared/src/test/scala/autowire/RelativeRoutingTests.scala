package autowire

import java.io.File
import utest._
import utest.framework._
import utest.framework.ExecutionContext.RunNow
import upickle.default._
import acyclic.file


object RelativeRoutingTests extends TestSuite {
 import utest.PlatformShims.await

  val goodHand = Seq("Black Lotus", "Mountain", "Fireball", "Channel")

  val tests = TestSuite {
    'example {
      import upickle._

      // shared API interface
      trait MagicalApi {
        def resetGame(): Seq[String]
        def shuffle(x: Int, s: String, d : String = "Foo"): String
        val table: TableApi
        var avartable: TableApi = _
      }

      trait TableApi {
        def getHand(playerName: String): Seq[String]
      }
      // server-side implementation, and router
      class MyApiImpl(c: String) extends MagicalApi {
        override def resetGame(): Seq[String] = ???
        override val table: TableApi = new TableApi {
          override def getHand(playerId: String): Seq[String] = {
            if (playerId == "Ben") {
              goodHand
            } else {
              Nil
            }
          }
        }
        override def shuffle(x: Int, s: String, d: String = "Foo"): String = x + ":" + s + ":" + d
      }


      trait UPickleSerializers extends Serializers[String, upickle.default.Reader, upickle.default.Writer] {
        override def write[Result: Writer](r: Result) = upickle.default.write(r)
        override def read[Result: Reader](p: String) = upickle.default.read[Result](p)
      }

      object UPickleServer extends autowire.Server[String, upickle.default.Reader, upickle.default.Writer] with UPickleSerializers
      val x = new MyApiImpl("aa")
      val router = UPickleServer.route[MagicalApi](x)

      // client-side implementation, and call-site
      object MyClient extends autowire.Client[String, upickle.default.Reader, upickle.default.Writer] with UPickleSerializers {
        override def doCall(req: Request) = {
          router(req)
        }
      }

      val client = MyClient[MagicalApi]

      val a = await(client.shuffle(3, "lol").call())
      val b = await(client.table.getHand("Ben").call())

      assert("3:lol:Foo" == a)
      assert(goodHand == b)
    }
  }
}
