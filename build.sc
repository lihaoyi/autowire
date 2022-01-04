import mill._
import scalalib._
import scalajslib._
import scalanativelib._
import publish._
import mill.scalalib.api.Util.isScala3

val scala213 = "2.13.7"
val scala31 = "3.1.0"

val scalaJS06 = "0.6.33"
val scalaJS1  = "1.8.0"
val scalaNative = "0.4.0"

val crossVersions = Seq(scala213, scala31)

val scalaJSVersions = Seq(
  (scala213, scalaJS06),
  (scala213, scalaJS1),
  (scala31, scalaJS1)
)

val scalaNativeVersions = Seq(
  (scala213, scalaNative)
)

object autowire extends Module{
  object jvm extends Cross[autowireJvmModule](crossVersions:_*)
  class autowireJvmModule(val crossScalaVersion: String) extends AutowireModule{
    object test extends Tests with CommonTestModule
  }

  object js extends Cross[autowireJsModule](scalaJSVersions:_*)
  class autowireJsModule(val crossScalaVersion: String, val crossScalaJSVersion: String) extends AutowireModule with ScalaJSModule{
    def scalaJSVersion = "1.8.0"
    object test extends CommonTestModule with Tests
  }

  object native extends Cross[autowireNativeModule](scalaNativeVersions:_*)
  class autowireNativeModule(val crossScalaVersion: String) extends AutowireModule with ScalaNativeModule{
    def scalaNativeVersion = "0.4.0"
    object test extends CommonTestModule with Tests
  }
}

trait AutowireModule extends CrossScalaModule with PublishModule{
  def scalacPluginIvyDeps = Agg(ivy"com.lihaoyi:acyclic_2.13.0:0.3.0")
   def compileIvyDeps = if (!isScala3(crossScalaVersion)) Agg(
      ivy"com.lihaoyi:acyclic_2.13.0:0.3.0",
      ivy"org.scala-lang:scala-reflect:${crossScalaVersion}"
    )
    else Agg.empty[Dep]

  def artifactName = "autowire"
  def publishVersion = "0.3.4"
  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "com.lihaoyi",
    url = "https://github.com/lihaoyi/autowire",
    licenses = Seq(License.MIT),
    scm = SCM(
      "git://github.com/lihaoyi/autowire.git",
      "scm:git://github.com/lihaoyi/autowire.git"
    ),
    developers = Seq(
      Developer("lihaoyi", "Li Haoyi","https://github.com/lihaoyi")
    )
  )
  def scalacOptions = T{
    super.scalacOptions() ++ Seq("-deprecation", "-feature")
  }
  def sources = T.sources(
    millSourcePath / "src"
  )
  def millSourcePath = super.millSourcePath / os.up
}
trait CommonTestModule extends ScalaModule with TestModule{

  def ivyDeps = T{ super.ivyDeps() ++
    Agg(
      ivy"com.lihaoyi::utest::0.7.10",
      ivy"com.lihaoyi::upickle::1.4.3"
    )
  }
  def scalacPluginIvyDeps = Agg(ivy"com.lihaoyi:acyclic_2.13.0:0.3.0")
  def compileIvyDeps = T{
    if (!isScala3(scalaVersion())) Agg(
      ivy"com.lihaoyi::acyclic:0.2.0",
      ivy"org.scala-lang:scala-reflect:2.13.7"
    )
    else Agg.empty[Dep]
  }
  def testFrameworks = Seq("utest.runner.Framework")
}
