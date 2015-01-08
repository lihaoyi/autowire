package autowire

import scala.concurrent.Future
import scala.reflect.macros.Context
import language.experimental.macros
import acyclic.file

import Core._

object Macros {

  sealed trait Check[T] {
    def map[V](f: T => V): Check[V]
    def flatMap[V](f: T => Check[V]): Check[V]
    def withFilter(f: T => Boolean): Check[T]
  }
  case class Luz[T](s: String) extends Check[T] {
    def map[V](f: T => V) = Luz[V](s)
    def flatMap[V](f: T => Check[V]) = Luz[V](s)
    def withFilter(f: T => Boolean) = Luz[T](s)
  }

  case class Win[T](t: T, s: String) extends Check[T] {
    def map[V](f: T => V) = Win(f(t), s)
    def flatMap[V](f: T => Check[V]) = f(t)
    def withFilter(f: T => Boolean) = if (f(t)) this else Luz(s)
  }


  class MacroHelp[C <: Context](val c: C) {
    import c.universe._
    def futurize(t: Tree, member: MethodSymbol) = {
      if (member.returnType <:< c.typeOf[Future[_]]) t
      else q"scala.concurrent.Future.successful($t)"
    }

    def getValsOrMeths(curCls: Type): Iterable[Either[(c.Symbol, MethodSymbol), (c.Symbol, MethodSymbol)]] = {
      def isAMemberOfAnyRef(member: Symbol) = weakTypeOf[AnyRef].members.exists(_.name == member.name)
      val membersOfBaseAndParents: Iterable[Symbol] = curCls.declarations ++ curCls.baseClasses.map(_.asClass.toType.declarations).flatten
      val extractableMembers = for {
        member <- membersOfBaseAndParents
        if !isAMemberOfAnyRef(member)
        if !member.isSynthetic
        if member.isPublic
        if member.isTerm
        memTerm = member.asTerm
        if memTerm.isMethod
      } yield {
        member -> memTerm.asMethod
      }

      extractableMembers flatMap { case (member, memTerm) =>
        if (memTerm.isGetter) {
          //This is a val (or a var-getter) so we will need to recur here
          Seq(Left(member -> memTerm))
        } else if (memTerm.isSetter || memTerm.isConstructor) {
          //Ignore setters and constructors
          Nil
        } else {
          Seq(Right(member -> memTerm))
        }
      }
    }

    def extractMethod(
      pickleType: WeakTypeTag[_],
      meth: MethodSymbol,
      outerPath: Seq[String],
      innerPath: Seq[String],
      target: Expr[Any],
      curCls: c.universe.Type,
      innerPathOnly: Boolean): c.universe.Tree = {
      val flattenedArgLists = meth.paramss.flatten
      def hasDefault(i: Int) = {
        val defaultName = s"${meth.name}$$default$$${i + 1}"
        if (curCls.members.exists(_.name.toString == defaultName)) {
          Some(defaultName)
        } else {
          None
        }
      }
      val argName = c.fresh[TermName]("args")
      val args: Seq[Tree] = flattenedArgLists.zipWithIndex.map { case (arg, i) =>
        val default = hasDefault(i) match {
          case Some(defaultName) => q"scala.util.Right(($target).${newTermName(defaultName)})"
          case None => q"scala.util.Left(autowire.Error.Param.Missing(${arg.name.toString}))"
        }
        q"""autowire.Internal.read[$pickleType, ${arg.typeSignature}](
                 $argName,
                 $default,
                 ${arg.name.toString},
                 ${c.prefix}.read[${arg.typeSignature}](_)
               )
             """
      }


      //val memSel = c.universe.newTermName(memPath.mkString("."))

      val bindings = args.foldLeft[Tree](q"Nil") { (old, next) =>
        q"$next :: $old"
      }

      val nameNames: Seq[TermName] = flattenedArgLists.map(x => x.name.toTermName)
      val assignment = flattenedArgLists.foldLeft[Tree](q"Nil") { (old, next) =>
        pq"scala.::(${next.name.toTermName}: ${next.typeSignature} @unchecked, $old)"
      }



      val memSel = innerPath.foldLeft(q"($target)") { (cur, nex) =>
        q"$cur.${c.universe.newTermName(nex)}"
      }

      val futurized = futurize(q"$memSel(..$nameNames)", meth)
      val frag = if (innerPathOnly) {
        cq""" autowire.Core.Request(_, Seq(..$innerPath), $argName) =>
             autowire.Internal.doValidate($bindings) match{ case (..$assignment) =>
               $futurized.map(${c.prefix}.write(_))
               case _ => ???
             }
           """
      } else {
        cq""" autowire.Core.Request(Seq(..$outerPath), Seq(..$innerPath), $argName) =>
             autowire.Internal.doValidate($bindings) match{ case (..$assignment) =>
               $futurized.map(${c.prefix}.write(_))
               case _ => ???
             }
           """
      }

      frag
    }

    def getAllRoutesForClass(
      pickleType: WeakTypeTag[_],
      target: Expr[Any],
      curCls: Type,
      outerPath: Seq[String],
      innerPath: Seq[String],
      innerPathOnly: Boolean
    ): Iterable[c.universe.Tree] = {
      //See http://stackoverflow.com/questions/15786917/cant-get-inherited-vals-with-scala-reflection
      //Yep case law to program WUNDERBAR!
      getValsOrMeths(curCls).flatMap {
        case Left((m, t)) => Nil
          //Vals / Vars
          getAllRoutesForClass(
            pickleType,
            target,
            m.typeSignature,
            outerPath,
            innerPath :+ m.name.toString,
            innerPathOnly = innerPathOnly)
        case Right((m, t)) =>
          //Methods
          Seq(extractMethod(
            pickleType,
            t,
            outerPath,
            innerPath :+ m.name.toString,
            target,
            curCls,
            innerPathOnly = innerPathOnly))

      }
    }

  }


  def clientMacro[Result]
    (c: Context)
      ()
      (implicit r: c.WeakTypeTag[Result])
  : c.Expr[Future[Result]] = {

    import c.universe._
    object Pkg {
      def unapply(t: Tree): Option[Tree] = {
        if (Seq("autowire.this", "autowire").contains(t.toString())) Some(t)
        else None
      }
    }
    val res = for {
      q"${Pkg(_)}.`package`.$callableName[$t]($contents)" <- Win(c.prefix.tree,
        "You can only use .call() on the Proxy returned by autowire.Client.apply, not " + c.prefix.tree
      )
      if Seq("clientFutureCallable", "clientCallable").contains(callableName.toString)
      // If the tree is one of those default-argument containing blocks or
      // functions, pry it apart such that the main logic can operate on the
      // inner tree, and leave instructions on how
      (unwrapTree: Tree, methodName: TermName, args: Seq[Tree], prelude: Seq[Tree], deadNames: Seq[String]@unchecked) = (contents: Tree) match {
        case x@q"$unwrapTree.$methodName(..$args)" =>
          //Normal tree
          (unwrapTree, methodName, args, Nil, Nil)
        case t@q"..${statements: List[ValDef]@unchecked}; $thing.$call(..$args)"
          if statements.forall(_.isInstanceOf[ValDef]) =>
          //Default argument tree


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

      (oTree, innerPath) = {
        def getMempath(tree: Tree, path: List[String]): (Tree, List[String]) = {
          tree match {
            case Select(t, n) =>
              getMempath(t, n.toTermName.toString :: path)
            case _ =>
              tree -> path
          }
        }

        getMempath(unwrapTree, List(methodName.toString))
      }

      q"${Pkg(_)}.`package`.unwrapClientProxy[$trt, $pt, $rb, $wb]($proxy)" <- Win(oTree,
        s"XX You can't call the .call() method on $contents, only on autowired function calls"
      )

      trtTpe: Type = trt.tpe

      outerPath =
      trtTpe
        .widen
        .typeSymbol
        .fullName
        .toString
        .split('.')
        .toSeq

      method = {
        // Look for method in the trait and in its base classes.
        def findMember(tpe: Type, name: TermName): Option[Symbol] = {
          (Iterator.single(tpe.declaration(name)) ++
            tpe.baseClasses.iterator.map(_.asClass.toType.declaration(name)))
            .find(_ != NoSymbol)
        }

        def loop(path: List[String], tpe: Type, lastMem: Option[Symbol]): MethodSymbol = {
          path match {
            case Nil =>
              lastMem match {
                case Some(mem) if mem.isMethod => mem.asMethod
                case Some(mem) => c.abort(c.enclosingPosition, s"Error while creating route proxy, expect method, $mem in $tpe")
                case None => c.abort(c.enclosingPosition, s"Error while creating route proxy, expect missing member in $tpe")
              }
            case name :: rem =>
              findMember(tpe, newTermName(name)) match {
                case None =>
                  c.abort(c.enclosingPosition, s"Error while creating route proxy, unable to find $name in $tpe")
                case Some(mem) =>
                  val memTpe = mem.typeSignature.widen
                  loop(rem, memTpe, Some(mem))
              }
          }
        }

        loop(innerPath, trtTpe, None)
      }

      pickled = args
        .zip(method.paramss.flatten)
        .filter {
        case (Ident(name: TermName), _) => !deadNames.contains(name)
        case (q"$thing.$name", _) if name.toString.contains("$default$") => false
        case _ => true
      }
        .map { case (t, param: Symbol) => q"${param.name.toString} -> $proxy.self.write($t)"}

    } yield {
      q"""{
        ..$prelude;
        $proxy.self.doCall(
          autowire.Core.Request(Seq(..$outerPath), Seq(..$innerPath), Map(..$pickled))
        ).map($proxy.self.read[${r}](_))
      }"""
    }

    res match {
      case Win(tree, s) => c.Expr[Future[Result]](tree)
      case Luz(s) => c.abort(c.enclosingPosition, s)
    }
  }

  private def routeMacroCommon[Trait, PickleType, C <: Context]
    (c: C)
      (target: c.Expr[Trait], innerPathOnly: Boolean)
      (implicit t: c.WeakTypeTag[Trait], pt: c.WeakTypeTag[PickleType])
  : c.Expr[Router[PickleType]] = {
    import c.universe._
    val help = new MacroHelp[c.type](c)
    val topClass = weakTypeOf[Trait]
    val routes = help.getAllRoutesForClass(
      pt,
      target,
      topClass,
      outerPath = topClass.typeSymbol.fullName.toString.split('.').toSeq,
      innerPath = Nil,
      innerPathOnly = innerPathOnly
    ).toList

    val res = q"{case ..$routes}: autowire.Core.Router[$pt]"
    //    println("RES", res)
    c.Expr(res)
  }

  def routeMacro[Trait, PickleType]
    (c: Context)
      (target: c.Expr[Trait])
      (implicit t: c.WeakTypeTag[Trait], pt: c.WeakTypeTag[PickleType])
  : c.Expr[Router[PickleType]] = {
    routeMacroCommon[Trait, PickleType, c.type](c)(target, innerPathOnly = false)
  }

  def innerRouteMacro[Trait, PickleType]
    (c: Context)
      (target: c.Expr[Trait])
      (implicit t: c.WeakTypeTag[Trait], pt: c.WeakTypeTag[PickleType])
  : c.Expr[Router[PickleType]] = {
    routeMacroCommon[Trait, PickleType, c.type](c)(target, innerPathOnly = true)
  }


}

