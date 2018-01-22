Autowire 0.2.6
==============

Autowire is a pair of macros that allows you to perform type-safe, reflection-free RPC between Scala systems. Autowire allows you to write type-safe Ajax/RPC calls that look like:

```scala    
// shared interface
trait Api{
  def add(x: Int, y: Int, z: Int): Int 
}

// server-side router and implementation 
object Server extends autowire.Server...
object ApiImpl extends Api
  def add(x: Int, y: Int, z: Int) = x + y + z 
}

// client-side callsite

import autowire._ // needed for correct macro application

object Client extends autowire.Client...
Client[Api].add(1, 2, 3).call(): Future[Int]
//         |    |             |
//         |    |             The T is pickled and wrapped in a Future[T]
//         |    The arguments to that method are pickled automatically
//         Call a method on the `Api` trait
```

This provides a range of nice properties: under-the-hood the macro converts your method calls into RPCs, together with the relevant serialization code, but you do not need to deal with that yourself. Furthermore, since the RPCs appear to be method calls, tools like IDEs are able to work with them, e.g. doing project-wide renames or analysis (jump-to-definition, find-usages, etc.). 

Most importantly, the compiler is able to typecheck these RPCs, and ensure that you are always calling them with the correct arguments, and handling the return-value correctly in turn. This removes an entire class of errors.  

Autowire is completely agnostic to both the serialization library, and the transport-mechanism.

Getting Started
===============

Autowire is available at the following maven coordinates, for Scala-JVM and Scala-JS respectively:

```scala
"com.lihaoyi" %% "autowire" % "0.2.6"
"com.lihaoyi" %%% "autowire" % "0.2.6"
```

It's only available for Scala.js 0.5.3+. Autowire works on both Scala-JVM and Scala-JS, meaning you can use it to get type-safe Ajax calls between a browser and your servers.

Minimal Example
===============
Here is a minimal example of Autowire in action, using [uPickle](https://www.github.com/lihaoyi/upickle) to serialize the transferred data:

```scala
import autowire._
import upickle._

// shared API interface 
trait MyApi{
  def doThing(i: Int, s: String): Seq[String]
}

// server-side implementation, and router 
object MyApiImpl extends MyApi{
  def doThing(i: Int, s: String) = Seq.fill(i)(s)
}
object MyServer extends autowire.Server[String, upickle.Reader, upickle.Writer]{
  def write[Result: Writer](r: Result) = upickle.write(r)
  def read[Result: Reader](p: String) = upickle.read[Result](p)
  
  val routes = MyServer.route[MyApi](MyApiImpl)
}

// client-side implementation, and call-site
object MyClient extends autowire.Client[String, upickle.Reader, upickle.Writer]{
  def write[Result: Writer](r: Result) = upickle.write(r)
  def read[Result: Reader](p: String) = upickle.read[Result](p)

  override def doCall(req: Request) = {
    println(req)
    MyServer.routes.apply(req)
  }
}

MyClient[MyApi].doThing(3, "lol").call().foreach(println)
// List(lol, lol, lol)
```

In this example, we have a shared `MyApi` trait, which contains a trait/interface which is shared by both client and server. The server contains an implementation of this trait, while the client can make calls against the trait using the `route` macro provided by `MyServer`.

Although in this example everything is happening locally, this example goes through the entire serialization/de-serialization process when transferring the arguments/return-value. Thus, it is trivial to replace the direct call to `MyServer.routes` to a remote call over HTTP or TCP or some other transport with `MyClient` and `MyServer`/`MyApiImpl` living on different machines or even running on different platforms (e.g. Scala-JS/Scala-JVM).

Here's a [longer example](https://github.com/lihaoyi/workbench-example-app/tree/autowire), which takes advantage of autowire's cross-platform-ness to write an interactive client-server web-app.  

Note that:
- The client proxy, `MyClient[MyApi]` in this example, must be explicitly written out, and followed by `.call()`  at each site where a remote call is made. That is, you cannot assign `MyClient[MyApi]` to a variable and use that instead.
- The methods in the autowired API can also be asynchronous, in which case they return `Future[T]`. So the example above could be modeled async as `def doThing(i: Int, s: String): Future[Seq[String]]`.

Reference
=========

This section goes deeper into the steps necessary to use Autowire, and should cover extension points not obvious from the above example.

To begin with, the developer has to implement two interfaces, `Client`:

```scala
trait Client[PickleType, Reader[_], Writer[_]] {
  type Request = Core.Request[PickleType]
  def read[Result: Reader](p: PickleType): Result
  def write[Result: Writer](r: Result): PickleType
  
  /**
   * A method for you to override, that actually performs the heavy
   * lifting to transmit the marshalled function call from the [[Client]]
   * all the way to the [[Core.Router]]
   */
  def doCall(req: Request): Future[PickleType]
}
```

And `Server`

```scala
trait Server[PickleType, Reader[_], Writer[_]] {
  type Request = Core.Request[PickleType]
  type Router = Core.Router[PickleType]
  
  def read[Result: Reader](p: PickleType): Result
  def write[Result: Writer](r: Result): PickleType

  /**
   * A macro that generates a `Router` PartialFunction which will dispatch incoming
   * [[Requests]] to the relevant method on [[Trait]]
   */
  def route[Trait](target: Trait): Router = macro Macros.routeMacro[Trait, PickleType]
}
```

Both `Client` and `Server` are flexible: you get to specify multiple type parameters:

- `PickleType`: or what type you want to serialize your arguments/return-value into. Typically something like `String`, `Array[Byte]`, `ByteString`, etc.
- `Reader[_]`: a context-bound used to convert data from `PickleType` to a desired type `T`. Typically something like `upickle.Reader`, `play.api.libs.json.Reads`, etc.
- `Writer[_]`: a context-bound used to convert data to `PickleType` from an original type `T`. Typically something like `upickle.Writer`, `play.api.libs.json.Writes`, etc.

If the serialization library you're using doesn't need `Reader[_]` or `Writer[_]` context-bound, you can use `autowire.Bounds.None` as the context-bound, and it will be filled in automatically. You then need to override the `read` and `write` methods of both sides, in order to tell Autowire how to serialize and deserialize your typed values.

Lastly, you need to wire together client and server to get them talking to one another: this is done by overriding `Client.doCall`, which takes the `Request` object holding the serialized arguments and returns a `Future` containing the serialized response, and by using `Server.route`, which generates a `Router` `PartialFunction` which you can then feed `Request`
 object into to receive a serialized response in return. These types are defined as
 
```scala
object Core {
  /**
   * The type returned by the [[Server.route]] macro; aliased for
   * convenience, but it's really just a normal `PartialFunction`
   * and can be combined/queried/treated like any other.
   */
  type Router[PickleType] = PartialFunction[Request[PickleType], Future[PickleType]]

  /**
   * A marshalled autowire'd function call.
   *
   * @param path A series of path segments which illustrate which method
   *             to call, typically the fully qualified path of the
   *             enclosing trait followed by the name of the method
   * @param args Serialized arguments for the method that was called. Kept
   *             as a Map of arg-name -> serialized value. Values which
   *             exactly match the default value are omitted, and are
   *             simply re-constituted by the receiver.
   */
  case class Request[PickleType](path: Seq[String], args: Map[String, PickleType])
}
```
 
 is outside the scope of Autowire, as is how you serialize/deserialize the arguments/return-value using the `read` and `write` functions. 

In short,

- `{Client, Server}.{read, write}` is your chance to substitute in whatever serialization library you want. Any library should work, as long as you can put the serialization code into the `read` and `write` functions to serialize an object of type `T`. Autowire works with any transport and any serialization library, including [Java-serialization](http://docs.oracle.com/javase/tutorial/jndi/objects/serial.html), [Kryo](https://github.com/EsotericSoftware/kryo) and [Play-Json](https://www.playframework.com/documentation/2.4.x/ScalaJsonCombinators), with [unit tests](blob/master/jvm/src/test/scala/autowire/InteropTests.scala) to ensure that they are working.
- `Client.doCall` and `Server.route` is your chance to choose whatever transport mechanism you want to use. By that point, the arguments/return-value have already been mostly serialized, with only a small amount of structure (e.g. the map of argument-names to serialized-values) left behind. Exactly how you get the `Request` object from the `Client` to the `Server` (HTTP, TCP, etc.) and the response-data back is up to you as long as you can give Autowire a `Future[PickleType]` in exchange for its `Request`.  

If you still don't fully understand, and need help getting something working, take a look at the [complete example](https://github.com/lihaoyi/workbench-example-app/tree/autowire).  

Error Handling
==============

Assuming that your chosen serialization library behaves as intended, Autowire only throws exceptions when de-serializing data. This could come from many sources: data corrupted in transit, external API users making mistakes, malicious users trying to pen-test your API.

Autowire's de-serialization behavior is documented in the errors it throws:

```scala
package autowire

trait Error extends Exception
object Error{
  /**
   * Signifies that something went wrong when de-serializing the
   * raw input into structured data.
   *
   * This can contain multiple exceptions, one for each parameter.
   */
  case class InvalidInput(exs: Param*) extends Exception with Error
  sealed trait Param
  object Param{

    /**
     * Some parameter was missing from the input.
     */
    case class Missing(param: String) extends Param

    /**
     * Something went wrong trying to de-serialize the input parameter;
     * the thrown exception is stored in [[ex]]
     */
    case class Invalid(param: String, ex: Throwable) extends Param
  }
}
```

These errors are not intended to be end-user-facing. Rather, as far as possible they are maintained as structured data, and it us up to the developer using Autowire to decide how to respond (error page, logging, etc).

Autowire only provides custom error handling on the server side, since there are multiple arguments to validate/aggregate. If something goes wrong on the client during de-serialization, you will get the exception thrown from whichever serialization library you're using.

Autowire supports interface methods with default values. When a RPC call's arguments are left out in favor of defaults, Autowire omits these arguments entirely from the pickled request, and evaluates these arguments on the server when it finds them missing from the request instead of throwing an `Error.Param.Missing` like it would if that particular parameter did not have default.

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
Client[Api].fastOp(editor.code).call()

[error] /Client.scala:104: value fastOp is not a member of fiddle.Api
[error]     Client[Api].fastOp(editor.code).call()
[error]                 ^
```

- Similarly, since Autowire RPC calls are (at least superficially) just method calls, things like find-usages, rename-all, and other such tools all work flawlessly across these calls.

- Autowire deals directly with `Future`s; this means that it plays nicely with the rest of the Scala ecosystem, including tools such as `async` and `await`, while making it much easier for future developers to reason about than callback soup.  

```scala
// endpoint definition
def completeStuff(txt: String, flag: String, offset: Int): List[(String, String)]

// somewhere else inside an `async` block
val res: List[(String, String)] = {
  await(Client[Api].completeStuff(code, flag, intOffset).call())
}
```

- Also shown in the example above, serialization and deserialization of data structures is handled implicitly. Thus you can call functions which return non-trivial structures and receive the structured data directly, without having to fiddle with JSON or other ad-hoc encodings yourself. 

Limitations
===========

Autowire can only serialize and deserialize things that the chosen serialization library can. For example, if you choose to go with uPickle, this means most of the immutable data structures in the standard library and case classes, but circular data structures aren't supported, and arbitrary object graphs don't work. 

Autowire does not support method overloading and type parameters on the interfaces/traits used for making the RPCs.
 
Apart from that, Autowire is a pretty thin layer on top of any existing serialization library and transport layer, and does not project much functionality apart from routing. It is up to the developer using Autowire to decide how he wants to transport the serialized data back and forth, how he wants to respond to errors, etc.

To see a simple example involving a ScalaJS client and Spray server, check out this example:

https://github.com/lihaoyi/workbench-example-app/tree/autowire


Changelog
=========

0.2.6
-----

- `.call()`s now wrap the endpoint using `Future()` instead of
  `Future.successful`, which means exceptions now get caught and propagated
  inside the `Future` rather than blowing up the call stack

- Cross-publish for Scala 2.12.0
