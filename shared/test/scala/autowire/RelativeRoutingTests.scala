package autowire

import java.io.File
import utest._
import utest.ExecutionContext.RunNow
import upickle._
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


      trait UPickleSerializers extends Serializers[String, upickle.Reader, upickle.Writer] {
        override def write[Result: Writer](r: Result) = upickle.write(r)
        override def read[Result: Reader](p: String) = upickle.read[Result](p)
      }

      object UPickleServer extends autowire.Server[String, upickle.Reader, upickle.Writer] with UPickleSerializers
      val x = new MyApiImpl("aa")
      val fullRouter = UPickleServer.route[MagicalApi](x)
      val innerRouter = UPickleServer.innerRoute[MagicalApi](x)

      // client-side implementation, and call-site
      object MyClient extends autowire.Client[String, upickle.Reader, upickle.Writer] with UPickleSerializers {
        override def doCall(req: Request) = {
          fullRouter(req)
        }
      }

      // client-side implementation, and call-site
      object MyClientIn extends autowire.Client[String, upickle.Reader, upickle.Writer] with UPickleSerializers {
        override def doCall(req: Request) = {
          innerRouter(req.copy(outerPath = Seq("YOU SHALL NOT PATH!", "AND MY AXE!")))
        }
      }

      def verifyClient(client : Client[String, Reader, Writer]) {
        val a = await(client[MagicalApi].shuffle(3, "lol").call())
        val b = await(client[MagicalApi].table.getHand("Ben").call())
        assert("3:lol:Foo" == a)
        assert(goodHand == b)
      }

      verifyClient(MyClient)
      verifyClient(MyClientIn)

    }
  }
}