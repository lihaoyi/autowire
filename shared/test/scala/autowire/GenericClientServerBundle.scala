package autowire

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
  val transmitted = collection.mutable.Buffer.empty[PickleType]
  object Server extends autowire.Server[PickleType, Reader, Writer] {
    def write[T: Writer](t: T) = self.write(t)
    def read[T: Reader](t: PickleType) = self.read(t)
    val routes = self.routes
  }

  object Client extends autowire.Client[PickleType, Reader, Writer]{
    def write[T: Writer](t: T) = {
      val x = self.write(t)
      transmitted.append(x)
      x
    }
    def read[T: Reader](t: PickleType) = self.read(t)
    case class NoSuchRoute(msg: String) extends Exception(msg)

    def callRequest(r: Request) = {
      Server.routes
        .lift(r)
        .getOrElse(Future.failed(new NoSuchRoute("No route found : " + r.path)))
    }
  }
}