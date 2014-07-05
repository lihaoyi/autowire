autowire 0.0.1
==============

Autowire is a pair of macros that allows you to perform type-safe, reflection-free RPC between Scala systems. Autowire provides two main primitives: calling is done via the `rpc` macro
 
```scala
def rpc[T](f: => T)(implicit : Future[T]
```

And routing of calls on the other end is done via the `route` macro

```scala
def route(handlers: Singleton*): Future[T]
```