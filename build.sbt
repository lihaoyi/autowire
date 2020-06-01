
val baseSettings = Seq(
  organization := "com.lihaoyi",
  name := "autowire",
  version := "0.3.3-SNAPSHOT",
  scalaVersion := "2.13.2",
  crossScalaVersions := Seq("2.12.11", "2.13.2"),
  scmInfo := Some(ScmInfo(
    browseUrl = url("https://github.com/lihaoyi/autowire"),
    connection = "scm:git:git@github.com:lihaoyi/autowire.git"
  )),
  licenses := Seq("MIT" -> url("http://www.opensource.org/licenses/mit-license.html")),
  homepage := Some(url("https://github.com/lihaoyi/autowire")),
  developers += Developer(
    email = "haoyi.sg@gmail.com",
    id = "lihaoyi",
    name = "Li Haoyi",
    url = url("https://github.com/lihaoyi")
  ),
  scalacOptions ++= Seq("-deprecation", "-feature", "-language:higherKinds")
)

val autowire = crossProject(JSPlatform, JVMPlatform)
  .settings(baseSettings)
  .settings(
    autoCompilerPlugins := true,
    addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.2.0"),
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "acyclic" % "0.2.0" % Provided,
      "com.lihaoyi" %%% "utest" % "0.7.4" % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.lihaoyi" %%% "upickle" % "1.1.0" % Test,
      "com.typesafe.play" %%% "play-json" % "2.9.0" % Test,
      "io.suzaku" %%% "boopickle" % "1.3.2" % Test
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    // Sonatype
    publishArtifact in Test := false,
    publishTo := Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2")

  ).jsSettings(
      scalaJSStage in Test := FullOptStage,
      scalacOptions += {
        val tagOrHash =
          if (isSnapshot.value) sys.process.Process("git rev-parse HEAD").lineStream_!.head
          else "v" + version.value
        val a = (baseDirectory in LocalRootProject).value.toURI.toString
        val g = "https://raw.githubusercontent.com/lihaoyi/autowire/" + tagOrHash
        s"-P:scalajs:mapSourceURI:$a->$g/"
      }
  ).jvmSettings(
    resolvers += "Typesafe Repo" at "https://repo.typesafe.com/typesafe/releases/",
    libraryDependencies ++= Seq(
      "com.esotericsoftware" % "kryo" % "5.0.0-RC6" % Test
    )
  )

lazy val autowireJS = autowire.js
lazy val autowireJVM = autowire.jvm
