package autowire

import scala.concurrent.Future
import scala.reflect.macros.Context
import language.experimental.macros
import scala.annotation.Annotation
import scala.collection.mutable
import acyclic.file

import Core._

object Macros {

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

  def futurize(c: Context)(t: c.Tree, member: c.Symbol) = {
    import c.universe._
    if (member.asMethod.returnType <:< c.typeOf[Future[_]]) {
      t
    } else {
      q"scala.concurrent.Future.successful($t)"
    }
  }
  def clientMacro[Result]
                 (c: Context)
                 ()
                 (implicit r: c.WeakTypeTag[Result])
                 : c.Expr[Future[Result]] = {
    import c.universe._
    object Pkg{
      def unapply(t: Tree): Option[Tree] = {
        if (Seq("autowire.this", "autowire").contains(t.toString)) Some(t)
        else None
      }
    }
    val res = for {
      q"${Pkg(_)}.`package`.$callableName[$t]($contents)" <- Win(c.prefix.tree,
        "You can only .call() on the Proxy returned by autowire.Client.apply, not " + c.prefix.tree
      )
      if Seq("clientFutureCallable", "clientCallable").contains(callableName.toString)
      // If the tree is one of those default-argument containing blocks or
      // functions, pry it apart such that the main logic can operate on the
      // inner tree, and leave instructions on how
      (unwrapTree: Tree, methodName: TermName, args: Seq[Tree], prelude: Seq[Tree], deadNames: Seq[String]) = (contents: Tree) match{
        case x @ q"$thing.$call(..$args)" => (thing, call, args, Nil, Nil)
        case t @ q"..${statements: List[ValDef]}; $thing.$call(..$args)"
          if statements.forall(_.isInstanceOf[ValDef]) =>

          val (liveStmts, deadStmts) = statements.tail.partition {
            case ValDef(mod, _, _, Select(singleton, name))
              if name.toString.contains("$default") => false
            case _ => true
          }
          val ValDef(_, _, _, rhs) = statements.head

          (rhs, call, args, liveStmts, deadStmts.map(_.name))
        case x =>
          c.abort(x.pos, s"You can't call the .call() method on $x, only on autowired function calls.")
      }

      q"${Pkg(_)}.`package`.unwrapClientProxy[$trt, $pt, $rb, $wb]($proxy)" <- Win(unwrapTree,
        s"XX You can't call the .call() method on $contents, only on autowired function calls"
      )
      path = trt.tpe
                .widen
                .typeSymbol
                .fullName
                .toString
                .split('.')
                .toSeq
                .:+(methodName.toString)
      method = (trt.tpe: Type).decl(methodName).asMethod
      pickled = args
        .zip(method.paramLists.flatten)
        .filter{
          case (Ident(name: TermName), _) => !deadNames.contains(name)
          case (q"$thing.$name", _) if name.toString.contains("$default$") => false
          case _ => true
        }
        .map{case (t, param: Symbol) => q"${param.name.toString} -> $proxy.self.write($t)"}

    } yield {
      q"""{
        ..$prelude;
        $proxy.self.doCall(
          autowire.Core.Request(Seq(..$path), Map(..$pickled))
        ).map($proxy.self.read[${r}](_))
      }"""
    }

    res match{
      case Win(tree, s) => c.Expr[Future[Result]](tree)
      case Luz(s) => c.abort(c.enclosingPosition, s)
    }
  }

  def routeMacro[Trait, PickleType]
                (c: Context)
                (target: c.Expr[Trait])
                (implicit t: c.WeakTypeTag[Trait], pt: c.WeakTypeTag[PickleType])
                : c.Expr[Router[PickleType]] = {
//    println("-----------------------------------------------------")

    import c.universe._

    val tree = target.tree
    val apiClass = weakTypeOf[Trait]
    val routes: Seq[Tree] = for{
      member <- apiClass.decls.toSeq
      // not some rubbish defined on AnyRef
      if !weakTypeOf[AnyRef].members.exists(_.name == member.name)
      // Not a default value synthetic methods
      if !member.isSynthetic
    } yield {
      val path = apiClass.typeSymbol.fullName.toString.split('.').toSeq :+ member.name.toString
      val flatArgs =
        member.typeSignature
          .paramLists
          .flatten


      def hasDefault(arg: Symbol, i: Int) = {
        val defaultName = s"${member.name}$$default$$${i + 1}"
        if (tree.symbol.asModule.typeSignature.members.exists(_.name.toString == defaultName))
          Some(defaultName)
        else
          None
      }
      val argName = c.freshName[TermName]("args")
      val args: Seq[Tree] = flatArgs.zipWithIndex.map { case (arg, i) =>
        val default = hasDefault(arg, i) match {
          case Some(defaultName) => q"scala.util.Right($target.${TermName(defaultName)})"
          case None => q"scala.util.Left(autowire.Error.Param.Missing(${arg.name.toString}))"
        }
        q"""
          autowire.Internal.read[$pt, ${arg.typeSignature}](
            $argName,
            $default,
            ${arg.name.toString},
            ${c.prefix}.read[${arg.typeSignature}](_)
          )
        """
      }

      val bindings = args.foldLeft[Tree](q"autowire.Internal.HNil[autowire.Internal.FailMaybe]()") { (old, next) =>
        q"autowire.Internal.#:($next, $old)"
      }

      val nameNames: Seq[TermName] = flatArgs.map(x => x.name.toTermName)
      val assignment = flatArgs.foldLeft[Tree](q"autowire.Internal.HNil()") { (old, next) =>
        pq"autowire.Internal.#:(${next.name.toTermName}: ${next.typeSignature} @unchecked, $old)"
      }

      val requiredArgs = flatArgs.zipWithIndex.collect {
        case (arg, i) if hasDefault(arg, i).isEmpty => arg.name.toString
      }

      val frag = cq""" autowire.Core.Request(Seq(..$path), $argName) =>
        autowire.Internal.doValidate($bindings) match{
          case (..$assignment) =>
            ${futurize(c)(q"$target.$member(..$nameNames)", member)}.map(${c.prefix}.write(_))
        }
      """

      frag
    }

    val res = q"{case ..$routes}: autowire.Core.Router[$pt]"
    c.Expr(res)
  }
}

