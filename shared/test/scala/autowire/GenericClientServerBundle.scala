package autowire

import scala.concurrent.Future

abstract class GenericClientServerBundle[PickleType, ReadBound[_], WriteBound[_]]{ self =>

  def write[T: WriteBound](t: T) : PickleType
  def read[T: ReadBound](s: PickleType): T
  def routes: Server.Router

  object Server extends autowire.Server[PickleType, ReadBound, WriteBound] {
    def write[T: WriteBound](t: T) = self.write(t)
    def read[T: ReadBound](t: PickleType) = self.read(t)
    val routes = self.routes
  }

  object Client extends autowire.Client[PickleType, ReadBound, WriteBound]{
    def write[T: WriteBound](t: T) = self.write(t)
    def read[T: ReadBound](t: PickleType) = self.read(t)
    case class NoSuchRoute(msg: String) extends Exception(msg)

    def callRequest(r: Request) = {
      Server.routes
        .lift(r)
        .getOrElse(Future.failed(new NoSuchRoute("No route found : " + r.path)))
    }
  }
}