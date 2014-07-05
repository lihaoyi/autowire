package autowire

import scala.concurrent.Future
import scala.reflect.macros.Context
import language.experimental.macros
import scala.annotation.Annotation
import scala.collection.mutable

object Macros {
  def handlerTypeParam(c: Context) = {
    import c.universe._
    c.prefix.actualType.baseType(typeOf[Handler[_]].typeSymbol).typeArgs(0)
  }
  def ajaxMacro[R: c.WeakTypeTag]
               (c: Context)
               (f: c.Expr[R])
               (reader: c.Expr[upickle.Reader[R]])
               : c.Expr[Future[R]] = {

    import c.universe._
    val markerType = handlerTypeParam(c)
    def handle(t: Tree): Tree = {
      t match {
        case t @ q"$prefix.$method(..$args)"
          if prefix.symbol != null
          && prefix.symbol.isModule =>
          if (!t.symbol.asMethod.annotations.exists(_.tree.tpe =:= markerType)) {
            c.abort(
              c.enclosingPosition,
              s"You can only make calls to functions marked as @$markerType"
            )
          } else {
            val path = prefix.symbol
                             .fullName
                             .toString
                             .split('.')
                             .toSeq
                             .:+(method.toString)

            val pickled = args.map(e => q"upickle.write($e)")
            q"""(
              ${c.prefix.tree}.callRequest(
                autowire.Request(Seq(..$path), Seq(..$pickled)))
              ).map(upickle.read(_)($reader)
            )"""
          }

        case q"..$statements" if statements.length > 1=>
          q"..${statements.dropRight(1)}; ${handle(statements.last)}"
        case _ =>
          c.abort(
            c.enclosingPosition,
            "Invalid contents: contents of a `call` must be a single function call " +
            s"to a method on a top-level object marked with @$markerType"
          )
      }
    }

    val res = c.Expr[Future[R]](handle(f.tree))
//    println("RESSS")
//    println(res)
    res
  }
  def route[A](f: scala.Singleton*): RouteType = macro routeMacro[A]
  def routeMacro[A: c.WeakTypeTag]
                (c: Context)
                (f: c.Expr[scala.Singleton]*)
                : c.Expr[RouteType] = {
    println("-----------------------------------------------------")

    import c.universe._
    val routes: Seq[Tree] = for{
      singleton <- f
      tree = singleton.tree
      member <- tree.symbol.asModule.typeSignature.members
      if member.annotations.exists(_.tree.tpe =:= weakTypeOf[A])
    } yield {
      val path = tree.symbol.fullName.toString.split('.').toSeq :+ member.name.toString
      val args = member
        .typeSignature.paramLists.flatten
        .zipWithIndex
        .map{ case (arg, i) => q"upickle.read[${arg.typeSignature}](args($i))"}
        .toList

      val frag = cq"Request(Seq(..$path), args) => upickle.write($singleton.$member(..$args))"
      frag
    }
    val res = q"{case ..$routes}: RouteType"

    c.Expr[RouteType](res)
  }
}

