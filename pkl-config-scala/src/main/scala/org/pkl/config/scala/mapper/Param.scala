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

import java.lang.reflect.Type as JType

/** A resolved case class ctor parameter. Pure data — no converter, no cache, no runtime state. */
private[mapper] final case class Param(index: Int, name: String, tpe: Param.Type)

private[mapper] object Param {

  /**
   * Describes a case class primary-constructor parameter's type.
   *
   * `Param.Type` makes the enum-ness of a param explicit in the type system, rather than encoding
   * it as an optional aspect of the `Param` descriptor. Everything reachable through `Param.Type`
   * is plain data, so a `Param` remains `println`-friendly.
   */
  private[mapper] sealed trait Type {

    def jvmType: JType

  }
  private[mapper] object Type {

    /**
     * A param whose type carries no Scala-specific structure beyond what Java reflection exposes.
     */
    private[mapper] final case class Jvm(jvmType: JType) extends Type

    /**
     * A param whose declared Scala type is a subtype of some `Enumeration#Value`. Carries the full
     * list of members of the originating `Enumeration`, recovered via Scala runtime reflection.
     */
    private[mapper] final case class ScalaEnum(
        jvmType: JType,
        members: List[Enumeration#Value]
    ) extends Type
  }
}
