package org.pkl.core

import org.pkl.core.ModuleSource.*
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.pkl.core.runtime.BaseModule

class EvaluateSchemaTest {
  private val evaluator = Evaluator.preconfigured()

  @AfterEach
  fun afterEach() {
    evaluator.close()
  }

  @Test
  fun `evaluate test schema`() {
    val module = evaluator.evaluateSchema(
      modulePath("org/pkl/core/EvaluateSchemaTest.pkl")
    )

    checkModuleMetadata(module)

    checkModuleProperties(module)

    checkModuleMethods(module)

    checkModuleClasses(module)

    checkSupermodule(module)
  }

  @Test
  fun `evaluate pkl_base schema`() {
    val module = evaluator.evaluateSchema(uri(URI("pkl:base")))
    assertThat(module.moduleClass.superclass)
      .isEqualTo(BaseModule.getModuleClass().export())
  }

  @Test
  fun `does not export local classes`() {
    val module = evaluator.evaluateSchema(
      text(
        """
        class Foo {}
        local class Baz {}
        """.trimIndent()
      )
    )

    assertThat(module.classes.keys)
      .containsExactly("Foo")
  }

  private fun checkModuleMetadata(module: ModuleSchema) {
    assertThat(module.moduleUri)
      .isEqualTo(URI("modulepath:/org/pkl/core/EvaluateSchemaTest.pkl"))

    assertThat(module.moduleName).isEqualTo("test")

    assertThat(module.moduleClass.sourceLocation.startLine).isEqualTo(2)
    assertThat(module.moduleClass.sourceLocation.endLine).isEqualTo(22)
  }

  private fun checkModuleProperties(module: ModuleSchema) {
    val properties = module.moduleClass.properties
    assertThat(properties).hasSize(2)

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
    assertThat(paramType.pClass)
      .isEqualTo(BaseModule.getIntClass().export())
  }

  private fun checkModuleMethods(module: ModuleSchema) {
    val methods = module.moduleClass.methods
    assertThat(methods).hasSize(2)

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
    assertThat(paramBaseType.pClass)
      .isEqualTo(BaseModule.getStringClass().export())
    assertThat(paramType.constraints)
      .isEqualTo(listOf("!isEmpty", "startsWith(\"a\")"))

    val returnType = methodb2.returnType
    assertThat(returnType).isInstanceOf(PType.Constrained::class.java)
    returnType as PType.Constrained
    val returnBaseType = returnType.baseType
    assertThat(returnBaseType).isInstanceOf(PType.Class::class.java)
    returnBaseType as PType.Class
    assertThat(returnBaseType.pClass).isEqualTo(BaseModule.getIntClass().export())
    assertThat(returnType.constraints).isEqualTo(listOf("isPositive"))
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

    assertThat(module.moduleClass.superclass)
      .isEqualTo(supermodule.moduleClass)
    assertThat(supermodule.moduleClass.superclass)
      .isEqualTo(BaseModule.getModuleClass().export())

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
