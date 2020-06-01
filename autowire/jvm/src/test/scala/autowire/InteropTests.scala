package autowire

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}
import java.nio.ByteBuffer
import acyclic.file
import org.objenesis.strategy.StdInstantiatorStrategy
import utest.PlatformShims.await
import utest._
import utest.framework.ExecutionContext.RunNow
import scala.reflect.ClassTag

object InteropTests extends TestSuite {


  val tests: Tests = utest.Tests {

    test("default") {
      import upickle.default._
      implicit val pointReadWrite: ReadWriter[Point] = macroRW
      object Bundle extends GenericClientServerBundle[String, Reader, Writer] {
        def write[T: Writer](t: T): String = upickle.default.write(t)
        def read[T: Reader](t: String): T = upickle.default.read[T](t)
        def routes: Server.Router = Server.route[Api](Controller)
      }
      import Bundle.Client

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

    test("reflection") {
      object Bundle extends GenericClientServerBundle[Array[Byte], Bounds.None, Bounds.None] {
        def write[T: Bounds.None](t: T): Array[Byte] = {
          val buffer = new ByteArrayOutputStream()
          val oos    = new ObjectOutputStream(buffer)
          oos.writeObject(t)
          oos.flush()
          oos.close()
          buffer.toByteArray
        }
        def read[T: Bounds.None](s: Array[Byte]): T = {
          val in  = new ByteArrayInputStream(s)
          val ois = new ObjectInputStream(in)
          val obj = ois.readObject()
          obj.asInstanceOf[T]
        }
        def routes: Bundle.Server.Router = Server.route[Api](Controller)
      }
      import Bundle.Client

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

    test("kryo") {
      object Bundle extends GenericClientServerBundle[Array[Byte], ClassTag, ClassTag] {
        val kryo = new com.esotericsoftware.kryo.Kryo()
        kryo.setRegistrationRequired(false)
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy())
        kryo.register(classOf[scala.collection.immutable.::[_]], 60)
        def write[T: ClassTag](t: T): Array[Byte] = {
          val output = new com.esotericsoftware.kryo.io.Output(new ByteArrayOutputStream())
          kryo.writeClassAndObject(output, t)
          output.toBytes
        }
        def read[T: ClassTag](s: Array[Byte]): T = {
          val input = new com.esotericsoftware.kryo.io.Input(new ByteArrayInputStream(s))
          val res   = kryo.readClassAndObject(input).asInstanceOf[T]
          res
        }
        def routes: Bundle.Server.Router = Server.route[Api](Controller)
      }
      import Bundle.Client

      val res1 = await(Client[Api].add(1, 2, 3).call())
      val res2 = await(Client[Api].add(1).call())
      val res3 = await(Client[Api].add(1, 2).call())

      // todo: Seq[Double] to Array[Byte] conversion seems to be broken (kryo problem?)
      //      val res4 = await(Client[Api].multiply(x = 1.2, Seq(2.3)).call())
      //      val res5 = await(Client[Api].multiply(x = 1.1, ys = Seq(2.2, 3.3, 4.4)).call())
      //      val res6 = await(Client[Api].sum(Point(1, 2), Point(10, 20)).call())
      assert(
        res1 == "1+2+3",
        res2 == "1+2+10",
        res3 == "1+2+10",
        //        res4 == "1.2*2.3",
        //        res5 == "1.1*2.2*3.3*4.4",
        //        res6 == Point(11, 22)
      )
      Bundle.transmitted.last
    }

    test("playJson") {
      import play.api.libs.json._
      implicit val pointReads  = Json.reads[Point]
      implicit val pointWrites = Json.writes[Point]
      object Bundle extends GenericClientServerBundle[String, Reads, Writes] {
        def write[T: Writes](t: T): String = Json.stringify(Json.toJson(t))
        def read[T: Reads](s: String): T = Json.fromJson[T](Json.parse(s)).get
        def routes: Bundle.Server.Router = Server.route[Api](Controller)
      }
      import Bundle.Client

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

    test("booPickle") {
      import boopickle.Default._
      object Bundle extends GenericClientServerBundle[ByteBuffer, Pickler, Pickler] {
        def write[Result: Pickler](r: Result): ByteBuffer = Pickle.intoBytes(r)
        def read[Result: Pickler](p: ByteBuffer): Result = Unpickle.apply[Result].fromBytes(p)
        def routes: Bundle.Server.Router = Server.route[Api](Controller)
      }
      import Bundle.Client

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
  }
}
