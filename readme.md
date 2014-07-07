Autowire 0.1.0
==============

Autowire is a pair of macros that allows you to perform type-safe, reflection-free RPC between Scala systems. Autowire provides two main primitives: a macro for type safe RPC calls
 
```scala
abstract class Client[R]{
  def apply[T]: ClientProxy[T, R] = new ClientProxy(this)
  def callRequest(req: Request): Future[String]
}

class ClientProxy[T, A](h: Client[A]){
  def apply[R: upickle.Reader](f: T => R): Future[R]
}
```

And routing of calls on the other end done via the `route` macro

```scala
def route(handlers: Singleton*): PartialFunction[Request, Future[String]]
```

With the `Request` data structure being defined as 

```scala
case class Request(path: Seq[String], args: Map[String, String])
```

This allows you to safely wire up disparate endpoints across the internet, taking care of all the serialization for you using [uPickle](https://github.com/lihaoyi/upickle), while still giving your full control of piping the data across some underlying transport (Ajax, tcp, etc.). As long as you are able to transmit the `Request` from the client and can provide a `Request` to `route`'s partial function, Autowire doesn't care how you do it.

Minimal Example
===============

A minimal example of how Autowire works can be found in the unit tests:

```scala
// Annotation used to mark all traits belonging to a particular "kind" of RPC
class Rpc extends Annotation

// The RPC public interface
@Rpc
trait Api{
  def multiply(x: Double, ys: Seq[Double]): String
}

// Object implementing the RPC public interface
object Controller extends Api{
  def multiply(x: Double, ys: Seq[Double]): String = x + ys.map("*"+_).mkString
}

// RPC Client which talks to any interface of kind `RPC` 
object Client extends autowire.Client[Rpc]{

  // Auto-generates the routes to the various methods of `Controller`
  val router = Macros.route[Rpc](Controller)

  // Delegate the `Request` object directly; in other 
  // circumstances this could go over the network or Ajax  
  def callRequest(r: Request) = router(r)
}
```

This defines and wires up everything. The `route` macro takes a list of objects which implement some trait annotated with (in this case) `Rpc`, and statically generates a set of routes that route incoming `Request` objects to the functions exposed by the `Rpc`-annotated traits. After that, you can make RPC calls via

```scala
println(await(Client[Api](_.add(1, 2, 3)))) // "1+2+3"
```

Where the `Client[Api](_.add(1, 2, 3))` is a macro that expands to the code necessary to generate a `Request` object in the first place and hands it to the `Client.callRequest` method.

In this case, `callRequest` just hands the `Request` directly to the `route` function, but you could easily do the same thing but passing the `Request` object over an Ajax call, TCP, Websockets, etc. as long as you have some way of getting the `Request` object from `callRequest` to the `route` function at the other end.

Usage With ScalaJS
==================

The fact that Autowire works perfectly fine across both Scala-JVM and Scala-JS makes it ideal for wiring up your web-server and client code for making type-safe, statically-checked Ajax requests. For example, [Scala-Js-Fiddle](http://www.scala-js-fiddle.com) uses it extensively for client-server calls. To do this, we have essentially the same code as above, except that instead of `callRequest` being a direct call to the `router`, we need to instead shuttle the data over HTTP:
 
```scala
object Post extends autowire.Client[Web]{
  override def callRequest(req: Request): Future[String] = {
    val url = "/api/" + req.path.mkString("/")
    logln("Calling " + url)
    dom.extensions.Ajax.post(
      url = Shared.url + url,
      data = upickle.write(req.args)
    ).map(_.responseText)
  }
}
```

As you can see, we're packaging up the path and data of the request and `POST`-ing it to some `/api/...` URL. On the server, this is backed by a Spray server which receives the `POST` request and shuttles it into the `route` function:

```scala
post {
  path("api" / Segments){ s =>
    extract(_.request.entity.asString) { e =>
      complete {
        autowire.Macros.route[Web](Server)(
          autowire.Request(s, upickle.read[Map[String, String]](e))
        )
      }
    }
  }
}
```

In this case, the public API of the web server is defined in the `shared/` project so as to be available to both the client and server code:
 
```scala
class Web extends ClassfileAnnotation

@Web
trait Api{
  def compile(txt: String): (String, Option[String])
  def fastOpt(txt: String): (String, Option[String])
  def fullOpt(txt: String): (String, Option[String])
  def export(compiled: String, source: String): String
  def `import`(compiled: String, source: String): String
  def completeStuff(txt: String, flag: String, offset: Int): List[(String, String)]
}
```

The server code implements these methods on the `Server` object, with the routes generated as shown above, and the client code can then use the `Post` object to directly "call" methods on the `Api` trait:
  
```scala
Post[Api](_.fullOpt(editor.code))  
```

Why Autowire
============

The motivation for Autowire is the fact that Ajax requests between client-side Javascript and server-side something-else has historically been a point of fragility and boilerplate, with problems such as:

- Being stringly-typed and impossibile to statically analyze
- Prone to getting broken due to typos and incomplete refactoring
- Needing reams of boilerplate to pickle/unpickle data structures on both ends
- Using callbacks all over the place, confusing future developers

Autowire aims to solve all these problems:

- Ajax calls using Autowire are statically checked just like the rest of your program. For example, typos are immediately spotted by the compiler:

```scala
Post[Api](_.fastOp(editor.code))

[error] /Client.scala:104: value fastOp is not a member of fiddle.Api
[error]     Post[Api](_.fastOp(editor.code))
[error]                 ^
```

- Similarly, since Autowire RPC calls are (at least superficially) just method calls, things like "find usages", IDE-renaming, and other such tools all work flawlessly across these calls.

- Autowire deals directly with `Future`s; this means that it plays nicely with the rest of the Scala ecosystem, including tools such as `async` and `await`, while making it much easier for future developers to reason about than callback soup.  

```scala
// endpoint definition
def completeStuff(txt: String, flag: String, offset: Int): List[(String, String)]

// somewhere else inside an `async` block
val res: List[(String, String)] = {
  await(Post[Api](_.completeStuff(code, flag, intOffset)))
}
```

- Also shown in the example above, serialization and deserialization of data structures is handled implicitly by uPickle. Thus you can call functions which return non-trivial structures and receive the structured data directly, without having to fiddle with JSON or other ad-hoc encodings yourself. 

Limitations
===========

Autowire naturally has some limitations, some fundamental, and some not:

- It can only serialize and deserialize things that [uPickle](https://www.github.com/lihaoyi/upickle) can. By default that means most of the immutable data structures in the standard library, but case classes and such don't come by default, circular data structures aren't supported. This will improve slowly as uPickle improves.
- It's hard-coded to use uPickle for serialization/de-serialization. This could change, but for now uPickle is the only serialization library that works across both Scala-JVM and Scala-JS
- It's performance characteristics are unknown.
- It's still a version 0.1.0 and likely to change in the near future. 

Maven Artifacts
===============

Autowire is available at the following maven coordinates, for Scala-JVM and Scala-JS respectively:

```scala
"com.lihaoyi" %% "autowire" % "0.1.0"
"com.lihaoyi" %%% "autowire" % "0.1.0"
```

It's only available for Scala 2.11.x

