addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.0-M3")

addSbtPlugin("com.lihaoyi" % "utest-js-plugin" % "0.2.5-M3")

resolvers += Resolver.url("scala-js-releases",
  url("http://dl.bintray.com/scala-js/scala-js-releases/"))(
    Resolver.ivyStylePatterns)

