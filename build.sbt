val baseSettings = Seq(
  organization := "com.lihaoyi",
  name := "fansi",
  version := "0.2.4",
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.10.6", "2.11.11", "2.12.2", "2.13.0-M1"),
  scmInfo := Some(ScmInfo(
    browseUrl = url("https://github.com/lihaoyi/fansi"),
    connection = "scm:git:git@github.com:lihaoyi/fansi.git"
  )),
  licenses := Seq("MIT" -> url("http://www.opensource.org/licenses/mit-license.html")),
  homepage := Some(url("https://github.com/lihaoyi/autowire")),
  developers += Developer(
    email = "haoyi.sg@gmail.com",
    id = "lihaoyi",
    name = "Li Haoyi",
    url = url("https://github.com/lihaoyi")
  )
)

baseSettings

val autowire = crossProject
  .settings(baseSettings)
  .settings(
    autoCompilerPlugins := true,
    addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.9"),
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "acyclic" % "0.1.9" % Provided,
      "com.lihaoyi" %%% "utest" % "0.4.7" % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.lihaoyi" %%% "upickle" % "0.4.6" % Test
    ) ++ (
      if (!scalaVersion.value.startsWith("2.10.")) Nil
      else Seq(
        compilerPlugin("org.scalamacros" % s"paradise" % "2.0.0" cross CrossVersion.full),
        "org.scalamacros" %% s"quasiquotes" % "2.0.0"
      )
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    // Sonatype
    publishArtifact in Test := false,
    publishTo := Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2")

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
      "com.esotericsoftware.kryo" % "kryo" % "2.24.0" % Test
    ),
    libraryDependencies ++= {
      if (!scalaVersion.value.startsWith("2.11.")) Nil
      else Seq(
        "org.scala-lang" %% "scala-pickling" % "0.9.1" % Test,
        "com.typesafe.play" %% "play-json" % "2.4.8" % Test
      )
    }
  )

lazy val autowireJS = autowire.js
lazy val autowireJVM = autowire.jvm
