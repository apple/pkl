/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.lsp.ast

import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.pkl.core.parser.Parser
import org.pkl.lsp.StdlibFile

class AstTest {
  @Test
  fun testAst() {
    val mod =
      """
      /// This is a module.
      ///
      /// This is another line of doc comment.
      abstract module Foo

      amends "bar.pkl"

      import "foo.pkl"

      hidden foo: String

      function bar() = "bar"

      open class Person {
        /// Some comment
        name: String

        /// Some comment
        local function toUpperCase(foo: String): String
      }
    """
        .trimIndent()
    val parser = Parser()
    val module = PklModuleImpl(parser.parseModule(mod), URI("my:test"), StdlibFile("file"))
    assertThat(module.declaration).isNotNull
    assertThat(module.declaration!!.docComment!!.text)
      .isEqualTo(
        """
        /// This is a module.
        ///
        /// This is another line of doc comment.
        
      """
          .trimIndent()
      )
    assertThat(module.declaration?.modifiers?.map { it.type }).isEqualTo(listOf(TokenType.ABSTRACT))
    assertThat(module.declaration?.effectiveExtendsOrAmendsCluse)
    assertThat(module.members).hasSize(3)
    assertThat(module.members[0]).isInstanceOf(PklClassProperty::class.java)
    val property = module.members[0] as PklClassProperty
    assertThat(property.identifier?.text).isEqualTo("foo")
    assertThat(property.typeAnnotation?.type).isInstanceOf(PklDeclaredType::class.java)
    assertThat(module.members[1]).isInstanceOf(PklClassMethod::class.java)
    val method = module.members[1] as PklClassMethod
    assertThat(method.methodHeader.identifier?.text).isEqualTo("bar")
    assertThat(module.members[2]).isInstanceOf(PklClass::class.java)
    val clazz = module.members[2] as PklClass
    val classMembers = clazz.classBody?.members
    assertThat(classMembers).hasSize(2)
    val classProperty = classMembers?.get(0)
    assertThat(classProperty).isInstanceOf(PklClassProperty::class.java)
    classProperty as PklClassProperty
    assertThat(classProperty.identifier?.text).isEqualTo("name")
    assertThat(classProperty.docComment?.text).isEqualTo("  /// Some comment\n")
    val classMethod = classMembers[1]
    assertThat(classMethod).isInstanceOf(PklClassMethod::class.java)
    classMethod as PklClassMethod
    assertThat(classMethod.methodHeader.identifier?.text).isEqualTo("toUpperCase")
    assertThat(classMethod.methodHeader.modifiers?.map { it.type })
      .isEqualTo(listOf(TokenType.LOCAL))
  }
}
