/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.config.scala.mapper

import izumi.reflect.Tag
import izumi.reflect.macrortti.LightTypeTagRef
import izumi.reflect.macrortti.LightTypeTagRef.{
  AbstractReference,
  AppliedReference,
  FullReference,
  NameReference,
  SymName,
  TypeParam,
  UnionReference
}
import org.pkl.config.java.Config
import org.pkl.config.java.mapper.{ConversionException, Types}

import java.lang.reflect.Type as JType

/**
 * Reifies a Scala type captured by `izumi.reflect.Tag` into a `java.lang.reflect.Type` (or a
 * higher-level shape that the underlying `pkl-config-java` mapper can't express directly — union
 * types and singleton-literal types).
 *
 * The motivation is that the prior `ClassTag`-only path on `Config.to[T]` erased generic type
 * arguments, union components, and singleton literals. izumi's `Tag[T]` carries the full
 * `LightTypeTag` (compile-time reified), which we walk here.
 *
 * Three terminal shapes:
 *   - `Reified.Java(t)` — a `java.lang.reflect.Type` the mapper consumes directly via
 *     `Config.as(Type)`. Covers scalars and parameterized types (collections, Option, Either,
 *     tuples, Map, ...).
 *   - `Reified.Singleton(value, base)` — a singleton-literal type like `"hello"`. Convert as the
 *     base, then runtime-check equality; raise `ConversionException` on mismatch.
 *   - `Reified.Union(members)` — a Scala 3 union like `A | B` or `"A" | "B"`. Try each member;
 *     return the first successful conversion. Raise `ConversionException` if none match.
 */
private[scala] object ScalaTypeReifier {

  sealed trait Reified
  object Reified {
    case class Java(t: JType) extends Reified
    case class Singleton(value: Any, base: Reified) extends Reified
    case class Union(members: List[Reified]) extends Reified
  }

  def reify[T](implicit tag: Tag[T]): Reified = fromRef(tag.tag.ref)

  def convert[T](cfg: Config)(implicit tag: Tag[T]): T = {
    convertReified(cfg, reify[T]).asInstanceOf[T]
  }

  private def convertReified(cfg: Config, r: Reified): Any = r match {
    case Reified.Java(t) =>
      cfg.as[Any](t)
    case Reified.Singleton(expected, base) =>
      val actual = convertReified(cfg, base)
      if (actual == expected) actual
      else {
        throw new ConversionException(
          s"Cannot convert value `$actual` to singleton type `$expected`."
        )
      }
    case Reified.Union(members) =>
      tryUnion(cfg, members)
  }

  private def tryUnion(cfg: Config, members: List[Reified]): Any = {
    import scala.util.control.NonFatal
    val errors = List.newBuilder[Throwable]
    def attempt(m: Reified): Option[Any] = {
      try Some(convertReified(cfg, m))
      catch { case NonFatal(e) => errors += e; None }
    }
    members.iterator.flatMap(attempt).nextOption() match {
      case Some(v) => v
      case None    =>
        val msg = errors
          .result()
          .map(e => s"[${e.getClass.getSimpleName}] ${e.getMessage}")
          .mkString("; ")
        throw new ConversionException(
          s"Cannot convert value `${cfg.getRawValue}` to any branch of the union: $msg"
        )
    }
  }

  private def fromRef(ref: LightTypeTagRef): Reified = ref match {
    case u: UnionReference =>
      Reified.Union(u.refs.toList.map(fromRef))
    case n: NameReference =>
      n.ref match {
        case lit: SymName.SymLiteral =>
          val (rawValue, baseClass) = parseLiteral(lit.name)
          Reified.Singleton(rawValue, Reified.Java(baseClass))
        case sym: SymName.NamedSymbol =>
          Reified.Java(loadClass(sym.name))
        case _: SymName.LambdaParamName =>
          throw new ConversionException(s"Lambda parameter type unsupported: $ref")
      }
    case f: FullReference =>
      val name = f.symName match {
        case s: SymName.NamedSymbol => s.name
        case _                      =>
          throw new ConversionException(s"Lambda parameter as type constructor: ${f.symName}")
      }
      val raw = loadClass(name)
      val args = f.parameters.map(paramToJavaType)
      Reified.Java(Types.parameterizedType(raw, args*))
    case other =>
      throw new ConversionException(s"Unsupported type reference: $other")
  }

  /**
   * Convert a parameterized type-arg position to a java.lang.reflect.Type, lifting primitives to
   * their boxed counterparts (Java reflection's parameterized types can't hold primitives).
   */
  private def paramToJavaType(p: TypeParam): JType = {
    fromRef(p.ref) match {
      case Reified.Java(c: Class[?]) if c.isPrimitive                       => box(c)
      case Reified.Java(t)                                                  => t
      case Reified.Singleton(_, Reified.Java(c: Class[?])) if c.isPrimitive => box(c)
      case Reified.Singleton(_, Reified.Java(t))                            => t
      case other                                                            =>
        throw new ConversionException(
          s"Type parameter cannot be expressed as a java.lang.reflect.Type: $other"
        )
    }
  }

  private def box(c: Class[?]): Class[?] = c.getName match {
    case "boolean" => classOf[java.lang.Boolean]
    case "byte"    => classOf[java.lang.Byte]
    case "short"   => classOf[java.lang.Short]
    case "int"     => classOf[java.lang.Integer]
    case "long"    => classOf[java.lang.Long]
    case "float"   => classOf[java.lang.Float]
    case "double"  => classOf[java.lang.Double]
    case "char"    => classOf[java.lang.Character]
    case "void"    => classOf[java.lang.Void]
    case _         => c
  }

  /**
   * SymLiteral stores the *stringified* constant: `"hello"` becomes `"\"hello\""`, `42` becomes
   * `"42"`, `true` becomes `"true"`. Parse it back to a typed value.
   */
  private def parseLiteral(rendered: String): (Any, Class[?]) = {
    if (rendered.length >= 2 && rendered.startsWith("\"") && rendered.endsWith("\"")) {
      (rendered.substring(1, rendered.length - 1), classOf[String])
    } else if (rendered == "true" || rendered == "false") {
      (java.lang.Boolean.valueOf(rendered), classOf[java.lang.Boolean])
    } else {
      try (java.lang.Integer.valueOf(rendered), classOf[java.lang.Integer])
      catch {
        case _: NumberFormatException =>
          try (java.lang.Long.valueOf(rendered), classOf[java.lang.Long])
          catch {
            case _: NumberFormatException =>
              try (java.lang.Double.valueOf(rendered), classOf[java.lang.Double])
              catch {
                case _: NumberFormatException =>
                  throw new ConversionException(s"Unsupported singleton literal: $rendered")
              }
          }
      }
    }
  }

  private def loadClass(name: String): Class[?] = {
    primitiveClasses.get(name) match {
      case Some(c) => c
      case None    => Class.forName(scalaToJavaName(name), false, getClass.getClassLoader)
    }
  }

  private val primitiveClasses: Map[String, Class[?]] = Map(
    "scala.Int" -> classOf[Int],
    "scala.Long" -> classOf[Long],
    "scala.Boolean" -> classOf[Boolean],
    "scala.Double" -> classOf[Double],
    "scala.Float" -> classOf[Float],
    "scala.Char" -> classOf[Char],
    "scala.Byte" -> classOf[Byte],
    "scala.Short" -> classOf[Short],
    "scala.Unit" -> classOf[Unit],
    "scala.Any" -> classOf[Any],
    "scala.AnyRef" -> classOf[AnyRef],
    "scala.AnyVal" -> classOf[AnyVal],
    "scala.Nothing" -> classOf[Nothing]
  )

  private def scalaToJavaName(name: String): String = name match {
    case "scala.Predef.String" => "java.lang.String"
    // Inner types of an `object` companion: izumi renders `Outer$.Inner`; JVM wants `Outer$Inner`.
    case n => n.replace("$.", "$")
  }
}
