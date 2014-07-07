package autowire

import scala.concurrent.Future
import scala.reflect.macros.Context
import language.experimental.macros
import scala.annotation.Annotation
import scala.collection.mutable

object Macros {

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

    c.prefix.tree match {
      case t @ q"$module.apply[$tpe]" =>
        val markerType = c.prefix.actualType.typeArgs(1)
        def handle(t: Tree, dead: Set[Name] = Set.empty): Tree = {
          t match {
            case t @ q"($src1) => $lol" =>
              println("----- Removing Function " + t)
              handle(lol)
            case t @ q"$src2.$method(..$args)" =>
              println("----- Munging Call " + t)
              if (!(src2: Tree).tpe.typeSymbol.annotations.exists(_.tree.tpe =:= markerType)) {
                c.abort(
                  c.enclosingPosition,
                  s"You can only make calls to traits marked as @$markerType"
                )
              } else {
                val path = src2
                  .tpe
                  .widen
                  .toString
                  .split('.')
                  .toSeq
                  .:+(method.toString)
                println(":::: " + args.zip(t.symbol.asMethod.paramLists.flatten))
                val pickled = args.zip(t.symbol.asMethod.paramLists.flatten)
                  .filter{
                    case (Ident(name), _) => !dead(name)
                    case (q"$thing.$name", _) if name.toString.contains("$default$") => false
                    case _ => true
                  }

                  .map{case (t, param: Symbol) => q"${param.name.toString} -> upickle.write($t)"}


                println(c.prefix.tree)
                q"""(
                  $module.callRequest(
                    autowire.Request(Seq(..$path), Map(..$pickled))
                  ).map(upickle.read(_)($reader))
                )"""
              }



            case t @ q"..${statements: List[ValDef]}; $last"
              if statements.length > 0
              && statements.forall(ValDef.unapply(_).isDefined) =>
              println("----- Handling Defaults " + t)
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
        println("f.tree " + f.tree)
        val res = c.Expr[Future[R]](handle(f.tree))
            println("RESSS")
            println(res)
        res
      case _ =>
        c.abort(c.enclosingPosition, "FAIL")
    }


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
      t = singleton.tree.symbol.asInstanceOf[ModuleSymbol]
      apiClass = singleton
        .tree
        .symbol
        .asModule
        .moduleClass
        .asClass
        .baseClasses
        .find(_.annotations.exists(_.tree.tpe =:= weakTypeOf[A]))
        .get
      member <- apiClass.typeSignature.members
      // not some rubbish defined on AnyRef
      if !weakTypeOf[AnyRef].members.exists(_.name == member.name)
      // Not a default value synthemethod
      if !member.isSynthetic
    } yield {
      val path = apiClass.fullName.toString.split('.').toSeq :+ member.name.toString

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

