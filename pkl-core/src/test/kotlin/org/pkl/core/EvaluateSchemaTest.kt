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
package org.pkl.core

import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.pkl.core.ModuleSource.*
import org.pkl.core.runtime.BaseModule

class EvaluateSchemaTest {
  private val evaluator = Evaluator.preconfigured()

  @AfterEach
  fun afterEach() {
    evaluator.close()
  }

  @Test
  fun `evaluate test schema`() {
    val module = evaluator.evaluateSchema(modulePath("org/pkl/core/EvaluateSchemaTest.pkl"))

    checkModuleMetadata(module)

    checkModuleProperties(module)

    checkModuleMethods(module)

    checkModuleClasses(module)

    checkSupermodule(module)
  }

  @Test
  fun `evaluate pkl_base schema`() {
    val module = evaluator.evaluateSchema(uri(URI("pkl:base")))
    assertThat(module.moduleClass.superclass).isEqualTo(BaseModule.getModuleClass().export())
  }

  @Test
  fun `does not export local classes`() {
    val module =
      evaluator.evaluateSchema(
        text(
          """
        class Foo {}
        local class Baz {}
        """
            .trimIndent()
        )
      )

    assertThat(module.classes.keys).containsExactly("Foo")
  }

  private fun checkModuleMetadata(module: ModuleSchema) {
    assertThat(module.moduleUri).isEqualTo(URI("modulepath:/org/pkl/core/EvaluateSchemaTest.pkl"))

    assertThat(module.moduleName).isEqualTo("test")

    assertThat(module.moduleClass.sourceLocation.startLine).isEqualTo(2)
    assertThat(module.moduleClass.sourceLocation.endLine).isEqualTo(26)
  }

  private fun checkModuleProperties(module: ModuleSchema) {
    val properties = module.moduleClass.properties
    assertThat(properties).hasSize(3)

    val propertyb1 = properties.getValue("propertyb1")
    assertThat(propertyb1.sourceLocation.startLine).isEqualTo(5)
    assertThat(propertyb1.sourceLocation.endLine).isEqualTo(5)
    assertThat(propertyb1.type).isEqualTo(PType.UNKNOWN)

    val propertyb2 = properties.getValue("propertyb2")
    assertThat(propertyb2.sourceLocation.startLine).isEqualTo(8)
    assertThat(propertyb2.sourceLocation.endLine).isEqualTo(9)
    val paramType = propertyb2.type
    assertThat(paramType).isInstanceOf(PType.Class::class.java)
    paramType as PType.Class
    assertThat(paramType.pClass).isEqualTo(BaseModule.getIntClass().export())

    val propertyb3 = properties.getValue("propertyb3")
    assertThat(propertyb3.sourceLocation.startLine).isEqualTo(24)
    assertThat(propertyb3.sourceLocation.endLine).isEqualTo(24)
  }

  private fun checkModuleMethods(module: ModuleSchema) {
    val methods = module.moduleClass.methods
    assertThat(methods).hasSize(3)

    val methodb1 = methods.getValue("methodb1")
    assertThat(methodb1.sourceLocation.startLine).isEqualTo(12)
    assertThat(methodb1.sourceLocation.endLine).isEqualTo(12)
    assertThat(methodb1.parameters).isEmpty()
    assertThat(methodb1.returnType).isEqualTo(PType.UNKNOWN)

    val methodb2 = methods.getValue("methodb2")
    assertThat(methodb2.sourceLocation.startLine).isEqualTo(15)
    assertThat(methodb2.sourceLocation.endLine).isEqualTo(16)
    val paramType = methodb2.parameters.getValue("str")
    assertThat(paramType).isInstanceOf(PType.Constrained::class.java)
    paramType as PType.Constrained
    val paramBaseType = paramType.baseType
    assertThat(paramBaseType).isInstanceOf(PType.Class::class.java)
    paramBaseType as PType.Class
    assertThat(paramBaseType.pClass).isEqualTo(BaseModule.getStringClass().export())
    assertThat(paramType.constraints).isEqualTo(listOf("!isEmpty", "startsWith(\"a\")"))

    val returnType = methodb2.returnType
    assertThat(returnType).isInstanceOf(PType.Constrained::class.java)
    returnType as PType.Constrained
    val returnBaseType = returnType.baseType
    assertThat(returnBaseType).isInstanceOf(PType.Class::class.java)
    returnBaseType as PType.Class
    assertThat(returnBaseType.pClass).isEqualTo(BaseModule.getIntClass().export())
    assertThat(returnType.constraints).isEqualTo(listOf("isPositive"))

    val methodb3 = methods.getValue("methodb3")
    assertThat(methodb3.sourceLocation.startLine).isEqualTo(26)
    assertThat(methodb3.sourceLocation.endLine).isEqualTo(26)
    assertThat(methodb3.parameters.keys).containsExactly("x", "_#1", "i", "_#3")
  }

  private fun checkModuleClasses(module: ModuleSchema) {
    val classes = module.classes
    assertThat(classes).hasSize(1)
    val classb1 = classes.getValue("Classb1")
    assertThat(classb1.sourceLocation.startLine).isEqualTo(19)
    assertThat(classb1.sourceLocation.endLine).isEqualTo(22)
    assertThat(classb1.properties).hasSize(2)
  }

  private fun checkSupermodule(module: ModuleSchema) {
    val supermodule = module.supermodule

    assertThat(supermodule).isNotNull
    assertThat(supermodule!!.supermodule).isNull()

    assertThat(module.moduleClass.superclass).isEqualTo(supermodule.moduleClass)
    assertThat(supermodule.moduleClass.superclass).isEqualTo(BaseModule.getModuleClass().export())

    assertThat(supermodule.moduleUri)
      .isEqualTo(URI("modulepath:/org/pkl/core/EvaluateSchemaTestBaseModule.pkl"))
    assertThat(supermodule.moduleName).isEqualTo("test.base")

    assertThat(supermodule.moduleClass.sourceLocation.startLine).isEqualTo(1)
    assertThat(supermodule.moduleClass.sourceLocation.endLine).isEqualTo(10)

    val properties = supermodule.moduleClass.properties
    assertThat(properties).hasSize(1)
    assertThat(properties.getValue("propertya1").type).isEqualTo(PType.UNKNOWN)

    val methods = supermodule.moduleClass.methods
    assertThat(methods).hasSize(1)
    assertThat(methods.getValue("methoda1").returnType).isEqualTo(PType.UNKNOWN)

    val classes = supermodule.classes
    assertThat(classes).hasSize(1)
    assertThat(classes).containsKey("Classa1")
  }
}
