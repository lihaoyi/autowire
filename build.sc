
import mill._
import mill.scalalib._
import mill.scalalib.publish._
import mill.scalajslib._

import mill.api.Loose
import mill.scalajslib.api._  


val scalaJsMatrix = List(
  // commented out versions due to missing upickle version / scalajs-tools issue
  ("2.12.9", "0.6.28"), //("2.12.9", "1.0.0-M8"), 
  ("2.13.0", "0.6.28"), //("2.13.0", "1.0.0-M8")
)
val scalaVersions = scalaJsMatrix.map(_._1)

trait Common extends ScalaModule with CrossScalaModule {
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    ivy"com.lihaoyi::acyclic:0.2.0",
    ivy"org.scala-lang:scala-reflect:$scalaVersion"
  )
}

trait CommonPublishModule extends Common with PublishModule {
  def publishVersion = "0.3.0"
  def artifactName = "autowire"
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
}

object autowire extends Module {
  def scalaRelease(version: String) = version.split('.').take(2).mkString(".")
  val utestDepJs = Map("0.6.28" -> "0.7.1", "1.0.0-M8" ->  "0.6.8") 

  object jvm extends Cross[CrossJvm](scalaVersions: _*)
  class CrossJvm(val crossScalaVersion: String)  extends CommonPublishModule {
    def millSourcePath = autowire.millSourcePath
    def scalaVersion = crossScalaVersion

    object test extends Tests{
      def testFrameworks = Seq("utest.runner.Framework")
      def ivyDeps = super.ivyDeps() ++ Agg(
        ivy"com.lihaoyi::acyclic:0.2.0",
        ivy"com.lihaoyi::upickle:0.7.5",
        ivy"com.lihaoyi::utest:0.7.1",
      )
    }
  }

  object js extends Cross[CrossJs](scalaJsMatrix: _*)  
  class CrossJs(val crossScalaVersion: String, scalaJsCrossVersion: String) extends CommonPublishModule with ScalaJSModule {
    def millSourcePath = autowire.millSourcePath
    def scalaVersion = crossScalaVersion
    def scalaJSVersion = scalaJsCrossVersion

    object test extends Tests {
      def testFrameworks = Seq("utest.runner.Framework")
      def ivyDeps = Agg(
        ivy"com.lihaoyi::acyclic:0.2.0",
        ivy"com.lihaoyi::upickle::0.7.5",
        ivy"com.lihaoyi::utest::${utestDepJs(scalaJsCrossVersion)}",
      )
    }
  }
}
