crossScalaVersions := Seq("2.10.4", "2.11.4")

val autowire = crossProject.settings(
  organization := "com.lihaoyi",

  version := "0.2.5",
  name := "autowire",
  scalaVersion := "2.10.4",
  autoCompilerPlugins := true,
  addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.2"),
  libraryDependencies ++= Seq(
    "com.lihaoyi" %% "acyclic" % "0.1.2" % "provided",
    "com.lihaoyi" %%% "utest" % "0.3.1" % "test",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    "com.lihaoyi" %%% "upickle" % "0.2.6" % "test"
  ) ++ (
    if (scalaVersion.value startsWith "2.11.") Nil
    else Seq(
      compilerPlugin("org.scalamacros" % s"paradise" % "2.0.0" cross CrossVersion.full),
      "org.scalamacros" %% s"quasiquotes" % "2.0.0"
    )
    ),
  testFrameworks += new TestFramework("utest.runner.Framework"),
  unmanagedSourceDirectories in Compile ++= {
    if (scalaVersion.value startsWith "2.10.") Seq(baseDirectory.value / ".."/"shared"/"src"/ "main" / "scala-2.10")
    else Seq(baseDirectory.value / ".."/"shared" / "src"/"main" / "scala-2.11")
  },
  //Vary compileTimeOnly based on scala version
  unmanagedSourceDirectories in Compile ++= {
    if (scalaVersion.value startsWith "2.10.") Seq(baseDirectory.value / "shared" / "main" / "scala-2.10")
    else Seq(baseDirectory.value /".."/ "shared"/"src"/"main"/ "scala-2.11")
  },
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
    "org.scala-lang" %% "scala-pickling" % "0.9.0" % "test",
    "com.esotericsoftware.kryo" % "kryo" % "2.24.0" % "test",
    "com.typesafe.play" %% "play-json" % "2.3.0" % "test"
  )
)

lazy val autowireJS = autowire.js
lazy val autowireJVM = autowire.jvm
