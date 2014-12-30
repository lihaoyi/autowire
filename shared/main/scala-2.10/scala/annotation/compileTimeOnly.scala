package scala.annotation
import scala.annotation.meta._
//Needed because 2.10 doesn't have this annotation and we want to use it
@getter @setter @beanGetter @beanSetter @companionClass @companionMethod
final class compileTimeOnly(message: String) extends scala.annotation.StaticAnnotation