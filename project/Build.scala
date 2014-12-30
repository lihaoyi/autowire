import sbt._
import Keys._
import scala.scalajs.sbtplugin.ScalaJSPlugin._

import scala.scalajs.sbtplugin.ScalaJSPlugin.ScalaJSKeys._

object Build extends sbt.Build{
  val cross = new utest.jsrunner.JsCrossBuild(
    organization := "com.lihaoyi",

    version := "0.2.3",
    name := "autowire",
    scalaVersion := "2.10.4",
    autoCompilerPlugins := true,
    addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.2"),
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "acyclic" % "0.1.2" % "provided",
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    ) ++ (
      if (scalaVersion.value startsWith "2.11.") Nil
      else Seq(
        compilerPlugin("org.scalamacros" % s"paradise" % "2.0.0" cross CrossVersion.full),
        "org.scalamacros" %% s"quasiquotes" % "2.0.0"
      )
    ),
    // Sonatype
    publishArtifact in Test := false,
    publishTo := Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),

    pomExtra :=
      <url>https://github.com/lihaoyi/ajax</url>
      <licenses>
        <license>
          <name>MIT license</name>
          <url>http://www.opensource.org/licenses/mit-license.php</url>
        </license>
      </licenses>
      <scm>
        <url>git://github.com/lihaoyi/ajax.git</url>
        <connection>scm:git://github.com/lihaoyi/ajax.git</connection>
      </scm>
      <developers>
        <developer>
          <id>lihaoyi</id>
          <name>Li Haoyi</name>
          <url>https://github.com/lihaoyi</url>
        </developer>
      </developers>
  )

  lazy val root = cross.root

  lazy val js = cross.js.settings(
    resolvers ++= Seq(
      "bintray-alexander_myltsev" at "http://dl.bintray.com/content/alexander-myltsev/maven"
    ),
    libraryDependencies ++= Seq(
      "com.lihaoyi" %%% "upickle" % "0.2.5" % "test"
    ),
    requiresDOM := false,
    sourceGenerators in Compile += generateCompileTimeOnlyAnnotationTask.taskValue,
    sourceGenerators in Test += testCompileTimeOnlyAnnotation.taskValue
  )

  lazy val jvm = cross.jvm.settings(
    resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "upickle" % "0.2.5" % "test",
      "org.scala-lang" %% "scala-pickling" % "0.9.0" % "test",
      "com.esotericsoftware.kryo" % "kryo" % "2.24.0" % "test",
      "com.typesafe.play" %% "play-json" % "2.3.0" % "test"
    ),
    sourceGenerators in Compile += generateCompileTimeOnlyAnnotationTask.taskValue,
    sourceGenerators in Test += testCompileTimeOnlyAnnotation.taskValue
  )


  lazy val generateCompileTimeOnlyAnnotationTask = Def.task {
    if (scalaVersion.value.startsWith("2.10")) {
      val file = (sourceManaged in Compile).value / "scala" / "annotation" / "compileTimeOnly.scala"
      IO.write(file,
        """
          |package scala.annotation
          |import scala.annotation.meta._
          |@getter @setter @beanGetter @beanSetter @companionClass @companionMethod
          |final class compileTimeOnly(message: String) extends scala.annotation.StaticAnnotation
        """.stripMargin)
      Seq(file)
    } else {
      Nil
    }
  }

  lazy val testCompileTimeOnlyAnnotation = Def.task {
    if (!scalaVersion.value.startsWith("2.10")) {
      val file = (sourceManaged in Test).value / "autowire" /  "CompileTimeOnlyTests.scala"
      IO.write(file,
        """

        """.stripMargin)
      Seq(file)
    } else {
      Nil
    }
  }



}

