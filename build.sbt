
val baseSettings = Seq(
  organization := "com.lihaoyi",
  name := "autowire",
  version := "0.2.7",
  scalaVersion := "2.13.1",
  crossScalaVersions := Seq("2.12.11", "2.13.1"),
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
  )
)

val acyclicVersion = Def.setting{ if (scalaVersion.value == "2.11.12") "0.1.8" else "0.2.0" }

val autowire = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .settings(baseSettings)
  .settings(
    autoCompilerPlugins := true,
    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "acyclic" % acyclicVersion.value % Provided,
      compilerPlugin("com.lihaoyi" %% "acyclic" % acyclicVersion.value),
      "com.lihaoyi" %%% "utest" % "0.7.4" % Test,
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "com.lihaoyi" %%% "upickle" % "1.0.0" % Test
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    // Sonatype
    publishArtifact in Test := false,
    publishTo := Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2")

  ).jsSettings(
      resolvers ++= Seq(
        "bintray-alexander_myltsev" at "https://dl.bintray.com/content/alexander-myltsev/maven"
      ),
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
      "com.esotericsoftware" % "kryo" % "5.0.0-RC5" % Test
    )
  ).nativeSettings(
    scalaVersion := "2.11.12",
    crossScalaVersions := Seq("2.11.12"),
    nativeLinkStubs := true
  )
