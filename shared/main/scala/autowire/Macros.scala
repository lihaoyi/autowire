package autowire

import scala.concurrent.Future
import scala.reflect.macros.Context
import language.experimental.macros
import scala.annotation.Annotation
import scala.collection.mutable

sealed trait Check[T] {
  def map[V](f: T => V): Check[V]
  def flatMap[V](f: T => Check[V]): Check[V]
  def withFilter(f: T => Boolean): Check[T]
}
case class Luz[T](s: String) extends Check[T]{
  def map[V](f: T => V) = Luz[V](s)
  def flatMap[V](f: T => Check[V]) = Luz[V](s)
  def withFilter(f: T => Boolean) = Luz[T](s)
}

case class Win[T](t: T, s: String) extends Check[T]{
  def map[V](f: T => V) = Win(f(t), s)
  def flatMap[V](f: T => Check[V]) = f(t)
  def withFilter(f: T => Boolean) = if (f(t)) this else Luz(s)
}

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
    val markerType = c.prefix.actualType.widen.typeArgs(1)

    // If the tree is one of those default-argument containing blocks or
    // functions, pry it apart such that the main logic can operate on the
    // inner tree, and leave instructions on how
    val (inner: Tree, dead: Set[TermName], wrap: (Tree => Tree)) = f.tree match{
      case t @ q"($src1) => $lol" => (lol, Set.empty, (x: Tree) => x)
      case t @ q"..${statements: List[ValDef]}; $last"
        if statements.length > 0
          && statements.forall(ValDef.unapply(_).isDefined) =>

        val (liveStmts, deadStmts) = (statements: List[ValDef]).partition {
          case ValDef(mod, _, _, Select(singleton, name))
            if name.toString.contains("$default") => false
          case _ => true
        }

        (last, deadStmts.map(_.name).toSet,(t: Tree) => q"..$liveStmts; $t")
      case x => (x, Set.empty, (y: Tree) => y)
    }

    val check = for{
      t @ q"$src2.$method(..$args)" <- Win(inner,
        "Invalid contents: contents of `Handler.apply` must be a single " +
        s"function call to a method on a top-level object marked with @$markerType"
      )
      true <- Win(src2.tpe.typeSymbol.annotations.exists(_.tree.tpe =:= markerType),
        s"You can only make calls to traits marked as @$markerType"
      )

      path = src2
        .tpe
        .widen
        .toString
        .split('.')
        .toSeq
        .:+(method.toString)

      pickled = args
        .zip(t.symbol.asMethod.paramLists.flatten)
        .filter{
          case (Ident(name: TermName), _) => !dead(name)
          case (q"$thing.$name", _) if name.toString.contains("$default$") => false
          case _ => true
        }
        .map{case (t, param: Symbol) => q"${param.name.toString} -> upickle.write($t)"}


    } yield {

      wrap(q"""(
        ${c.prefix}.callRequest(
          autowire.Request(Seq(..$path), Map(..$pickled))
        ).map(upickle.read(_)($reader))
      )""")
    }

    check match{
      case Win(tree, s) => c.Expr[Future[R]](tree)
      case Luz(s) => c.abort(c.enclosingPosition, s)
    }
  }
  def route[A](f: scala.Singleton*): RouteType = macro routeMacro[A]
  def routeMacro[A: c.WeakTypeTag]
                (c: Context)
                (f: c.Expr[scala.Singleton]*)
                : c.Expr[RouteType] = {
//    println("-----------------------------------------------------")

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
      // Not a default value synthetic methods
      if !member.isSynthetic
    } yield {
      val path = apiClass.fullName.toString.split('.').toSeq :+ member.name.toString

      val args = member
        .typeSignature
        .paramLists
        .flatten
        .zipWithIndex
        .map{ case (arg, i) =>
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
    c.Expr[RouteType](res)
  }
}

