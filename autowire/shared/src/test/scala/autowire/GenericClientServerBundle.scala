package autowire
import scala.collection.mutable
import scala.concurrent.Future

/**
* A generic client-server implementation, which does everything locally but
* nonetheless exercises the whole macro-based pickling/unpickling round trip.
* Re-used for all the different implementations of pickling/unpickling, to
* make sure it works for all of them
*/
abstract class GenericClientServerBundle[PickleType, Reader[_], Writer[_]]{ self =>

  def write[T: Writer](t: T) : PickleType
  def read[T: Reader](s: PickleType): T
  def routes: Server.Router
  val transmitted: mutable.Buffer[PickleType] = collection.mutable.Buffer.empty[PickleType]
  object Server extends autowire.Server[PickleType, Reader, Writer] {
    def write[T: Writer](t: T): PickleType = self.write(t)
    def read[T: Reader](t: PickleType): T = self.read(t)
    val routes: Server.Router = self.routes
  }

  object Client extends autowire.Client[PickleType, Reader, Writer]{
    def write[T: Writer](t: T): PickleType = {
      val x = self.write(t)
      transmitted.append(x)
      x
    }
    def read[T: Reader](t: PickleType): T = self.read(t)
    case class NoSuchRoute(msg: String) extends Exception(msg)

    def doCall(r: Request): Future[PickleType] = {
      Server.routes
        .lift(r)
        .getOrElse(Future.failed(NoSuchRoute("No route found : " + r.path)))
    }
  }
}