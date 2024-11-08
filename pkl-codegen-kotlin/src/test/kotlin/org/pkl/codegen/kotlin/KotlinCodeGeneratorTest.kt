/*
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
package org.pkl.codegen.kotlin

import java.io.*
import java.nio.file.Path
import kotlin.reflect.KClass
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.memberProperties
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.pkl.core.*
import org.pkl.core.util.IoUtils

class KotlinCodeGeneratorTest {
  companion object {
    private const val MAPPER_PREFIX = "resources/META-INF/org/pkl/config/java/mapper/classes"

    // according to:
    // https://github.com/JetBrains/kotlin/blob/master/core/descriptors/
    // src/org/jetbrains/kotlin/renderer/KeywordStringsGenerated.java
    private val kotlinKeywords =
      setOf(
        "package",
        "as",
        "typealias",
        "class",
        "this",
        "super",
        "val",
        "var",
        "fun",
        "for",
        "null",
        "true",
        "false",
        "is",
        "in",
        "throw",
        "return",
        "break",
        "continue",
        "object",
        "if",
        "try",
        "else",
        "while",
        "do",
        "when",
        "interface",
        "typeof"
      )

    private val simpleClass: KClass<*> by lazy {
      generateKotlinCode(
          """
        module my.mod

        open class Simple {
          str: String
          list: List<Int>
        }
      """
            .trimIndent()
        )
        .compile()
        .getValue("Simple")
    }

    private val propertyTypesKotlinCode: KotlinSourceCode by lazy {
      generateKotlinCode(
        """
        module my.mod

        open class PropertyTypes {
          boolean: Boolean
          int: Int
          float: Float
          string: String
          duration: Duration
          durationUnit: DurationUnit
          dataSize: DataSize
          dataSizeUnit: DataSizeUnit
          nullable: String?
          nullable2: String?
          pair: Pair
          pair2: Pair<String, Other>
          coll: Collection
          coll2: Collection<Other>
          list: List
          list2: List<Other>
          set: Set
          set2: Set<Other>
          map: Map
          map2: Map<String, Other>
          container: Mapping
          container2: Mapping<String, Other>
          other: Other
          regex: Regex
          any: Any
          nonNull: NonNull
          enum: Direction
        }

        open class Other {
          name: String
        }

        typealias Direction = "north"|"east"|"south"|"west"
      """
          .trimIndent()
      )
    }

    private val propertyTypesClasses: Map<String, KClass<*>> by lazy {
      propertyTypesKotlinCode.compile()
    }

    private fun generateKotlinCode(
      pklCode: String,
      generateKdoc: Boolean = false,
      generateSpringBootConfig: Boolean = false,
      implementSerializable: Boolean = false
    ): KotlinSourceCode {

      val module = Evaluator.preconfigured().evaluateSchema(ModuleSource.text(pklCode))

      val generator =
        KotlinCodeGenerator(
          module,
          KotlinCodegenOptions(
            generateKdoc = generateKdoc,
            generateSpringBootConfig = generateSpringBootConfig,
            implementSerializable = implementSerializable
          )
        )
      return KotlinSourceCode(generator.kotlinFile)
    }
  }

  @TempDir lateinit var tempDir: Path

  @Test
  fun testEquals() {
    val ctor = simpleClass.constructors.first()
    val instance1 = ctor.call("foo", listOf(1, 2, 3))
    val instance2 = ctor.call("foo", listOf(1, 2, 3))
    val instance3 = ctor.call("foo", listOf(1, 3, 2))
    val instance4 = ctor.call("bar", listOf(1, 2, 3))

    assertThat(instance1).isEqualTo(instance1)
    assertThat(instance1).isEqualTo(instance2)
    assertThat(instance2).isEqualTo(instance1)

    assertThat(instance3).isNotEqualTo(instance1)
    assertThat(instance4).isNotEqualTo(instance1)
  }

  @Test
  fun testHashCode() {
    val ctor = simpleClass.constructors.first()
    val instance1 = ctor.call("foo", listOf(1, 2, 3))
    val instance2 = ctor.call("foo", listOf(1, 2, 3))
    val instance3 = ctor.call("foo", listOf(1, 3, 2))
    val instance4 = ctor.call("bar", listOf(1, 2, 3))

    assertThat(instance1.hashCode()).isEqualTo(instance1.hashCode())
    assertThat(instance1.hashCode()).isEqualTo(instance2.hashCode())
    assertThat(instance3.hashCode()).isNotEqualTo(instance1.hashCode())
    assertThat(instance4.hashCode()).isNotEqualTo(instance1.hashCode())
  }

  @Test
  fun testToString() {
    val (_, propertyTypes) = instantiateOtherAndPropertyTypes()

    assertThat(propertyTypes.toString())
      .isEqualTo(
        """PropertyTypes(boolean=true, int=42, float=42.3, string=string, duration=5.min, """ +
          """durationUnit=min, dataSize=3.gb, dataSizeUnit=gb, nullable=idea, nullable2=null, """ +
          """pair=(1, 2), pair2=(pigeon, Other(name=pigeon)), coll=[1, 2], """ +
          """coll2=[Other(name=pigeon), Other(name=pigeon)], list=[1, 2], """ +
          """list2=[Other(name=pigeon), Other(name=pigeon)], set=[1, 2], """ +
          """set2=[Other(name=pigeon)], map={1=one, 2=two}, map2={one=Other(name=pigeon), """ +
          """two=Other(name=pigeon)}, container={1=one, 2=two}, container2={one=Other(name=pigeon), """ +
          """two=Other(name=pigeon)}, other=Other(name=pigeon), regex=(i?)\w*, any=Other(name=pigeon), """ +
          """nonNull=Other(name=pigeon), enum=north)"""
      )
  }

  @Test
  fun `deprecated property with message`() {
    val kotlinCode =
      generateKotlinCode(
        """
      class ClassWithDeprecatedProperty {
        @Deprecated { message = "property deprecation message" } 
        deprecatedProperty: Int = 1337
      }
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .contains(
        """
      |  data class ClassWithDeprecatedProperty(
      |    @Deprecated(message = "property deprecation message")
      |    val deprecatedProperty: Long
    """
          .trimMargin()
      )
  }

  @Test
  fun `deprecated class with message`() {
    val kotlinCode =
      generateKotlinCode(
        """
      @Deprecated { message = "class deprecation message" }
      class DeprecatedClass {
        propertyOfDeprecatedClass: Int = 42
      }
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .contains(
        """
      |  @Deprecated(message = "class deprecation message")
      |  data class DeprecatedClass(
    """
          .trimMargin()
      )
  }

  @Test
  fun `deprecated module class with message`() {
    val kotlinCode =
      generateKotlinCode(
        """
      @Deprecated{ message = "module class deprecation message" }
      module DeprecatedModule
      
      propertyInDeprecatedModuleClass : Int = 42
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .contains(
        """
      |@Deprecated(message = "module class deprecation message")
      |data class DeprecatedModule(
    """
          .trimMargin()
      )
  }

  @Test
  fun `deprecated property`() {
    val kotlinCode =
      generateKotlinCode(
        """
      class ClassWithDeprecatedProperty {
         @Deprecated
         deprecatedProperty: Int = 1337
      }
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .contains(
        """
      |  data class ClassWithDeprecatedProperty(
      |    @Deprecated
      |    val deprecatedProperty: Long
    """
          .trimMargin()
      )
  }

  @Test
  fun `deprecated class`() {
    val kotlinCode =
      generateKotlinCode(
        """
      @Deprecated
      class DeprecatedClass {
        propertyOfDeprecatedClass: Int = 42
      }
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .contains(
        """
      |  @Deprecated
      |  data class DeprecatedClass(
    """
          .trimMargin()
      )
  }

  @Test
  fun `deprecated module class`() {
    val kotlinCode =
      generateKotlinCode(
        """
      @Deprecated
      module DeprecatedModule
      
      propertyInDeprecatedModuleClass : Int = 42
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .contains(
        """
      |@Deprecated
      |data class DeprecatedModule(
    """
          .trimMargin()
      )
  }

  @Test
  fun properties() {
    val (other, propertyTypes) = instantiateOtherAndPropertyTypes()

    assertThat(readProperty(other, "name")).isEqualTo("pigeon")
    assertThat(readProperty(propertyTypes, "boolean")).isEqualTo(true)
    assertThat(readProperty(propertyTypes, "int")).isEqualTo(42L)
    assertThat(readProperty(propertyTypes, "float")).isEqualTo(42.3)
    assertThat(readProperty(propertyTypes, "string")).isEqualTo("string")
    assertThat(readProperty(propertyTypes, "duration"))
      .isEqualTo(Duration(5.0, DurationUnit.MINUTES))
    assertThat(readProperty(propertyTypes, "dataSize"))
      .isEqualTo(DataSize(3.0, DataSizeUnit.GIGABYTES))
    assertThat(readProperty(propertyTypes, "nullable")).isEqualTo("idea")
    assertThat(readProperty(propertyTypes, "nullable2")).isEqualTo(null)
    assertThat(readProperty(propertyTypes, "list")).isEqualTo(listOf(1, 2))
    assertThat(readProperty(propertyTypes, "list2")).isEqualTo(listOf(other, other))
    assertThat(readProperty(propertyTypes, "set")).isEqualTo(setOf(1, 2))
    assertThat(readProperty(propertyTypes, "set2")).isEqualTo(setOf(other))
    assertThat(readProperty(propertyTypes, "map")).isEqualTo(mapOf(1 to "one", 2 to "two"))
    assertThat(readProperty(propertyTypes, "map2")).isEqualTo(mapOf("one" to other, "two" to other))
    assertThat(readProperty(propertyTypes, "container")).isEqualTo(mapOf(1 to "one", 2 to "two"))
    assertThat(readProperty(propertyTypes, "container2"))
      .isEqualTo(mapOf("one" to other, "two" to other))
    assertThat(readProperty(propertyTypes, "other")).isEqualTo(other)
    assertThat(readProperty(propertyTypes, "regex")).isInstanceOf(Regex::class.java)
    assertThat(readProperty(propertyTypes, "any")).isEqualTo(other)
    assertThat(readProperty(propertyTypes, "nonNull")).isEqualTo(other)
  }

  private fun readProperty(receiver: Any, name: String): Any? {
    val property = receiver.javaClass.kotlin.memberProperties.find { it.name == name }!!
    return property.invoke(receiver)
  }

  @Test
  fun `properties 2`() {
    assertThat(propertyTypesKotlinCode).isEqualToResourceFile("PropertyTypes.kotlin")
  }

  @Test
  fun `enum constant names`() {
    val cases =
      listOf(
        "camelCasedName" to "CAMEL_CASED_NAME",
        "hyphenated-name" to "HYPHENATED_NAME",
        "EnQuad\u2000EmSpace\u2003IdeographicSpace\u3000" to "EN_QUAD_EM_SPACE_IDEOGRAPHIC_SPACE_",
        "ᾊᾨ" to "ᾊᾨ",
        "0-digit" to "_0_DIGIT",
        "digit-1" to "DIGIT_1",
        "42" to "_42",
        "àœü" to "ÀŒÜ",
        "日本-つくば" to "日本_つくば"
      )
    val kotlinCode =
      generateKotlinCode(
        """
      module my.mod
      typealias MyTypeAlias = ${cases.joinToString(" | ") { "\"${it.first}\"" }}
    """
          .trimIndent()
      )
    val kotlinClass = kotlinCode.compile().getValue("MyTypeAlias").java

    assertThat(kotlinClass.enumConstants.size)
      .isEqualTo(cases.size) // make sure zip doesn't drop cases

    assertAll(
      "generated enum constants have correct names",
      kotlinClass.declaredFields.zip(cases) { field, (_, kotlinName) ->
        {
          assertThat(field.name).isEqualTo(kotlinName)
          Unit
        }
      }
    )

    assertAll(
      "toString() returns Pkl name",
      kotlinClass.enumConstants.zip(cases) { enumConstant, (pklName, _) ->
        {
          assertThat(enumConstant.toString()).isEqualTo(pklName)
          Unit
        }
      }
    )
  }

  @Test
  fun `conflicting enum constant names`() {
    val exception =
      assertThrows<KotlinCodeGeneratorException> {
        generateKotlinCode(
          """
        module my.mod
        typealias MyTypeAlias = "foo-bar" | "foo bar"
      """
            .trimIndent()
        )
      }
    assertThat(exception)
      .hasMessageContainingAll("both be converted to enum constant name", "FOO_BAR")
  }

  @Test
  fun `empty enum constant name`() {
    val exception =
      assertThrows<KotlinCodeGeneratorException> {
        generateKotlinCode(
          """
        module my.mod
        typealias MyTypeAlias = "foo" | "" | "bar"
      """
            .trimIndent()
        )
      }
    assertThat(exception).hasMessageContaining("cannot be converted")
  }

  @Test
  fun `inconvertible enum constant name`() {
    val exception =
      assertThrows<KotlinCodeGeneratorException> {
        generateKotlinCode(
          """
        module my.mod
        typealias MyTypeAlias = "foo" | "✅" | "bar"
      """
            .trimIndent()
        )
      }
    assertThat(exception).hasMessageContainingAll("✅", "cannot be converted")
  }

  @Test
  fun `data class`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module my.mod

      class Person {
        name: String
        age: Int
        hobbies: List<String>
        friends: Map<String, Person>
        sibling: Person?
      }
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .compilesSuccessfully()
      .isEqualTo(
        """
        package my
  
        import kotlin.Long
        import kotlin.String
        import kotlin.collections.List
        import kotlin.collections.Map
  
        object Mod {
          data class Person(
            val name: String,
            val age: Long,
            val hobbies: List<String>,
            val friends: Map<String, Person>,
            val sibling: Person?
          )
        }
        
      """
          .trimIndent()
      )
  }

  @Test
  fun `recursive types`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module my.mod

      open class Foo {
        other: Int
        bar: Bar
      }
      open class Bar {
        foo: Foo
        other: String
      }
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .compilesSuccessfully()
      .contains(
        """
        |  open class Foo(
        |    open val other: Long,
        |    open val bar: Bar
        |  )
      """
          .trimMargin()
      )
      .contains(
        """
        |  open class Bar(
        |    open val foo: Foo,
        |    open val other: String
        |  )
      """
          .trimMargin()
      )
  }

  @Test
  fun inheritance() {
    val kotlinCode =
      generateKotlinCode(
        """
      module my.mod

      open class Foo {
        one: Int
      }
      open class None extends Foo {}
      open class Bar extends None {
        two: String
      }
      class Baz extends Bar {
        three: Duration
      }
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .compilesSuccessfully()
      .contains(
        """
        |  open class Foo(
        |    open val one: Long
        |  )
      """
          .trimMargin()
      )
      .contains(
        """
        |  open class None(
        |    one: Long
        |  ) : Foo(one)
      """
          .trimMargin()
      )
      .contains(
        """
        |  open class Bar(
        |    one: Long,
        |    open val two: String
        |  ) : None(one)
      """
          .trimMargin()
      )
      .isEqualToResourceFile("Inheritance.kotlin")
  }

  @Test
  fun `stateless classes`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module my.mod

      class Foo
      abstract class Bar
      class Baz extends Bar
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .contains(
        """
      |  data class Foo
      |
      |  abstract class Bar
      |
      |  class Baz : Bar() {
      |    fun copy(): Baz = Baz()
    """
          .trimMargin()
      )
  }

  // https://github.com/apple/pkl/issues/569
  @Test
  fun `abstract classes`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module my.mod

      abstract class Foo { one: Int }
      abstract class Bar extends Foo { two: String }
      class Baz extends Bar { three: Duration }
      class Qux extends Bar {}
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .compilesSuccessfully()
      // missing trailing `{` proves that no methods are generated
      .contains(
        """
        |  abstract class Foo(
        |    open val one: Long
        |  )
      """
          .trimMargin()
      )
      // missing trailing `{` proves that no methods are generated
      .contains(
        """
        |  abstract class Bar(
        |    one: Long,
        |    open val two: String
        |  ) : Foo(one)
      """
          .trimMargin()
      )
      .contains(
        """
        |  class Baz(
        |    one: Long,
        |    two: String,
        |    val three: Duration
        |  ) : Bar(one, two) {
        |    fun copy(
        |      one: Long = this.one,
        |      two: String = this.two,
        |      three: Duration = this.three
        |    ): Baz = Baz(one, two, three)
      """
          .trimMargin()
      )
      .contains(
        """
        |  class Qux(
        |    one: Long,
        |    two: String
        |  ) : Bar(one, two) {
        |    fun copy(one: Long = this.one, two: String = this.two): Qux = Qux(one, two)
      """
          .trimMargin()
      )
  }

  // https://github.com/apple/pkl/issues/569
  @Test
  fun `abstract class that extends open class`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module my.mod

      open class Foo { one: Int }
      abstract class Bar extends Foo { two: String }
      class Baz extends Bar { three: Duration }
      class Qux extends Bar {}
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .compilesSuccessfully()
      .contains(
        """
        |  open class Foo(
        |    open val one: Long
        |  ) {
        |    open fun copy(one: Long = this.one): Foo = Foo(one)
      """
          .trimMargin()
      )
      // missing trailing `{` proves that no methods are generated
      .contains(
        """
        |  abstract class Bar(
        |    one: Long,
        |    open val two: String
        |  ) : Foo(one)
      """
          .trimMargin()
      )
      .contains(
        """
        |  class Baz(
        |    one: Long,
        |    two: String,
        |    val three: Duration
        |  ) : Bar(one, two) {
        |    fun copy(
        |      one: Long = this.one,
        |      two: String = this.two,
        |      three: Duration = this.three
        |    ): Baz = Baz(one, two, three)
        |
        |    override fun copy(one: Long): Baz = Baz(one, two, three)
      """
          .trimMargin()
      )
      .contains(
        """
        |  class Qux(
        |    one: Long,
        |    two: String
        |  ) : Bar(one, two) {
        |    fun copy(one: Long = this.one, two: String = this.two): Qux = Qux(one, two)
        |
        |    override fun copy(one: Long): Qux = Qux(one, two)
      """
          .trimMargin()
      )
  }

  // https://github.com/apple/pkl/issues/569
  @Test
  fun `abstract class that extends open class without adding properties`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module my.mod

      open class Foo { one: Int }
      abstract class Bar extends Foo {}
      class Baz extends Bar { two: Duration }
      class Qux extends Bar {}
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .compilesSuccessfully()
      .contains(
        """
        |  open class Foo(
        |    open val one: Long
        |  ) {
        |    open fun copy(one: Long = this.one): Foo = Foo(one)
      """
          .trimMargin()
      )
      // missing trailing `{` proves that no methods are generated
      .contains(
        """
        |  abstract class Bar(
        |    one: Long
        |  ) : Foo(one)
      """
          .trimMargin()
      )
      .contains(
        """
        |  class Baz(
        |    one: Long,
        |    val two: Duration
        |  ) : Bar(one) {
        |    fun copy(one: Long = this.one, two: Duration = this.two): Baz = Baz(one, two)
        |
        |    override fun copy(one: Long): Baz = Baz(one, two)
      """
          .trimMargin()
      )
      .contains(
        """
        |  class Qux(
        |    one: Long
        |  ) : Bar(one) {
        |    override fun copy(one: Long): Qux = Qux(one)
      """
          .trimMargin()
      )
  }

  @Test
  fun keywords() {
    val props = kotlinKeywords.joinToString("\n") { "`$it`: Int" }

    val fooClass =
      generateKotlinCode(
          """
      module my.mod

      class Foo {
        $props
      }
    """
            .trimIndent()
        )
        .compile()
        .getValue("Foo")

    assertThat(fooClass.declaredMemberProperties.map { it.name }).hasSameElementsAs(kotlinKeywords)
  }

  @Test
  fun `module properties`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module my.mod

      pigeon: Person
      parrot: Person

      class Person {
        name: String
      }
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .compilesSuccessfully()
      .isEqualTo(
        """
        package my

        import kotlin.String
  
        data class Mod(
          val pigeon: Person,
          val parrot: Person
        ) {
          data class Person(
            val name: String
          )
        }
        
      """
          .trimIndent()
      )
  }

  @Test
  fun `simple module name`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module mod

      pigeon: Person
      parrot: Person

      class Person {
        name: String
      }
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .compilesSuccessfully()
      .isEqualTo(
        """
        import kotlin.String

        data class Mod(
          val pigeon: Person,
          val parrot: Person
        ) {
          data class Person(
            val name: String
          )
        }
        
      """
          .trimIndent()
      )
  }

  @Test
  fun `hidden properties`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module my.mod

      hidden pigeon1: String
      parrot1: String

      class Persons {
        hidden pigeon2: String
        parrot2: String
      }
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .doesNotContain("pigeon1: String")
      .contains("parrot1: String")
      .doesNotContain("pigeon2: String")
      .contains("parrot2: String")
  }

  @Test
  fun kdoc() {
    val kotlinCode =
      generateKotlinCode(
        """
      /// module comment.
      /// *emphasized* `code`.
      module my.mod

      /// module property comment.
      /// *emphasized* `code`.
      pigeon: Person

      /// class comment.
      /// *emphasized* `code`.
      open class Product {
        /// class property comment.
        /// *emphasized* `code`.
        price: String
      }

      /// class comment.
      /// *emphasized* `code`.
      class Person {
        /// class property comment.
        /// *emphasized* `code`.
        name: String
      }

      /// type alias comment.
      /// *emphasized* `code`.
      typealias Email = String(contains("@"))
    """
          .trimIndent(),
        generateKdoc = true
      )

    assertThat(kotlinCode).compilesSuccessfully().isEqualToResourceFile("Kdoc.kotlin")
  }

  @Test
  fun `kdoc 2`() {
    val kotlinCode =
      generateKotlinCode(
        """
      /// module comment.
      /// *emphasized* `code`.
      module my.mod

      class Product
    """
          .trimIndent(),
        generateKdoc = true
      )

    assertThat(kotlinCode)
      .isEqualTo(
        """
      package my

      /**
       * module comment.
       * *emphasized* `code`.
       */
      object Mod {
        data class Product
      }
      
    """
          .trimIndent()
      )
  }

  @Test
  fun `pkl_base type aliases`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module mod

      uint8: UInt8
      uint16: UInt16
      uint32: UInt32
      uint: UInt
      int8: Int8
      int16: Int16
      int32: Int32
      uri: Uri

      pair: Pair<UInt8, UInt16>
      list: List<UInt32>
      set: Set<UInt>
      map: Map<Int8, Int16>
      listing: Listing<Int32>
      mapping: Mapping<Uri, UInt8>
      nullable: UInt16?

      class Foo {
        uint8: UInt8
        uint16: UInt16
        uint32: UInt32
        uint: UInt
        int8: Int8
        int16: Int16
        int32: Int32
        uri: Uri
        list: List<UInt>
      }
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .compilesSuccessfully()
      .isEqualTo(
        """
        import java.net.URI
        import kotlin.Byte
        import kotlin.Int
        import kotlin.Long
        import kotlin.Pair
        import kotlin.Short
        import kotlin.collections.List
        import kotlin.collections.Map
        import kotlin.collections.Set
  
        data class Mod(
          val uint8: Short,
          val uint16: Int,
          val uint32: Long,
          val uint: Long,
          val int8: Byte,
          val int16: Short,
          val int32: Int,
          val uri: URI,
          val pair: Pair<Short, Int>,
          val list: List<Long>,
          val set: Set<Long>,
          val map: Map<Byte, Short>,
          val listing: List<Int>,
          val mapping: Map<URI, Short>,
          val nullable: Int?
        ) {
          data class Foo(
            val uint8: Short,
            val uint16: Int,
            val uint32: Long,
            val uint: Long,
            val int8: Byte,
            val int16: Short,
            val int32: Int,
            val uri: URI,
            val list: List<Long>
          )
        }
        
      """
          .trimIndent()
      )
  }

  @Test
  fun `user defined type aliases`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module mod

      typealias Simple = String
      typealias Constrained = String(length >= 3)
      typealias Parameterized = List<Int(nonZero)>
      typealias Recursive1 = Parameterized(nonEmpty)
      typealias Recursive2 = List<Constrained>

      simple: Simple
      constrained: Constrained
      parameterized: Parameterized
      recursive1: Recursive1
      recursive2: Recursive2

      class Foo {
        simple: Simple
        constrained: Constrained
        parameterized: Parameterized
        recursive1: Recursive1
        recursive2: Recursive2
      }
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .compilesSuccessfully()
      .isEqualTo(
        """
        import kotlin.Long
        import kotlin.String
        import kotlin.collections.List
  
        typealias Simple = String
  
        typealias Constrained = String
  
        typealias Parameterized = List<Long>
  
        typealias Recursive1 = Parameterized
  
        typealias Recursive2 = List<Constrained>
  
        data class Mod(
          val simple: Simple,
          val constrained: Constrained,
          val parameterized: Parameterized,
          val recursive1: Recursive1,
          val recursive2: Recursive2
        ) {
          data class Foo(
            val simple: Simple,
            val constrained: Constrained,
            val parameterized: Parameterized,
            val recursive1: Recursive1,
            val recursive2: Recursive2
          )
        }
        
      """
          .trimIndent()
      )
  }

  @Test
  fun genericTypeAliases() {
    val kotlinCode =
      generateKotlinCode(
        """
      module mod

      class Person { name: String }

      typealias List2<E> = List<E>
      typealias Map2<V, K> = Map<K, V>
      typealias StringMap<V> = Map<String, V>
      typealias MMap<X> = Map<X, X>

      res1: List2<Int>
      res2: List2<List2<String>>
      res3: Map2<String, Int>
      res4: StringMap<Duration>
      res5: MMap<Person?>

      res6: List2
      res7: Map2
      res8: StringMap
      res9: MMap

      class Foo {
        res1: List2<Int>
        res2: List2<List2<String>>
        res3: Map2<String, Int>
        res4: StringMap<Duration>
        res5: MMap<Person?>

        res6: List2
        res7: Map2
        res8: StringMap
        res9: MMap
      }
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .compilesSuccessfully()
      .contains(
        """
        |data class Mod(
        |  val res1: List2<Long>,
        |  val res2: List2<List2<String>>,
        |  val res3: Map2<String, Long>,
        |  val res4: StringMap<Duration>,
        |  val res5: MMap<Person?>,
        |  val res6: List2<Any?>,
        |  val res7: Map2<Any?, Any?>,
        |  val res8: StringMap<Any?>,
        |  val res9: MMap<Any?>
      """
          .trimMargin()
      )
      .contains(
        """
        |  data class Foo(
        |    val res1: List2<Long>,
        |    val res2: List2<List2<String>>,
        |    val res3: Map2<String, Long>,
        |    val res4: StringMap<Duration>,
        |    val res5: MMap<Person?>,
        |    val res6: List2<Any?>,
        |    val res7: Map2<Any?, Any?>,
        |    val res8: StringMap<Any?>,
        |    val res9: MMap<Any?>
      """
          .trimMargin()
      )
  }

  @Test
  fun `union of string literals`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module mod

      x: "Pigeon"|"Barn Owl"|"Parrot"
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .compilesSuccessfully()
      .contains(
        """
        |data class Mod(
        |  val x: String
        |)
      """
          .trimMargin()
      )
  }

  @Test
  fun `other union type`() {
    val e =
      assertThrows<KotlinCodeGeneratorException> {
        generateKotlinCode(
          """
          module mod
  
          x: "Pigeon"|Int|"Parrot"
        """
            .trimIndent()
        )
      }
    assertThat(e).hasMessageContaining("Pkl union types are not supported")
  }

  @Test
  fun `stringy type`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module mod

      v1: "RELEASE"
      v2: "RELEASE"|String
      v3: String|"RELEASE"
      v4: "RELEASE"|String|"LATEST"
      v5: Version|String|"LATEST"
      v6: (Version|String)|("LATEST"|String)
      
      typealias Version = "RELEASE"|String|"LATEST"
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .contains("v1: String")
      .contains("v2: String")
      .contains("v3: String")
      .contains("v4: String")
      .contains("v5: String")
      .contains("v6: String")
  }

  @Test
  fun `stringy type alias`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module mod

      typealias Version1 = "RELEASE"|String
      typealias Version2 = String|"RELEASE"
      typealias Version3 = "RELEASE"|String|"LATEST"
      typealias Version4 = Version3|String|"LATEST" // ideally wouldn't be inlined
      typealias Version5 = (Version4|String)|("LATEST"|String)
      typealias Version6 = Version5 // not inlined
    """
          .trimIndent()
      )

    assertThat(kotlinCode)
      .contains("typealias Version1 = String")
      .contains("typealias Version2 = String")
      .contains("typealias Version3 = String")
      .contains("typealias Version4 = String")
      .contains("typealias Version5 = String")
      .contains("typealias Version6 = Version5")
  }

  @Test
  fun `spring boot config`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module my.mod

      server: Server

      class Server {
        port: Int
        urls: Listing<Uri>
      }
    """
          .trimIndent(),
        generateSpringBootConfig = true
      )

    assertThat(kotlinCode)
      .contains(
        """
        |@ConfigurationProperties
        |data class Mod(
        |  val server: Server
      """
          .trimMargin()
      )
      .contains(
        """
        |  @ConfigurationProperties("server")
        |  data class Server(
        |    val port: Long,
        |    val urls: List<URI>
      """
          .trimMargin()
      )
      .doesNotContain("@ConstructorBinding")

    // not worthwhile to add spring & spring boot dependency just so that this test can compile
    // their annotations
    val kotlinCodeWithoutSpringAnnotations =
      kotlinCode.deleteLines { it.contains("ConfigurationProperties") }
    assertThat(kotlinCodeWithoutSpringAnnotations).compilesSuccessfully()
  }

  @Test
  fun `import module`() {
    val library =
      PklModule(
        "library",
        """
          module library
    
          class Person { name: String; age: Int }
          
          pigeon: Person
        """
          .trimIndent()
      )

    val client =
      PklModule(
        "client",
        """
          module client
          
          import "library.pkl"
          
          lib: library
          
          parrot: library.Person
        """
          .trimIndent()
      )

    val kotlinSourceFiles = generateFiles(library, client)
    assertDoesNotThrow {
      InMemoryKotlinCompiler.compile(kotlinSourceFiles.mapValues { it.value.text })
    }

    val kotlinClientCode =
      kotlinSourceFiles.entries.find { (fileName, _) -> fileName.endsWith("Client.kt") }!!.value

    assertThat(kotlinClientCode)
      .contains(
        """
        |data class Client(
        |  val lib: Library,
        |  val parrot: Library.Person
        |)
      """
          .trimMargin()
      )
  }

  @Test
  fun `extend module`() {
    val base =
      PklModule(
        "base",
        """
          open module base
    
          open class Person { name: String }
    
          pigeon: Person
        """
          .trimIndent()
      )

    val derived =
      PklModule(
        "derived",
        """
          module derived
          extends "base.pkl"
          
          class Person2 extends Person { age: Int }
          
          person1: Person
          person2: Person2
        """
          .trimIndent()
      )

    val kotlinSourceFiles = generateFiles(base, derived)
    assertDoesNotThrow {
      InMemoryKotlinCompiler.compile(kotlinSourceFiles.mapValues { it.value.text })
    }

    val kotlinDerivedCode =
      kotlinSourceFiles.entries.find { (filename, _) -> filename.endsWith("Derived.kt") }!!.value

    assertThat(kotlinDerivedCode)
      .contains(
        """
        |class Derived(
        |  pigeon: Base.Person,
        |  val person1: Base.Person,
        |  val person2: Person2
        |) : Base(pigeon)
      """
          .trimMargin()
      )
      .contains(
        """
        |  class Person2(
        |    name: String,
        |    val age: Long
        |  ) : Base.Person(name)
      """
          .trimMargin()
      )
  }

  @Test
  fun `empty module`() {
    val kotlinCode = generateKotlinCode("module mod")
    assertThat(kotlinCode).isEqualTo("object Mod\n")
  }

  @Test
  fun `extend module that only contains type aliases`() {
    val moduleOne =
      PklModule(
        "base",
        """
          abstract module base
    
          typealias Version = "LATEST"|String
        """
          .trimIndent()
      )

    val moduleTwo =
      PklModule(
        "derived",
        """
          module derived
          
          extends "base.pkl"
          
          v: Version = "1.2.3"
        """
          .trimIndent()
      )

    val kotlinSourceFiles = generateFiles(moduleOne, moduleTwo)
    assertDoesNotThrow {
      InMemoryKotlinCompiler.compile(kotlinSourceFiles.mapValues { it.value.text })
    }

    val kotlinDerivedCode =
      kotlinSourceFiles.entries.find { (filename, _) -> filename.endsWith("Derived.kt") }!!.value
    assertThat(kotlinDerivedCode)
      .contains(
        """
      |class Derived(
      |  val v: Version
      |) : Base()
    """
          .trimMargin()
      )
  }

  @Test
  fun `generated properties files`() {
    val pklModule =
      PklModule(
        "Mod.pkl",
        """
          module org.pkl.Mod
    
          foo: Foo
    
          bar: Bar
    
          class Foo {
            prop: String
          }
    
          class Bar {
            prop: Int
          }
        """
          .trimIndent()
      )
    val generated = generateFiles(pklModule)
    val expectedPropertyFile =
      "resources/META-INF/org/pkl/config/java/mapper/classes/org.pkl.Mod.properties"
    assertThat(generated).containsKey(expectedPropertyFile)
    val propertyFileContents = generated[expectedPropertyFile]!!
    assertThat(propertyFileContents)
      .contains("org.pkl.config.java.mapper.org.pkl.Mod\\#ModuleClass=org.pkl.Mod")
      .contains("org.pkl.config.java.mapper.org.pkl.Mod\\#Foo=org.pkl.Mod\$Foo")
      .contains("org.pkl.config.java.mapper.org.pkl.Mod\\#Bar=org.pkl.Mod\$Bar")
  }

  @Test
  fun `generates serializable classes`() {
    val kotlinCode =
      generateKotlinCode(
        """
      module mod

      class BigStruct {
        boolean: Boolean
        int: Int
        float: Float
        string: String
        duration: Duration
        dataSize: DataSize
        pair: Pair
        pair2: Pair<String, SmallStruct>
        coll: Collection
        coll2: Collection<SmallStruct>
        list: List
        list2: List<SmallStruct>
        set: Set
        set2: Set<SmallStruct>
        map: Map
        map2: Map<String, SmallStruct>
        container: Mapping
        container2: Mapping<String, SmallStruct>
        other: SmallStruct
        regex: Regex
        nonNull: NonNull
        enum: Direction
      }

      class SmallStruct {
        name: String
      }

      typealias Direction = "north"|"east"|"south"|"west"
      
      abstract class NotSerializable
    """
          .trimIndent(),
        implementSerializable = true
      )

    assertThat(kotlinCode)
      .contains(": Serializable")
      .contains("private const val serialVersionUID: Long = 0L")
      .contains("abstract class NotSerializable\n")

    val classes = kotlinCode.compile()
    val enumClass = classes.getValue("Direction")
    val enumValue = enumClass.java.enumConstants.first()

    val smallStructCtor = classes.getValue("SmallStruct").constructors.first()
    val smallStruct = smallStructCtor.call("pigeon")

    val bigStructCtor = classes.getValue("BigStruct").constructors.first()
    val bigStruct =
      bigStructCtor.call(
        true,
        42L,
        42.3,
        "string",
        Duration(5.0, DurationUnit.MINUTES),
        DataSize(3.0, DataSizeUnit.GIGABYTES),
        kotlin.Pair(1, 2),
        kotlin.Pair("pigeon", smallStruct),
        listOf(1, 2, 3),
        listOf(smallStruct, smallStruct),
        listOf(1, 2, 3),
        listOf(smallStruct, smallStruct),
        setOf(1, 2, 3),
        setOf(smallStruct, smallStruct),
        mapOf(1 to "one", 2 to "two"),
        mapOf("one" to smallStruct, "two" to smallStruct),
        mapOf(1 to "one", 2 to "two"),
        mapOf("one" to smallStruct, "two" to smallStruct),
        smallStruct,
        Regex("(i?)\\w*"),
        smallStruct,
        enumValue
      )

    fun confirmSerDe(instance: Any) {
      var restoredInstance: Any? = null

      assertThatCode {
          // serialize
          val baos = ByteArrayOutputStream()
          val oos = ObjectOutputStream(baos)
          oos.writeObject(instance)
          oos.flush()

          // deserialize
          val bais = ByteArrayInputStream(baos.toByteArray())
          val ois =
            object : ObjectInputStream(bais) {
              override fun resolveClass(desc: ObjectStreamClass?): Class<*> {
                return Class.forName(desc!!.name, false, instance.javaClass.classLoader)
              }
            }
          restoredInstance = ois.readObject()
        }
        .doesNotThrowAnyException()

      assertThat(restoredInstance!!).isEqualTo(instance)
    }

    confirmSerDe(enumValue)
    confirmSerDe(smallStruct)
    confirmSerDe(bigStruct)
  }

  @Test
  fun `generates serializable module classes`() {
    val kotlinCode =
      generateKotlinCode(
        """
        module Person
        
        address: Address
        
        class Address {
          street: String
        }
      """,
        implementSerializable = true
      )

    assertThat(kotlinCode)
      .contains(
        """
      |data class Person(
      |  val address: Address
      |) : Serializable {
      |  data class Address(
      |    val street: String
      |  ) : Serializable {
      |    companion object {
      |      private const val serialVersionUID: Long = 0L
      |    }
      |  }
      |
      |  companion object {
      |    private const val serialVersionUID: Long = 0L
      |  }
      |}
    """
          .trimMargin()
      )
  }

  @Test
  fun `encoded file paths`() {
    val kotlinCode =
      generateFiles(
        PklModule(
          "FooBar.pkl",
          """
            module `Foo*Bar`
            
            someProp: String
          """
            .trimIndent()
        )
      )
    assertThat(kotlinCode).containsKey("kotlin/Foo(2a)Bar.kt")
  }

  @Test
  fun `override names in a standalone module`() {
    val files =
      KotlinCodegenOptions(
          renames = mapOf("a.b.c" to "x.y.z", "d.e.f.AnotherModule" to "u.v.w.RenamedModule")
        )
        .generateFiles(
          "MyModule.pkl" to
            """
              module a.b.c.MyModule
              
              foo: String = "abc"
            """
              .trimIndent(),
          "AnotherModule.pkl" to
            """
              module d.e.f.AnotherModule
              
              bar: Int = 123
            """
              .trimIndent()
        )
        .toMutableMap()

    files.validateContents(
      "kotlin/x/y/z/MyModule.kt" to listOf("package x.y.z", "data class MyModule("),
      "$MAPPER_PREFIX/a.b.c.MyModule.properties" to
        listOf("org.pkl.config.java.mapper.a.b.c.MyModule\\#ModuleClass=x.y.z.MyModule"),
      // ---
      "kotlin/u/v/w/RenamedModule.kt" to listOf("package u.v.w", "data class RenamedModule("),
      "$MAPPER_PREFIX/d.e.f.AnotherModule.properties" to
        listOf("org.pkl.config.java.mapper.d.e.f.AnotherModule\\#ModuleClass=u.v.w.RenamedModule"),
    )
  }

  @Test
  fun `override names based on the longest prefix`() {
    val files =
      KotlinCodegenOptions(
          renames = mapOf("com.foo.bar." to "x.", "com.foo." to "y.", "com." to "z.", "" to "w.")
        )
        .generateFiles(
          "com/foo/bar/Module1" to
            """
              module com.foo.bar.Module1
              
              bar: String
            """
              .trimIndent(),
          "com/Module2" to
            """
              module com.Module2
              
              com: String
            """
              .trimIndent(),
          "org/baz/Module3" to
            """
              module org.baz.Module3
              
              baz: String
            """
              .trimIndent()
        )
        .toMutableMap()

    files.validateContents(
      "kotlin/x/Module1.kt" to listOf("package x", "data class Module1("),
      "$MAPPER_PREFIX/com.foo.bar.Module1.properties" to
        listOf("org.pkl.config.java.mapper.com.foo.bar.Module1\\#ModuleClass=x.Module1"),
      // ---
      "kotlin/z/Module2.kt" to listOf("package z", "data class Module2("),
      "$MAPPER_PREFIX/com.Module2.properties" to
        listOf("org.pkl.config.java.mapper.com.Module2\\#ModuleClass=z.Module2"),
      // ---
      "kotlin/w/org/baz/Module3.kt" to listOf("package w.org.baz", "data class Module3("),
      "$MAPPER_PREFIX/org.baz.Module3.properties" to
        listOf("org.pkl.config.java.mapper.org.baz.Module3\\#ModuleClass=w.org.baz.Module3")
    )
  }

  @Test
  fun `override names in multiple modules using each other`() {
    val files =
      KotlinCodegenOptions(
          renames =
            mapOf(
              "org.foo" to "com.foo.x",
              "org.bar.Module2" to "org.bar.RenamedModule",
              "org.baz" to "com.baz.a.b"
            )
        )
        .generateFiles(
          "org/foo/Module1" to
            """
              module org.foo.Module1
              
              class Person {
                name: String
              }
            """
              .trimIndent(),
          "org/bar/Module2" to
            """
              module org.bar.Module2
              
              import "../../org/foo/Module1.pkl"

              class Group {
                owner: Module1.Person
                name: String
              }
            """
              .trimIndent(),
          "org/baz/Module3" to
            """
              module org.baz.Module3
              
              import "../../org/bar/Module2.pkl"

              class Supergroup {
                owner: Module2.Group
              }
            """
              .trimIndent()
        )

    files.validateContents(
      "kotlin/com/foo/x/Module1.kt" to
        listOf("package com.foo.x", "object Module1 {", "data class Person("),
      "$MAPPER_PREFIX/org.foo.Module1.properties" to
        listOf(
          "org.pkl.config.java.mapper.org.foo.Module1\\#ModuleClass=com.foo.x.Module1",
          "org.pkl.config.java.mapper.org.foo.Module1\\#Person=com.foo.x.Module1${'$'}Person",
        ),
      // ---
      "kotlin/org/bar/RenamedModule.kt" to
        listOf(
          "package org.bar",
          "import com.foo.x.Module1",
          "object RenamedModule {",
          "val owner: Module1.Person"
        ),
      "$MAPPER_PREFIX/org.bar.Module2.properties" to
        listOf(
          "org.pkl.config.java.mapper.org.bar.Module2\\#ModuleClass=org.bar.RenamedModule",
          "org.pkl.config.java.mapper.org.bar.Module2\\#Group=org.bar.RenamedModule${'$'}Group",
        ),
      // ---
      "kotlin/com/baz/a/b/Module3.kt" to
        listOf(
          "package com.baz.a.b",
          "import org.bar.RenamedModule",
          "object Module3 {",
          "val owner: RenamedModule.Group"
        ),
      "$MAPPER_PREFIX/org.baz.Module3.properties" to
        listOf(
          "org.pkl.config.java.mapper.org.baz.Module3\\#ModuleClass=com.baz.a.b.Module3",
          "org.pkl.config.java.mapper.org.baz.Module3\\#Supergroup=com.baz.a.b.Module3${'$'}Supergroup",
        ),
    )
  }

  @Test
  fun `do not capitalize names of renamed classes`() {
    val files =
      KotlinCodegenOptions(
          renames = mapOf("a.b.c.MyModule" to "x.y.z.renamed_module", "d.e.f." to "u.v.w.")
        )
        .generateFiles(
          "MyModule.pkl" to
            """
              module a.b.c.MyModule
              
              foo: String = "abc"
            """
              .trimIndent(),
          "lower_module.pkl" to
            """
              module d.e.f.lower_module 
              
              bar: Int = 123
            """
              .trimIndent()
        )

    files.validateContents(
      "kotlin/x/y/z/renamed_module.kt" to listOf("package x.y.z", "data class renamed_module("),
      "$MAPPER_PREFIX/a.b.c.MyModule.properties" to
        listOf("org.pkl.config.java.mapper.a.b.c.MyModule\\#ModuleClass=x.y.z.renamed_module"),
      // ---
      "kotlin/u/v/w/Lower_module.kt" to listOf("package u.v.w", "data class Lower_module("),
      "$MAPPER_PREFIX/d.e.f.lower_module.properties" to
        listOf("org.pkl.config.java.mapper.d.e.f.lower_module\\#ModuleClass=u.v.w.Lower_module"),
    )
  }

  private fun Map<String, String>.validateContents(
    vararg assertions: kotlin.Pair<String, List<String>>
  ) {
    val files = toMutableMap()

    for ((fileName, lines) in assertions) {
      assertThat(files).containsKey(fileName)
      assertThat(files.remove(fileName)).describedAs("Contents of $fileName").contains(lines)
    }

    assertThat(files).isEmpty()
  }

  private fun KotlinCodegenOptions.generateFiles(
    vararg pklModules: PklModule
  ): Map<String, String> {
    val pklFiles = pklModules.map { it.writeToDisk(tempDir.resolve("pkl/${it.name}.pkl")) }
    val evaluator = Evaluator.preconfigured()
    return pklFiles.fold(mapOf()) { acc, pklFile ->
      val pklSchema = evaluator.evaluateSchema(ModuleSource.path(pklFile))
      val generator = KotlinCodeGenerator(pklSchema, this)
      acc + generator.output
    }
  }

  private fun KotlinCodegenOptions.generateFiles(
    vararg pklModules: kotlin.Pair<String, String>
  ): Map<String, String> =
    generateFiles(*pklModules.map { (name, text) -> PklModule(name, text) }.toTypedArray())

  private fun generateFiles(vararg pklModules: PklModule): Map<String, KotlinSourceCode> =
    KotlinCodegenOptions().generateFiles(*pklModules).mapValues { KotlinSourceCode(it.value) }

  private fun instantiateOtherAndPropertyTypes(): kotlin.Pair<Any, Any> {
    val otherCtor = propertyTypesClasses.getValue("Other").constructors.first()
    val other = otherCtor.call("pigeon")

    val enumClass = propertyTypesClasses.getValue("Direction").java
    val enumValue = enumClass.enumConstants.first()

    val propertyTypesCtor = propertyTypesClasses.getValue("PropertyTypes").constructors.first()
    val propertyTypes =
      propertyTypesCtor.call(
        true,
        42,
        42.3,
        "string",
        Duration(5.0, DurationUnit.MINUTES),
        DurationUnit.MINUTES,
        DataSize(3.0, DataSizeUnit.GIGABYTES),
        DataSizeUnit.GIGABYTES,
        "idea",
        null,
        kotlin.Pair(1, 2),
        kotlin.Pair("pigeon", other),
        listOf(1, 2),
        listOf(other, other),
        listOf(1, 2),
        listOf(other, other),
        setOf(1, 2),
        setOf(other),
        mapOf(1 to "one", 2 to "two"),
        mapOf("one" to other, "two" to other),
        mapOf(1 to "one", 2 to "two"),
        mapOf("one" to other, "two" to other),
        other,
        Regex("(i?)\\w*"),
        other,
        other,
        enumValue
      )

    return other to propertyTypes
  }

  private fun assertThat(actual: KotlinSourceCode): KotlinSourceCodeAssert =
    KotlinSourceCodeAssert(actual)

  private class KotlinSourceCodeAssert(actual: KotlinSourceCode) :
    AbstractAssert<KotlinSourceCodeAssert, KotlinSourceCode>(
      actual,
      KotlinSourceCodeAssert::class.java
    ) {
    fun contains(expected: String): KotlinSourceCodeAssert {
      if (!actual.text.contains(expected)) {
        // check for equality to get better error output (IDE diff dialog)
        assertThat(actual.text).isEqualTo(expected)
      }
      return this
    }

    fun doesNotContain(expected: String): KotlinSourceCodeAssert {
      assertThat(actual.text).doesNotContain(expected)
      return this
    }

    fun compilesSuccessfully(): KotlinSourceCodeAssert {
      assertThatCode { actual.compile() }.doesNotThrowAnyException()
      return this
    }

    fun isEqualTo(expected: String): KotlinSourceCodeAssert {
      assertThat(actual.text).isEqualTo(expected)
      return this
    }

    fun isEqualToResourceFile(fileName: String): KotlinSourceCodeAssert {
      isEqualTo(IoUtils.readClassPathResourceAsString(javaClass, fileName))
      return this
    }
  }

  data class KotlinSourceCode(val text: String) {
    fun compile(): Map<String, KClass<*>> =
      InMemoryKotlinCompiler.compile(mapOf("my/Mod.kt" to text))

    fun deleteLines(predicate: (String) -> Boolean): KotlinSourceCode =
      KotlinSourceCode(text.lines().filterNot(predicate).joinToString("\n"))
  }
}
