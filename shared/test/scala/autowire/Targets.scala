package autowire

import scala.concurrent.Future

trait Api{
  def multiply(x: Double, ys: Seq[Double]): String
  def add(x: Int, y: Int = 1 + 1, z: Int = 10): String
  def sloww(s: Seq[String]): Future[Seq[Int]]
}

trait FakeApi{
  def omg(x: Int): Int
}

object Controller extends Api{
  def multiply(x: Double, ys: Seq[Double]): String = x + ys.map("*"+_).mkString
  def add(x: Int, y: Int = 1 + 1, z: Int = 10): String = s"$x+$y+$z"
  def sloww(s: Seq[String]): Future[Seq[Int]] = Future.successful(s.map(_.length))
  def subtract(x: Int, y: Int = 1 + 1): String = s"$x-$y"
}
