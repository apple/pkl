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
import java.nio.file.Path
import kotlin.io.path.writeText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pkl.core.Loggers
import org.pkl.core.SecurityManagers
import org.pkl.core.StackFrameTransformers
import org.pkl.core.TypeParameter
import org.pkl.core.evaluatorSettings.TraceMode
import org.pkl.core.http.HttpClient
import org.pkl.core.module.ModuleKey
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.module.ModuleKeys.file
import org.pkl.core.module.ModuleKeys.synthetic

class VmTypeTest {

  companion object {
    val stringClass = VmType.ClassType(BaseModule.getStringClass())
    val boolClass = VmType.ClassType(BaseModule.getBooleanClass())
    val intClass = VmType.ClassType(BaseModule.getIntClass())
    val numberClass = VmType.ClassType(BaseModule.getNumberClass())
  }

  private fun makeModule(rootDir: Path?, moduleKey: ModuleKey): VmTyped {
    val securityManager =
      SecurityManagers.standard(
        SecurityManagers.defaultAllowedModules,
        SecurityManagers.defaultAllowedResources,
        SecurityManagers.defaultTrustLevels,
        rootDir,
      )
    var ret: VmTyped? = null
    VmUtils.createContext {
        val vmContext = VmContext.get(null)
        vmContext.initialize(
          VmContext.Holder(
            StackFrameTransformers.defaultTransformer,
            securityManager,
            HttpClient.dummyClient(),
            ModuleResolver(
              listOfNotNull(
                ModuleKeyFactories.standardLibrary,
                rootDir?.let { ModuleKeyFactories.file },
              )
            ),
            ResourceManager(securityManager, listOf()),
            Loggers.noop(),
            mapOf(),
            mapOf(),
            null,
            null,
            null,
            null,
            TraceMode.COMPACT,
            false,
          )
        )
        ret = VmLanguage.get(null).loadModule(moduleKey)
      }
      .close()
    return ret!!
  }

  private fun makeModule(text: String): VmTyped =
    makeModule(null, synthetic(URI("repl:text"), text))

  private fun makeModule(dir: Path, path: Path): VmTyped = makeModule(dir, file(path.toUri()))

  private fun VmTyped.getTypeForProperty(property: String): VmType =
    vmClass.getProperty(Identifier.get(property))!!.typeNode!!.typeNode.type

  private fun VmTyped.getTypeForNestedProperty(prop1: String, prop2: String): VmType =
    vmClass
      .getProperty(Identifier.get(prop1))!!
      .typeNode!!
      .typeNode
      .type
      .vmClass!!
      .getProperty(Identifier.get(prop2))!!
      .typeNode!!
      .typeNode
      .type

  /** Assert this is a subtype of [other] and [other] is a supertype of this, but not equivalent */
  private fun VmType.sub(other: VmType) {
    assertThat(this.isSubtypeOf(other)).isTrue
    assertThat(other.isSupertypeOf(this)).isTrue
    assertThat(other.isSubtypeOf(this)).isFalse
    assertThat(this.isSupertypeOf(other)).isFalse
    assertThat(this.equals(other)).isFalse
    assertThat(other.equals(this)).isFalse
  }

  /**
   * Assert this is a subtype of [other] and [other] is a supertype of this and they are equivalent
   */
  private fun VmType.eq(other: VmType) {
    assertThat(this.isSubtypeOf(other)).isTrue
    assertThat(other.isSupertypeOf(this)).isTrue
    assertThat(other.isSubtypeOf(this)).isTrue
    assertThat(this.isSupertypeOf(other)).isTrue
    assertThat(this.equals(other)).isTrue
    assertThat(other.equals(this)).isTrue
  }

  /** Assert this is a subtype of [other] and [other] is a subtype of this, but not equivalent */
  private fun VmType.bidi(other: VmType) {
    assertThat(this.isSubtypeOf(other)).isTrue
    assertThat(other.isSupertypeOf(this)).isTrue
    assertThat(other.isSubtypeOf(this)).isTrue
    assertThat(this.isSupertypeOf(other)).isTrue
    assertThat(this.equals(other)).isFalse
    assertThat(other.equals(this)).isFalse
  }

  @Test
  fun `unknown - equality (referential)`() {
    VmType.UnknownType.INSTANCE.eq(VmType.UnknownType.INSTANCE)
  }

  @Test
  fun `unknown - subtype and supertype of everything`() {
    VmType.UnknownType.INSTANCE.bidi(VmType.ClassType(BaseModule.getStringClass()))
  }

  @Test
  fun `nothing - equality (referential)`() {
    VmType.NothingType.INSTANCE.eq(VmType.NothingType.INSTANCE)
  }

  @Test
  fun `nothing - subtype of everything`() {
    VmType.NothingType.INSTANCE.sub(VmType.ClassType(BaseModule.getStringClass()))
    VmType.NothingType.INSTANCE.sub(VmType.StringLiteralType("foo"))
  }

  @Test
  fun `final self - equality`(@TempDir tempDir: Path) {
    val modPath = tempDir.resolve("test.pkl")
    modPath.writeText(
      """
      module mod
      import "test.pkl" as Declared

      hidden moduleType: module
      hidden moduleType2: module
      hidden thisType: this
      hidden thisType2: this
      hidden declaredType: Declared
      """
        .trimIndent()
    )
    val mod = makeModule(tempDir, modPath)

    assertThat(mod.vmClass.isOpen).isFalse
    mod.getTypeForProperty("moduleType").eq(mod.getTypeForProperty("moduleType2"))
    mod.getTypeForProperty("moduleType").eq(mod.getTypeForProperty("thisType"))
    mod.getTypeForProperty("moduleType").eq(mod.getTypeForProperty("declaredType"))
    mod.getTypeForProperty("thisType").eq(mod.getTypeForProperty("thisType2"))
    mod.getTypeForProperty("thisType").eq(mod.getTypeForProperty("declaredType"))
  }

  @Test
  fun `non-final self - equality`() {
    val mod =
      makeModule(
        """
        open module mod

        hidden moduleType: module
        hidden moduleType2: module
        hidden thisType: this
        hidden thisType2: this
        """
          .trimIndent()
      )

    assertThat(mod.vmClass.isOpen).isTrue
    mod.getTypeForProperty("moduleType").eq(mod.getTypeForProperty("moduleType2"))
    mod.getTypeForProperty("moduleType").eq(mod.getTypeForProperty("thisType"))
    mod.getTypeForProperty("thisType").eq(mod.getTypeForProperty("thisType2"))
  }

  @Test
  fun `non-final self - comparison to extended type`(@TempDir tempDir: Path) {
    val parentPath = tempDir.resolve("parent.pkl")
    parentPath.writeText(
      """
      open module parent

      moduleType: module
      thisType: this
      """
        .trimIndent()
    )

    val modPath = tempDir.resolve("test.pkl")
    modPath.writeText(
      """
      open module mod
      extends "parent.pkl"
      import "parent.pkl" as Parent

      parent: Parent
      child: Child

      moduleType2: module
      thisType2: this

      open class Child extends module {
        // no moduleType3 because module type usage in class bodies is deprecated
        thisType3: this
      }
      """
        .trimIndent()
    )

    val mod = makeModule(tempDir, modPath)

    mod.getTypeForProperty("moduleType").eq(mod.getTypeForNestedProperty("parent", "moduleType"))
    mod.getTypeForProperty("thisType").eq(mod.getTypeForNestedProperty("parent", "moduleType"))
    mod.getTypeForProperty("moduleType").eq(mod.getTypeForNestedProperty("parent", "thisType"))
    mod.getTypeForProperty("thisType").eq(mod.getTypeForNestedProperty("parent", "thisType"))
    mod.getTypeForProperty("moduleType2").sub(mod.getTypeForNestedProperty("parent", "moduleType"))
    mod.getTypeForProperty("thisType2").sub(mod.getTypeForNestedProperty("parent", "moduleType"))
    mod.getTypeForProperty("moduleType2").sub(mod.getTypeForNestedProperty("parent", "thisType"))
    mod.getTypeForProperty("thisType2").sub(mod.getTypeForNestedProperty("parent", "thisType"))

    mod
      .getTypeForNestedProperty("child", "moduleType")
      .eq(mod.getTypeForNestedProperty("parent", "moduleType"))
    mod
      .getTypeForNestedProperty("child", "thisType")
      .eq(mod.getTypeForNestedProperty("parent", "moduleType"))
    mod
      .getTypeForNestedProperty("child", "moduleType")
      .eq(mod.getTypeForNestedProperty("parent", "thisType"))
    mod
      .getTypeForNestedProperty("child", "thisType")
      .eq(mod.getTypeForNestedProperty("parent", "thisType"))
    mod
      .getTypeForNestedProperty("child", "thisType3")
      .sub(mod.getTypeForNestedProperty("parent", "moduleType"))
    mod
      .getTypeForNestedProperty("child", "thisType3")
      .sub(mod.getTypeForNestedProperty("parent", "thisType"))

    mod.getTypeForNestedProperty("child", "moduleType").eq(mod.getTypeForProperty("moduleType"))
    mod.getTypeForNestedProperty("child", "thisType").eq(mod.getTypeForProperty("moduleType"))
    mod.getTypeForNestedProperty("child", "moduleType").eq(mod.getTypeForProperty("thisType"))
    mod.getTypeForNestedProperty("child", "thisType").eq(mod.getTypeForProperty("thisType"))
    mod.getTypeForNestedProperty("child", "thisType3").sub(mod.getTypeForProperty("moduleType2"))
    mod.getTypeForNestedProperty("child", "thisType3").sub(mod.getTypeForProperty("thisType2"))

    // non-final self type subtype of declared type that is superclass of self type's "base"
    // class types are never subtypes of non-final self types
    mod.getTypeForProperty("moduleType").sub(mod.getTypeForProperty("parent"))
    mod.getTypeForProperty("thisType").sub(mod.getTypeForProperty("parent"))
    mod.getTypeForProperty("moduleType2").sub(mod.getTypeForProperty("parent"))
    mod.getTypeForProperty("thisType2").sub(mod.getTypeForProperty("parent"))
    mod.getTypeForNestedProperty("child", "thisType3").sub(mod.getTypeForProperty("parent"))
  }

  @Test
  fun `string literal - equality`() {
    VmType.StringLiteralType("foo").eq(VmType.StringLiteralType("foo"))
  }

  @Test
  fun `string literal - subtype of String`() {
    VmType.StringLiteralType("foo").sub(VmType.ClassType(BaseModule.getStringClass()))
  }

  @Test
  fun `class - equality, unparameterized`() {
    VmType.ClassType(BaseModule.getBooleanClass())
      .eq(VmType.ClassType(BaseModule.getBooleanClass()))
  }

  @Test
  fun `class - equality, unparameterized generic`() {
    VmType.ClassType(BaseModule.getListClass()).eq(VmType.ClassType(BaseModule.getListClass()))
  }

  @Test
  fun `class - equality, parameterized generic`() {
    VmType.ClassType(BaseModule.getMappingClass(), stringClass, boolClass)
      .eq(VmType.ClassType(BaseModule.getMappingClass(), stringClass, boolClass))
  }

  @Test
  fun `class - subtype, unparameterized generic`() {
    VmType.ClassType(BaseModule.getListClass())
      .sub(VmType.ClassType(BaseModule.getCollectionClass()))
  }

  @Test
  fun `class - subtype of any`() {
    VmType.ClassType(BaseModule.getListClass()).sub(VmType.ClassType(BaseModule.getAnyClass()))
    VmType.ClassType(BaseModule.getListClass(), stringClass)
      .sub(VmType.ClassType(BaseModule.getAnyClass()))
  }

  @Test
  fun `class - subtype of unparameterized`() {
    VmType.ClassType(BaseModule.getListClass(), stringClass)
      .sub(VmType.ClassType(BaseModule.getListClass()))
  }

  @Test
  fun `class - subtype, generic with substitution`() {
    VmType.ClassType(BaseModule.getFunction1Class(), stringClass, boolClass)
      .sub(VmType.ClassType(BaseModule.getFunctionClass(), boolClass))
    VmType.ClassType(BaseModule.getFunction1Class(), stringClass, intClass)
      .sub(VmType.ClassType(BaseModule.getFunctionClass(), numberClass))
  }

  @Test
  fun `class - special cases`() {
    VmType.AliasType(BaseModule.getCharTypeAlias()).sub(stringClass)
    for (cls in listOf(intClass, numberClass)) {
      VmType.AliasType(BaseModule.getInt8TypeAlias()).sub(cls)
      VmType.AliasType(BaseModule.getInt16TypeAlias()).sub(cls)
      VmType.AliasType(BaseModule.getInt32TypeAlias()).sub(cls)
      VmType.AliasType(BaseModule.getUIntTypeAlias()).sub(cls)
      VmType.AliasType(BaseModule.getUInt8TypeAlias()).sub(cls)
      VmType.AliasType(BaseModule.getUInt16TypeAlias()).sub(cls)
      VmType.AliasType(BaseModule.getUInt32TypeAlias()).sub(cls)
    }
  }

  @Test
  fun `nullable - class is subtype of nullable class`() {
    stringClass.sub(VmType.NullableType(stringClass))
    intClass.sub(VmType.NullableType(numberClass))
  }

  @Test
  fun `nullable - subtype of nullable supertype`() {
    VmType.NullableType(intClass).sub(VmType.NullableType(numberClass))
  }

  @Test
  fun `constrained - equality (identity)`() {
    VmType.ConstrainedType(stringClass, arrayOf("true"), 0)
      .eq(VmType.ConstrainedType(stringClass, arrayOf("true"), 0))
  }

  @Test
  fun `constrained - subtype when base type is supertype`() {
    VmType.ConstrainedType(stringClass, arrayOf("true"), 0).sub(stringClass)
    VmType.ConstrainedType(intClass, arrayOf("true"), 0).sub(numberClass)
  }

  @Test
  fun `alias - equality`() {
    val mod =
      makeModule(
        """
        typealias A = Int
        typealias B = A
        typealias C = B

        int: Int
        a: A
        b: B
        c: C
        """
          .trimIndent()
      )

    mod.getTypeForProperty("int").eq(mod.getTypeForProperty("a"))
    mod.getTypeForProperty("int").eq(mod.getTypeForProperty("b"))
    mod.getTypeForProperty("int").eq(mod.getTypeForProperty("c"))
    mod.getTypeForProperty("a").eq(mod.getTypeForProperty("b"))
    mod.getTypeForProperty("a").eq(mod.getTypeForProperty("c"))
    intClass.eq(mod.getTypeForProperty("b"))
  }

  @Test
  fun `alias - subtype`() {
    val mod =
      makeModule(
        """
        typealias A = Int
        typealias B = A
        typealias C = B

        int: Int
        a: A
        b: B
        c: C
        """
          .trimIndent()
      )

    mod.getTypeForProperty("c").sub(numberClass)
  }

  @Test
  fun `union - equality (ignores default index)`() {
    VmType.UnionType(-1, arrayOf("foo", "bar", "baz"))
      .eq(VmType.UnionType(0, arrayOf("foo", "bar", "baz")))
  }

  @Test
  @Disabled("TODO: support order-independent union type equivalence")
  fun `union - equality, order-independent`() {
    VmType.UnionType(-1, arrayOf("foo", "bar", "baz"))
      .eq(VmType.UnionType(-1, arrayOf("bar", "baz", "foo")))
  }

  @Test
  fun `union - subtype when all elements are subtype`() {
    VmType.UnionType(-1, arrayOf("foo", "bar", "baz")).sub(stringClass)
  }

  @Test
  fun `union - subtype of union when all elements are contained in supertype`() {
    VmType.UnionType(-1, arrayOf("foo", "bar", "baz"))
      .sub(VmType.UnionType(-1, arrayOf("foo", "bar", "baz", "qux")))
  }

  @Test
  fun `type variable - treated like unknown, supertype and subtype to all`() {
    VmType.TypeVariableType(VmTypeParameter(TypeParameter.Variance.INVARIANT, "Foo", 0))
      .bidi(stringClass)
  }
}
