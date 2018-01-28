crossScalaVersions := Seq("2.11.12", "2.12.4")

val autowire = crossProject.settings(
  organization := "com.lihaoyi",

  version := "0.2.7-SNAPSHOT",
  name := "autowire",
  scalaVersion := "2.12.4",
  scalacOptions ++= Seq(
     "-language:higherKinds"
  ),
  autoCompilerPlugins := true,
  addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.5"),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "acyclic" % "0.1.5" % "provided",
    "com.lihaoyi" %%% "utest" % "0.4.4" % "test",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "com.lihaoyi" %%% "upickle" % "0.4.4" % "test"
  ) ++  Seq(
    compilerPlugin("org.scalamacros" % s"paradise" % "2.1.1" cross CrossVersion.full),
    "org.scalameta" %% s"quasiquotes" % "2.1.7" 
  ),
  testFrameworks += new TestFramework("utest.runner.Framework"),
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
).jsSettings(
    resolvers ++= Seq(
      "bintray-alexander_myltsev" at "http://dl.bintray.com/content/alexander-myltsev/maven"
    ),
    scalaJSStage in Test := FullOptStage
).jvmSettings(
  resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
  libraryDependencies ++= Seq(
//    "org.scala-lang" %% "scala-pickling" % "0.9.1" % "test",
    "com.esotericsoftware.kryo" % "kryo" % "2.24.0" % "test"
//    "com.typesafe.play" %% "play-json" % "2.4.8" % "test"
  ),
  libraryDependencies ++= {
    if (!scalaVersion.value.startsWith("2.11.")) Nil
    else Seq(
      "org.scala-lang" %% "scala-pickling" % "0.9.1" % "test",
      "com.typesafe.play" %% "play-json" % "2.4.8" % "test"
    )
  }
)

lazy val autowireJS = autowire.js
lazy val autowireJVM = autowire.jvm
