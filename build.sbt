val autowire = crossProject.settings(
  organization := "de.daxten",
  version := "0.3.0",
  name := "autowire",
  scalaVersion := "2.11.11",
  crossScalaVersions := Seq("2.10.6", "2.11.11", "2.12.2", "2.13.0-M1"),
  autoCompilerPlugins := true,
  addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.5"),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "acyclic" % "0.1.5" % "provided",
    "com.lihaoyi" %%% "utest" % "0.4.7" % "test",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "com.lihaoyi" %%% "upickle" % "0.4.4" % "test"
  ) ++ (
    if (!scalaVersion.value.startsWith("2.10.")) Nil
    else Seq(
      compilerPlugin("org.scalamacros" % s"paradise" % "2.0.0" cross CrossVersion.full),
      "org.scalamacros" %% s"quasiquotes" % "2.0.0"
    )
  ),
  testFrameworks += new TestFramework("utest.runner.Framework"),
  // Bintray
  publishArtifact in Test := false,
  bintrayReleaseOnPublish in ThisBuild := false,
  licenses in ThisBuild += ("MIT", url("http://opensource.org/licenses/MIT")),
  bintrayVcsUrl in ThisBuild := Some("git@github.com:daxten/autowire"),
  homepage := Some(url("https://github.com/daxten/autowire")),
  developers ++= List(
    Developer(
      email = "haoyi.sg@gmail.com",
      id = "lihaoyi",
      name = "Li Haoyi",
      url = url("https://github.com/lihaoyi")
    ) ,
    Developer(
      email = "alexej.haak@outlook.de",
      id = "daxten",
      name = "Alexej Haak",
      url = url("https://github.com/daxten")
    )
  )
).jsSettings(
  resolvers ++= Seq(
    "bintray-alexander_myltsev" at "http://dl.bintray.com/content/alexander-myltsev/maven"
  ),
  scalaJSStage in Test := FullOptStage,
  scalacOptions += {
    val tagOrHash =
      if (isSnapshot.value) sys.process.Process("git rev-parse HEAD").lines_!.head
      else "v" + version.value
    val a = (baseDirectory in LocalRootProject).value.toURI.toString
    val g = "https://raw.githubusercontent.com/lihaoyi/autowire/" + tagOrHash
    s"-P:scalajs:mapSourceURI:$a->$g/"
  }
).jvmSettings(
  resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
  libraryDependencies ++= Seq(
    "com.esotericsoftware.kryo" % "kryo" % "2.24.0" % "test"
  ),
  libraryDependencies ++= {
    if (!scalaVersion.value.startsWith("2.11.")) Nil
    else Seq(
      "org.scala-lang" %% "scala-pickling" % "0.9.1" % "test",
      "com.typesafe.play" %% "play-json" % "2.5.15" % "test"
    )
  }
)

lazy val autowireJS = autowire.js
lazy val autowireJVM = autowire.jvm