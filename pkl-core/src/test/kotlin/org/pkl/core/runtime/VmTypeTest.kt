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
package org.pkl.core.runtime

import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.pkl.core.PClassInfo
import org.pkl.core.TypeParameter
import org.pkl.core.ast.VmModifier
import org.pkl.core.ast.type.TypeNode
import org.pkl.core.ast.type.TypeNodeFactory

class VmTypeTest {

  class SubtypeCase(
    private val name: String,
    val first: VmType,
    val second: VmType,
    val bidi: Boolean = false,
  ) {
    // for nice test output
    override fun toString(): String = "$name: ($first ${if (bidi) "<->" else "->"} $second)"
  }

  companion object {
    @JvmStatic
    val subtypeCases by lazy {
      val boolClass = VmType.ClassType(BaseModule.getBooleanClass())
      val stringClass = VmType.ClassType(BaseModule.getStringClass())
      val anyClass = VmType.ClassType(BaseModule.getAnyClass())
      val intClass = VmType.ClassType(BaseModule.getIntClass())
      val numberClass = VmType.ClassType(BaseModule.getNumberClass())
      val typeVar =
        VmType.TypeVariableType(TypeParameter(TypeParameter.Variance.INVARIANT, "Foo", 0))

      val modA =
        VmClass(
          VmUtils.unavailableSourceSection(),
          VmUtils.unavailableSourceSection(),
          null,
          emptyList(),
          VmModifier.OPEN,
          PClassInfo.forModuleClass("modA", URI("test:/modA.pkl")),
          emptyList(),
          VmUtils.createEmptyModule(),
        )
      modA.initSupertype(
        TypeNode.AnyTypeNode(VmUtils.unavailableSourceSection()),
        BaseModule.getAnyClass(),
      )
      val modB =
        VmClass(
          VmUtils.unavailableSourceSection(),
          VmUtils.unavailableSourceSection(),
          null,
          emptyList(),
          VmModifier.NONE,
          PClassInfo.forModuleClass("modB", URI("test:/modB.pkl")),
          emptyList(),
          VmUtils.createEmptyModule(),
        )
      modB.initSupertype(
        TypeNodeFactory.NonFinalClassTypeNodeGen.create(VmUtils.unavailableSourceSection(), modA),
        modA,
      )

      val aliasA =
        VmTypeAlias(
          VmUtils.unavailableSourceSection(),
          VmUtils.unavailableSourceSection(),
          null,
          VmModifier.NONE,
          emptyList(),
          "AliasA",
          modB.prototype,
          "modB#AliasA",
          emptyList(),
          VmUtils.createEmptyMaterializedFrame(),
        )
      aliasA.initTypeCheckNode(TypeNode.IntTypeNode(VmUtils.unavailableSourceSection()))
      val aliasB =
        VmTypeAlias(
          VmUtils.unavailableSourceSection(),
          VmUtils.unavailableSourceSection(),
          null,
          VmModifier.NONE,
          emptyList(),
          "AliasB",
          modB.prototype,
          "modB#AliasB",
          emptyList(),
          VmUtils.createEmptyMaterializedFrame(),
        )
      aliasB.initTypeCheckNode(TypeNode.NumberTypeNode(VmUtils.unavailableSourceSection()))

      listOf(
        // unknown
        SubtypeCase(
          "unknown: equality (referential)",
          VmType.UnknownType.INSTANCE,
          VmType.UnknownType.INSTANCE,
          bidi = true,
        ),
        SubtypeCase(
          "unknown: subtype and supertype of everything",
          VmType.UnknownType.INSTANCE,
          stringClass,
          bidi = true,
        ),

        // nothing
        SubtypeCase(
          "nothing: equality (referential)",
          VmType.NothingType.INSTANCE,
          VmType.NothingType.INSTANCE,
          bidi = true,
        ),
        SubtypeCase("nothing: subtype of everything", VmType.NothingType.INSTANCE, stringClass),
        SubtypeCase("nothing: subtype of everything", VmType.NothingType.INSTANCE, boolClass),

        // module
        SubtypeCase(
          "module: equality (semantic)",
          VmType.ModuleType(modA),
          VmType.ModuleType(modA),
          bidi = true,
        ),
        SubtypeCase(
          "module: equality to this (semantic)",
          VmType.ModuleType(modA),
          VmType.ThisType(modA),
          bidi = true,
        ),
        SubtypeCase(
          "module: subtype to module of superclass",
          VmType.ModuleType(modB),
          VmType.ModuleType(modA),
        ),
        SubtypeCase(
          "module: subtype to this of superclass",
          VmType.ModuleType(modB),
          VmType.ThisType(modA),
        ),

        // this
        SubtypeCase(
          "this: equality (semantic)",
          VmType.ThisType(modA),
          VmType.ThisType(modA),
          bidi = true,
        ),
        SubtypeCase(
          "this: equality to module (semantic)",
          VmType.ThisType(modA),
          VmType.ModuleType(modA),
          bidi = true,
        ),
        SubtypeCase(
          "this: subtype to this of superclass",
          VmType.ThisType(modB),
          VmType.ThisType(modA),
        ),
        SubtypeCase(
          "this: subtype to module of superclass",
          VmType.ThisType(modB),
          VmType.ModuleType(modA),
        ),

        // string literal
        SubtypeCase(
          "string literal: equality (semantic)",
          VmType.StringLiteralType("foo"),
          VmType.StringLiteralType("foo"),
          bidi = true,
        ),
        SubtypeCase(
          "string literal: subtype of String",
          VmType.StringLiteralType("foo"),
          stringClass,
        ),

        // class
        SubtypeCase(
          "class: equality (semantic, unparameterized)",
          boolClass,
          VmType.ClassType(BaseModule.getBooleanClass()),
          bidi = true,
        ),
        SubtypeCase(
          "class: equality (semantic, parameterized)",
          VmType.ClassType(BaseModule.getMappingClass(), stringClass, boolClass),
          VmType.ClassType(BaseModule.getMappingClass(), stringClass, boolClass),
          bidi = true,
        ),
        SubtypeCase(
          "class: subtype (unparameterized)",
          VmType.ClassType(BaseModule.getListClass()),
          VmType.ClassType(BaseModule.getCollectionClass()),
        ),
        SubtypeCase(
          "class: subtype of Any (unparameterized)",
          VmType.ClassType(BaseModule.getListClass()),
          anyClass,
        ),
        SubtypeCase(
          "class: subtype (parameterized, direct inheritance)",
          VmType.ClassType(BaseModule.getListClass()),
          VmType.ClassType(BaseModule.getCollectionClass()),
        ),
        SubtypeCase(
          "class: subtype (parameterized vs. unparametetized)",
          VmType.ClassType(BaseModule.getListClass(), stringClass),
          VmType.ClassType(BaseModule.getListClass()),
        ),
        SubtypeCase(
          "class: subtype of Any (parameterized)",
          VmType.ClassType(BaseModule.getListClass()),
          anyClass,
        ),
        SubtypeCase(
          "class: subtype (parameterized, type variable substitution)",
          VmType.ClassType(BaseModule.getFunction1Class(), stringClass, boolClass),
          VmType.ClassType(BaseModule.getFunctionClass(), boolClass),
        ),
        SubtypeCase(
          "class: subtype (parameterized, type variable substitution, variance)",
          VmType.ClassType(BaseModule.getFunction1Class(), stringClass, intClass),
          VmType.ClassType(BaseModule.getFunctionClass(), numberClass),
        ),
        SubtypeCase(
          "class: Char is a subtype of String",
          VmType.AliasType(BaseModule.getCharTypeAlias()),
          stringClass,
        ),
        SubtypeCase(
          "class: Int alias is a subtype of Int",
          VmType.AliasType(BaseModule.getInt8TypeAlias(), BaseModule.getIntClass()),
          intClass,
        ),
        SubtypeCase(
          "class: Int alias is a subtype of Int",
          VmType.AliasType(BaseModule.getInt16TypeAlias(), BaseModule.getIntClass()),
          intClass,
        ),
        SubtypeCase(
          "class: Int alias is a subtype of Int",
          VmType.AliasType(BaseModule.getInt32TypeAlias(), BaseModule.getIntClass()),
          intClass,
        ),
        SubtypeCase(
          "class: Int alias is a subtype of Int",
          VmType.AliasType(BaseModule.getUIntTypeAlias(), BaseModule.getIntClass()),
          intClass,
        ),
        SubtypeCase(
          "class: Int alias is a subtype of Int",
          VmType.AliasType(BaseModule.getUInt8TypeAlias(), BaseModule.getIntClass()),
          intClass,
        ),
        SubtypeCase(
          "class: Int alias is a subtype of Int",
          VmType.AliasType(BaseModule.getUInt16TypeAlias(), BaseModule.getIntClass()),
          intClass,
        ),
        SubtypeCase(
          "class: Int alias is a subtype of Int",
          VmType.AliasType(BaseModule.getUInt32TypeAlias(), BaseModule.getIntClass()),
          intClass,
        ),

        // nullable
        SubtypeCase(
          "nullable: class is subtype of nullable class",
          stringClass,
          VmType.NullableType(stringClass),
        ),
        SubtypeCase(
          "nullable: nullable class is subtype of nullable supertype",
          VmType.NullableType(intClass),
          VmType.NullableType(numberClass),
        ),

        // constrained
        SubtypeCase(
          "constained: equality (identity)",
          VmType.ConstrainedType(stringClass, arrayOf("true"), 0),
          VmType.ConstrainedType(stringClass, arrayOf("true"), 0),
          bidi = true,
        ),
        SubtypeCase(
          "constrained: subtype when base type is a subtype",
          VmType.ConstrainedType(stringClass, arrayOf("true"), 0),
          stringClass,
        ),

        // alias
        SubtypeCase("alias: equality (unaliased)", VmType.AliasType(aliasA), intClass, bidi = true),
        SubtypeCase(
          "alias: equality (aliased)",
          VmType.AliasType(aliasA),
          VmType.AliasType(aliasA),
          bidi = true,
        ),
        SubtypeCase(
          "alias: subtype of plain class (aliased)",
          VmType.AliasType(aliasA),
          numberClass,
        ),
        SubtypeCase("alias: subtype of plain class (aliased)", intClass, VmType.AliasType(aliasB)),

        // union
        SubtypeCase(
          "union: equality (ignores default index)",
          VmType.UnionType(-1, arrayOf("foo", "bar", "baz")),
          VmType.UnionType(0, arrayOf("foo", "bar", "baz")),
          bidi = true,
        ),
        //        SubtypeCase(
        //          "union: equality (ignores order)", // TODO
        //          VmType.UnionType(-1, arrayOf("foo", "bar", "baz")),
        //          VmType.UnionType(-1, arrayOf("bar", "baz", "foo")),
        //          bidi = true,
        //        ),
        SubtypeCase(
          "union: subtype when all elements are subtype",
          VmType.UnionType(-1, arrayOf("foo", "bar", "baz")),
          stringClass,
        ),
        SubtypeCase(
          "union: subtype of union when all elements are contained in supertype",
          VmType.UnionType(-1, arrayOf("foo", "bar", "baz")),
          VmType.UnionType(-1, arrayOf("foo", "bar", "baz", "qux")),
        ),

        // type variable
        SubtypeCase(
          "type variable: treated like unknown, supertype and subtype to all",
          boolClass,
          typeVar,
          bidi = true,
        ),
      )
    }
  }

  @ParameterizedTest
  @MethodSource("getSubtypeCases")
  fun `subtype checking`(case: SubtypeCase) {
    assertThat(case.first.isSubtypeOf(case.second)).isTrue
    assertThat(case.second.isSupertypeOf(case.first)).isTrue
    assertThat(case.first.isSupertypeOf(case.second)).isEqualTo(case.bidi)
    assertThat(case.second.isSubtypeOf(case.first)).isEqualTo(case.bidi)
  }
}
