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

  def futurize(c: Context)(t: c.Tree, member: c.Symbol) = {
    import c.universe._
    if (member.asMethod.returnType <:< c.typeOf[Future[_]]) {
      t
    } else {
      q"scala.concurrent.Future.successful($t)"
    }
  }
  def ajaxMacro[R: c.WeakTypeTag]
               (c: Context)
               (f: c.Expr[R])
               (reader: c.Expr[upickle.Reader[R]])
               : c.Expr[Future[R]] = {

    import c.universe._
    val markerType = handlerTypeParam(c)
    def handle(t: Tree, dead: Set[Name] = Set.empty): Tree = {
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

            val pickled = args.zip(t.symbol.asMethod.paramLists.flatten)
                              .filter{
                                case (Ident(name), _) => !dead(name)
                                case _ => true
                              }
                              .map{case (t, param: Symbol) => q"${param.name.toString} -> upickle.write($t)"}
            q"""(
              ${c.prefix.tree}.callRequest(
                autowire.Request(Seq(..$path), Map(..$pickled))
              ).map(upickle.read(_)($reader))
            )"""
          }

        case q"..${statements: List[ValDef]}; $last"
          if statements.length > 0
          && statements.forall(ValDef.unapply(_).isDefined) =>

          // Look for statements involving the use of default arguments,
          // and remove them and mark their names as dead
          val (lessStatements, deadStatements) =
            statements.partition {
              case ValDef(mod, _, _, Select(singleton, name))
                if name.toString.contains("$default") =>
                false
              case _ => true
            }

          q"""
            ..$lessStatements;
            ${handle(
              last,
              deadStatements.map(_.name).toSet
            )}
          """
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
        .typeSignature
        .paramLists
        .flatten
        .zipWithIndex
        .map{
          case (arg, i) =>
            val defaultName = s"${member.name}$$default$$${i+1}"
            def get(t: Tree) = q"""
              args.get(${arg.name.toString}).fold($t)(x => autowire.wrapInvalid(upickle.read[${arg.typeSignature}](x)))
            """
            if (tree.symbol.asModule.typeSignature.members.exists(_.name.toString == defaultName))
              get(q"$singleton.${TermName(defaultName)}")
            else
              get(q"throw new autowire.InputError(new Exception())")

        }
        .toList


      val frag =
        cq"""
          autowire.Request(Seq(..$path), args) =>
          ${futurize(c)(q"$singleton.$member(..$args)", member)}.map(upickle.write(_))
        """
      frag
    }
    val res = q"{case ..$routes}: autowire.RouteType"
    println(res)
    c.Expr[RouteType](res)
  }
}

