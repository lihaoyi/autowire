package autowire
import acyclic.file
import scala.concurrent.Future

trait Api{
  def multiply(x: Double, ys: Seq[Double]): String
  def add(x: Int, y: Int = 1 + 1, z: Int = 10): String
  def sloww(s: Seq[String]): Future[Seq[Int]]
  def sum(p1: Point, p2: Point): Point
}

trait FakeApi{
  def omg(x: Int): Int
}

object Controller extends Api{
  def multiply(x: Double, ys: Seq[Double]): String = x + ys.map("*"+_).mkString
  def add(x: Int, y: Int = 1 + 1, z: Int = 10): String = s"$x+$y+$z"
  def sloww(s: Seq[String]): Future[Seq[Int]] = Future.successful(s.map(_.length))
  def subtract(x: Int, y: Int = 1 + 1): String = s"$x-$y"
  def sum(p1: Point, p2: Point): Point = Point(p1.x + p2.x, p1.y + p2.y)
}

case class Point(x: Int, y: Int)
