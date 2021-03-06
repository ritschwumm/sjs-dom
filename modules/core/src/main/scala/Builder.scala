package sjs.dom.builder

import scala.language.dynamics
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context

object Builder {
	def apply[T]:Builder[T]	= new Builder[T]
}

class Builder[T] private () extends Dynamic {
	def applyDynamic(name:String)(args:Any*):T	=
			macro BuilderMacros.applyDynamic[T]

	def applyDynamicNamed(name:String)(args:(String,Any)*):T	=
			macro BuilderMacros.applyDynamicNamed[T]
}

final class BuilderMacros(val c:Context) {
	import c.universe._

	def applyDynamic[T](name:c.Tree)(args:Tree*)(implicit nodeTypeTag:c.WeakTypeTag[T]):c.Tree	= {
		assertApply(name)
		build[T](elementName, nodeTypeTag, args map { av =>
			// @see http://stackoverflow.com/questions/17394389/infer-a-type-of-a-tree-in-a-scala-macro
			//val at:c.Type	= c.typecheck(av).tpe.widen
			Right(av)
		})
	}

	def applyDynamicNamed[T](name:c.Tree)(args:Tree*)(implicit nodeTypeTag:c.WeakTypeTag[T]):c.Tree	= {
		assertApply(name)
		build[T](elementName, nodeTypeTag, args map {
			case q"scala.Tuple2.apply[$kt,$at]($kv, $av)" =>
				kv match {
					case Literal(Constant(argName:String)) =>
						if (argName == "")	Right(av)
						else				Left(TermName(argName) -> av)
					case x	=>
						abort(s"unexpected applyDynamicNamed syntax: ${x.toString}")
				}
			case x	=>
				abort(s"unexpected applyDynamicNamed syntax: ${x.toString}")
		})
	}

	// make sure the right method has been called
	private def assertApply(name:c.Tree):Unit	=
		name match {
			case Literal(Constant(methodName:String))	=>
				if (methodName != "apply")	abort(s"unhandled applyDynamicNamed call to Builder member '${methodName}'")
			case x	=>
				abort(s"unexpected assertApply syntax: ${x.toString}")
		}

	// find out which element type to build by looking at the val the Builder was assigned to
	private def elementName:String	=
		c.prefix.tree match {
			case Select(_, TermName(elementName))	=> elementName
			case x	=> abort(s"unexpected elementName syntax: ${x.toString}")
		}

	// build element with args of either property or child
	private def build[T](elementName:String, nodeTypeTag:c.WeakTypeTag[T], args:Seq[Either[(TermName,c.Tree),c.Tree]]):c.Tree	= {
		q"""{
			val el	= _root_.org.scalajs.dom.document.createElement($elementName).asInstanceOf[$nodeTypeTag]
			..${
				args.toList map {
					case Left((fieldName,av))	=> q"el.$fieldName = $av"
					case Right(av)				=> q"el.appendChild(_root_.sjs.dom.builder.Fraggable.asNode($av))"
				}
			}
			el
		}"""
	}

	private def abort(msg:String):Nothing	= c.abort(c.enclosingPosition, msg)
}
