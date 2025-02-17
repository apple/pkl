/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.codegen.java

import java.io.*
import java.nio.file.Path
import java.util.function.Consumer
import java.util.regex.Pattern
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.pkl.core.*
import org.pkl.core.ModuleSource.path
import org.pkl.core.ModuleSource.text
import org.pkl.core.util.IoUtils

class JavaCodeGeneratorTest {
  companion object {
    private const val MAPPER_PREFIX = "resources/META-INF/org/pkl/config/java/mapper/classes"

    private val simpleClass: Class<*> by lazy {
      generateJavaCode(
          """
        module my.mod

        class Simple {
          str: String
          list: List<Int>
        }
      """
            .trimIndent()
        )
        .compile()
        .getValue("my.Mod\$Simple")
    }

    private val propertyTypesSources: JavaSourceCode by lazy {
      generateJavaCode(
        """
        module my.mod

        class PropertyTypes {
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

        class Other {
          name: String
        }

        typealias Direction = "north"|"east"|"south"|"west"
      """
      )
    }

    private val propertyTypesClasses: Map<String, Class<*>> by lazy {
      propertyTypesSources.compile()
    }

    private fun generateJavaCode(
      pklCode: String,
      options: JavaCodeGeneratorOptions = JavaCodeGeneratorOptions(),
    ): JavaSourceCode {
      val module = Evaluator.preconfigured().evaluateSchema(text(pklCode))
      val generator = JavaCodeGenerator(module, options)
      return JavaSourceCode(generator.javaFile)
    }
  }

  @TempDir lateinit var tempDir: Path

  @Test
  fun testEquals() {
    val ctor = simpleClass.constructors.first()
    val instance1 = ctor.newInstance("foo", listOf(1, 2, 3))
    val instance2 = ctor.newInstance("foo", listOf(1, 2, 3))
    val instance3 = ctor.newInstance("foo", listOf(1, 3, 2))
    val instance4 = ctor.newInstance("bar", listOf(1, 2, 3))

    assertThat(instance1).isEqualTo(instance1).isEqualTo(instance2)
    assertThat(instance2).isEqualTo(instance1)
    assertThat(instance3).isNotEqualTo(instance1)
    assertThat(instance4).isNotEqualTo(instance1)
  }

  @Test
  fun testHashCode() {
    val ctor = simpleClass.constructors.first()
    val instance1 = ctor.newInstance("foo", listOf(1, 2, 3))
    val instance2 = ctor.newInstance("foo", listOf(1, 2, 3))
    val instance3 = ctor.newInstance("foo", listOf(1, 3, 2))
    val instance4 = ctor.newInstance("bar", listOf(1, 2, 3))

    assertThat(instance1.hashCode()).isEqualTo(instance1.hashCode()).isEqualTo(instance2.hashCode())
    assertThat(instance3.hashCode()).isNotEqualTo(instance1.hashCode())
    assertThat(instance4.hashCode()).isNotEqualTo(instance1.hashCode())
  }

  @Test
  fun testToString() {
    val (_, propertyTypes) = instantiateOtherAndPropertyTypes()

    assertThat(propertyTypes.toString())
      .isEqualTo(
        """
        PropertyTypes {
          _boolean = true
          _int = 42
          _float = 42.3
          string = string
          duration = 5.min
          durationUnit = min
          dataSize = 3.gb
          dataSizeUnit = gb
          nullable = idea
          nullable2 = null
          pair = Pair(1, 2)
          pair2 = Pair(pigeon, Other {
            name = pigeon
          })
          coll = [1, 2, 3]
          coll2 = [Other {
            name = pigeon
          }, Other {
            name = pigeon
          }]
          list = [1, 2, 3]
          list2 = [Other {
            name = pigeon
          }, Other {
            name = pigeon
          }]
          set = [1, 2, 3]
          set2 = [Other {
            name = pigeon
          }]
          map = {1=one, 2=two}
          map2 = {one=Other {
            name = pigeon
          }, two=Other {
            name = pigeon
          }}
          container = {1=one, 2=two}
          container2 = {one=Other {
            name = pigeon
          }, two=Other {
            name = pigeon
          }}
          other = Other {
            name = pigeon
          }
          regex = (i?)\w*
          any = Other {
            name = pigeon
          }
          nonNull = Other {
            name = pigeon
          }
          _enum = north
        }
      """
          .trimIndent()
      )
  }

  @Test
  fun `deprecated property with message`() {
    val javaCode =
      generateJavaCode(
        """
      class ClassWithDeprecatedProperty {
         @Deprecated { message = "property deprecation message" } 
         deprecatedProperty: Int = 1337
      }
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(generateJavadoc = true),
      )
    assertThat(javaCode)
      .contains(
        """
        |  public static final class ClassWithDeprecatedProperty {
        |    /**
        |     * @deprecated property deprecation message
        |     */
        |    @Deprecated
        |    public final long deprecatedProperty;
      """
          .trimMargin()
      )
      .contains(
        """
        |    /**
        |     * @deprecated property deprecation message
        |     */
        |    @Deprecated
        |    public ClassWithDeprecatedProperty withDeprecatedProperty(long deprecatedProperty) {
        |      return new ClassWithDeprecatedProperty(deprecatedProperty);
        |    }
      """
          .trimMargin()
      )
  }

  @Test
  fun `deprecated property's getter with message`() {
    val javaCode =
      generateJavaCode(
        """
      class ClassWithDeprecatedProperty {
         @Deprecated { message = "property deprecation message" } 
         deprecatedProperty: Int = 1337
      }
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(generateGetters = true, generateJavadoc = true),
      )
    assertThat(javaCode)
      .contains(
        """
        |  public static final class ClassWithDeprecatedProperty {
        |    private final long deprecatedProperty;
      """
          .trimMargin()
      )
      .contains(
        """
        |    /**
        |     * @deprecated property deprecation message
        |     */
        |    @Deprecated
        |    public long getDeprecatedProperty() {
        |      return deprecatedProperty;
        |    }
      """
          .trimMargin()
      )
      .contains(
        """
        |    /**
        |     * @deprecated property deprecation message
        |     */
        |    @Deprecated
        |    public ClassWithDeprecatedProperty withDeprecatedProperty(long deprecatedProperty) {
        |      return new ClassWithDeprecatedProperty(deprecatedProperty);
        |    }
      """
          .trimMargin()
      )
  }

  @Test
  fun `deprecated class with message`() {
    val javaCode =
      generateJavaCode(
        """
      @Deprecated { message = "class deprecation message" }
      class DeprecatedClass {
        propertyOfDeprecatedClass: Int = 42
      }
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(generateJavadoc = true),
      )
    assertThat(javaCode)
      .contains(
        """
      |  /**
      |   * @deprecated class deprecation message
      |   */
      |  @Deprecated
      |  public static final class DeprecatedClass {
    """
          .trimMargin()
      )
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `deprecated module class with message`(generateJavadoc: Boolean) {
    val javaCode =
      generateJavaCode(
        """
      @Deprecated{ message = "module class deprecation message" }
      module DeprecatedModule
      
      propertyInDeprecatedModuleClass : Int = 42
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(generateJavadoc = generateJavadoc),
      )

    assertThat(javaCode)
      .contains(
        """
      |@Deprecated
      |public final class DeprecatedModule {
    """
          .trimMargin()
      )

    if (generateJavadoc) {
      assertThat(javaCode)
        .contains(
          """
        |/**
        | * @deprecated module class deprecation message
        | */
      """
            .trimMargin()
        )
    } else {
      assertThat(javaCode).doesNotContain("* @deprecated")
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `deprecated property`(generateJavadoc: Boolean) {
    val javaCode =
      generateJavaCode(
        """
      class ClassWithDeprecatedProperty {
         @Deprecated
         deprecatedProperty: Int = 1337
      }
    """
          .trimIndent(),
        // no message, so no Javadoc, regardless of flag
        JavaCodeGeneratorOptions(generateJavadoc = generateJavadoc),
      )

    assertThat(javaCode)
      .contains(
        """
        |  public static final class ClassWithDeprecatedProperty {
        |    @Deprecated
        |    public final long deprecatedProperty;
      """
          .trimMargin()
      )
      .contains(
        """
        |    @Deprecated
        |    public ClassWithDeprecatedProperty withDeprecatedProperty(long deprecatedProperty) {
        |      return new ClassWithDeprecatedProperty(deprecatedProperty);
        |    }
      """
          .trimMargin()
      )
      .doesNotContain("* @deprecated")
  }

  @Test
  fun `deprecated property's getter`() {
    val javaCode =
      generateJavaCode(
        """
        class ClassWithDeprecatedProperty {
           @Deprecated
           deprecatedProperty: Int = 1337
        }
      """
          .trimIndent(),
        JavaCodeGeneratorOptions(generateGetters = true),
      )

    assertThat(javaCode)
      .contains(
        """
        |  public static final class ClassWithDeprecatedProperty {
        |    private final long deprecatedProperty;
      """
          .trimMargin()
      )
      .contains(
        """
        |    @Deprecated
        |    public long getDeprecatedProperty() {
        |      return deprecatedProperty;
        |    }
      """
          .trimMargin()
      )
      .contains(
        """
        |    @Deprecated
        |    public ClassWithDeprecatedProperty withDeprecatedProperty(long deprecatedProperty) {
        |      return new ClassWithDeprecatedProperty(deprecatedProperty);
        |    }
      """
          .trimMargin()
      )
      .doesNotContain("* @deprecated")
  }

  @Test
  fun `deprecated class`() {
    val javaCode =
      generateJavaCode(
        """
      @Deprecated
      class DeprecatedClass {
        propertyOfDeprecatedClass: Int = 42
      }
    """
          .trimIndent()
      )

    assertThat(javaCode)
      .contains(
        """
        |  @Deprecated
        |  public static final class DeprecatedClass {
      """
          .trimMargin()
      )
      .doesNotContain("* @deprecated")
  }

  @Test
  fun `deprecated module class`() {
    val javaCode =
      generateJavaCode(
        """
      @Deprecated
      module DeprecatedModule
      
      propertyInDeprecatedModuleClass : Int = 42
    """
          .trimIndent()
      )

    assertThat(javaCode)
      .contains(
        """
        |@Deprecated
        |public final class DeprecatedModule {
      """
          .trimMargin()
      )
      .doesNotContain("* @deprecated")
  }

  @Test
  fun `deprecation with message and doc comment on the same property`() {
    val javaCode =
      generateJavaCode(
        """
      /// Documenting deprecatedProperty
      @Deprecated { message = "property is deprecated" }
      deprecatedProperty: Int
    """,
        JavaCodeGeneratorOptions(generateJavadoc = true),
      )

    assertThat(javaCode)
      .contains(
        """
        |  /**
        |   * Documenting deprecatedProperty
        |   *
        |   * @deprecated property is deprecated
        |   */
        |  @Deprecated
        |  public final long deprecatedProperty;
      """
          .trimMargin()
      )
  }

  @Test
  fun properties() {
    val (other, propertyTypes) = instantiateOtherAndPropertyTypes()

    assertThat(readProperty(other, "name")).isEqualTo("pigeon")
    assertThat(readProperty(propertyTypes, "_boolean")).isEqualTo(true)
    assertThat(readProperty(propertyTypes, "_int")).isEqualTo(42L)
    assertThat(readProperty(propertyTypes, "_float")).isEqualTo(42.3)
    assertThat(readProperty(propertyTypes, "string")).isEqualTo("string")
    assertThat(readProperty(propertyTypes, "duration"))
      .isEqualTo(Duration(5.0, DurationUnit.MINUTES))
    assertThat(readProperty(propertyTypes, "dataSize"))
      .isEqualTo(DataSize(3.0, DataSizeUnit.GIGABYTES))
    assertThat(readProperty(propertyTypes, "nullable")).isEqualTo("idea")
    assertThat(readProperty(propertyTypes, "nullable2")).isEqualTo(null as String?)
    assertThat(readProperty(propertyTypes, "list")).isEqualTo(listOf(1, 2, 3))
    assertThat(readProperty(propertyTypes, "list2")).isEqualTo(listOf(other, other))
    assertThat(readProperty(propertyTypes, "set")).isEqualTo(setOf(1, 2, 3))
    assertThat(readProperty(propertyTypes, "set2")).isEqualTo(setOf(other))
    assertThat(readProperty(propertyTypes, "map")).isEqualTo(mapOf(1 to "one", 2 to "two"))
    assertThat(readProperty(propertyTypes, "map2")).isEqualTo(mapOf("one" to other, "two" to other))
    assertThat(readProperty(propertyTypes, "container")).isEqualTo(mapOf(1 to "one", 2 to "two"))
    assertThat(readProperty(propertyTypes, "container2"))
      .isEqualTo(mapOf("one" to other, "two" to other))
    assertThat(readProperty(propertyTypes, "other")).isEqualTo(other)
    assertThat(readProperty(propertyTypes, "regex")).isInstanceOf(Pattern::class.java)
    assertThat(readProperty(propertyTypes, "any")).isEqualTo(other)
    assertThat(readProperty(propertyTypes, "nonNull")).isEqualTo(other)
  }

  private fun readProperty(obj: Any, property: String): Any? =
    obj::class.java.getField(property).get(obj)

  @Test
  fun `properties 2`() {
    assertThat(propertyTypesSources).isEqualToResourceFile("PropertyTypes.jva")
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
        "日本-つくば" to "日本_つくば",
      )
    val javaCode =
      generateJavaCode(
        """
      module my.mod
      typealias MyTypeAlias = ${cases.joinToString(" | ") { "\"${it.first}\"" }}
    """
          .trimIndent()
      )
    val javaClass = javaCode.compile().getValue("my.Mod\$MyTypeAlias")

    assertThat(javaClass.enumConstants.size)
      .isEqualTo(cases.size) // make sure zip doesn't drop cases

    assertAll(
      "generated enum constants have correct names",
      javaClass.declaredFields.zip(cases) { field, (_, kotlinName) ->
        {
          assertThat(field.name).isEqualTo(kotlinName)
          Unit
        }
      },
    )

    assertAll(
      "toString() returns Pkl name",
      javaClass.enumConstants.zip(cases) { enumConstant, (pklName, _) ->
        {
          assertThat(enumConstant.toString()).isEqualTo(pklName)
          Unit
        }
      },
    )
  }

  @Test
  fun `conflicting enum constant names`() {
    val exception =
      assertThrows<JavaCodeGeneratorException> {
        generateJavaCode(
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
      assertThrows<JavaCodeGeneratorException> {
        generateJavaCode(
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
      assertThrows<JavaCodeGeneratorException> {
        generateJavaCode(
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
  fun `recursive types`() {
    val javaCode =
      generateJavaCode(
        """
      module my.mod

      class Foo {
        other: Int
        bar: Bar
      }
      class Bar {
        foo: Foo
        other: String
      }
    """
          .trimIndent()
      )

    assertThat(javaCode)
      .compilesSuccessfully()
      .contains(
        """
        |  public static final class Foo {
        |    public final long other;
        |
        |    public final @NonNull Bar bar;
        |
        |    public Foo(@Named("other") long other, @Named("bar") @NonNull Bar bar) {
        |      this.other = other;
        |      this.bar = bar;
        |    }
      """
          .trimMargin()
      )
      .contains(
        """
        |  public static final class Bar {
        |    public final @NonNull Foo foo;
        |
        |    public final @NonNull String other;
        |
        |    public Bar(@Named("foo") @NonNull Foo foo, @Named("other") @NonNull String other) {
        |      this.foo = foo;
        |      this.other = other;
        |    }
      """
          .trimMargin()
      )
  }

  @Test
  fun inheritance() {
    val javaCode =
      generateJavaCode(
        """
      module my.mod

      abstract class Foo {
        one: Int
      }
      open class None extends Foo {}
      open class Bar extends None {
        two: String?
      }
      class Baz extends Bar {
        three: Duration
      }
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(generateGetters = true),
      )

    assertThat(javaCode)
      .compilesSuccessfully()
      .contains(
        """
        |  public abstract static class Foo {
        |    protected final long one;
        |
        |    protected Foo(@Named("one") long one) {
        |      this.one = one;
        |    }
      """
          .trimMargin()
      )
      .contains(
        """
        |  public static class None extends Foo {
        |    public None(@Named("one") long one) {
        |      super(one);
        |    }
      """
          .trimMargin()
      )
      .contains(
        """
        |  public static class Bar extends None {
        |    protected final String two;
        |
        |    public Bar(@Named("one") long one, @Named("two") String two) {
        |      super(one);
        |      this.two = two;
        |    }
      """
          .trimMargin()
      )
      .isEqualToResourceFile("Inheritance.jva")
  }

  @Test
  fun `stateless classes`() {
    val javaCode =
      generateJavaCode(
        """
      module my.mod

      class Foo
      abstract class Bar
      class Baz extends Bar
    """
          .trimIndent()
      )

    assertThat(javaCode)
      .contains(
        """
        |  public static final class Foo {
        |    public Foo() {
        |    }
      """
          .trimMargin()
      )
      .contains(
        """
        |  public abstract static class Bar {
        |    protected Bar() {
        |    }
      """
          .trimMargin()
      )
      .contains(
        """
        |  public static final class Baz extends Bar {
        |    public Baz() {
        |    }
      """
          .trimMargin()
      )
  }

  @Test
  fun `stateless module classes`() {
    var javaCode = generateJavaCode("module my.mod")
    assertThat(javaCode)
      .contains(
        """
        |public final class Mod {
        |  private Mod() {
        |  }
      """
          .trimMargin()
      )

    javaCode = generateJavaCode("abstract module my.mod")
    assertThat(javaCode)
      .contains(
        """
      |public abstract class Mod {
      |  protected Mod() {
      |  }
    """
          .trimMargin()
      )

    javaCode = generateJavaCode("open module my.mod")
    assertThat(javaCode)
      .contains(
        """
      |public class Mod {
      |  public Mod() {
      |  }
    """
          .trimMargin()
      )
  }

  @Test
  fun `reserved words`() {
    val props = javaReservedWords.joinToString("\n") { "`$it`: Int" }

    val fooClass =
      generateJavaCode(
          """
      module my.mod

      class Foo {
        $props
      }
    """
            .trimIndent()
        )
        .compile()
        .getValue("my.Mod\$Foo")

    assertThat(fooClass.declaredFields).allSatisfy(Consumer { it.name.startsWith("_") })
  }

  @Test
  fun getters() {
    val javaCode =
      generateJavaCode(
        """
      module my.mod

      class GenerateGetters {
        urgent: Boolean = true
        url: String = "https://apple.com"
        diskSize: DataSize = 4.mb
        ETA: Duration = 3.s
        package: String
      }
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(generateGetters = true),
      )

    assertThat(javaCode).compilesSuccessfully().isEqualToResourceFile("GenerateGetters.jva")
  }

  @Test
  fun `'with' methods`() {
    val javaCode =
      generateJavaCode(
        """
      module my.mod

      abstract class Foo {
        x: Int
      }
      class Bar extends Foo {
        y: String
      }
    """
      )

    assertThat(javaCode)
      .compilesSuccessfully()
      .contains(
        """
        |    public Bar withX(long x) {
        |      return new Bar(x, y);
        |    }
      """
          .trimMargin()
      )
      .contains(
        """
        |    public Bar withY(@NonNull String y) {
        |      return new Bar(x, y);
        |    }
      """
          .trimMargin()
      )
      .doesNotContain("public Foo withX") // because `Foo` is abstract
  }

  @Test
  fun `module class`() {
    val javaCode =
      generateJavaCode(
        """
      module my.mod
      
      pigeon: String
      parrot: String
    """
          .trimIndent()
      )

    assertThat(javaCode)
      .contains(
        """
      |public final class Mod {
      |  public final @NonNull String pigeon;
      |
      |  public final @NonNull String parrot;
    """
          .trimMargin()
      )
  }

  @Test
  fun `hidden properties`() {
    val javaCode =
      generateJavaCode(
        """
      hidden pigeon1: String
      parrot1: String

      class Persons {
        hidden pigeon2: String
        parrot2: String
      }
    """
          .trimIndent()
      )

    assertThat(javaCode)
      .doesNotContain("final String pigeon1")
      .contains("final @NonNull String parrot1")
      .doesNotContain("final String pigeon2")
      .contains("final @NonNull String parrot2")
  }

  @Test
  fun javadoc() {
    val javaCode =
      generateJavaCode(
        """
      /// module comment.
      /// *emphasized* `code`.
      module my.mod

      /// module property comment.
      /// *emphasized* `code`.
      pigeon: Person

      /// class comment.
      /// *emphasized* `code`.
      class Person {
        /// class property comment.
        /// *emphasized* `code`.
        name: String
      }
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(generateJavadoc = true),
      )

    assertThat(javaCode).compilesSuccessfully().isEqualToResourceFile("Javadoc.jva")
  }

  @Test
  fun `javadoc 2`() {
    val javaCode =
      generateJavaCode(
        """
      module my.mod

      /// module property comment.
      /// can contain /* and */ characters.
      pigeon: Person

      class Person {
        /// class property comment.
        /// can contain /* and */ characters.
        name: String
      }
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(generateGetters = true, generateJavadoc = true),
      )

    assertThat(javaCode)
      .compilesSuccessfully()
      .contains(
        """
        |  /**
        |   * module property comment.
        |   * can contain /* and *&#47; characters.
        |   */
        |  public @NonNull Person getPigeon() {
      """
          .trimMargin()
      )
      .contains(
        """
        |    /**
        |     * class property comment.
        |     * can contain /* and *&#47; characters.
        |     */
        |    public @NonNull String getName() {
      """
          .trimMargin()
      )
  }

  @Test
  fun `pkl_base type aliases`() {
    val javaCode =
      generateJavaCode(
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

    assertThat(javaCode)
      .compilesSuccessfully()
      .contains(
        """
        |public final class Mod {
        |  public final short uint8;
        |
        |  public final int uint16;
        |
        |  public final long uint32;
        |
        |  public final long uint;
        |
        |  public final byte int8;
        |
        |  public final short int16;
        |
        |  public final int int32;
        |
        |  public final @NonNull URI uri;
      """
          .trimMargin()
      )
      .contains(
        """
        |  public final @NonNull Pair<@NonNull Short, @NonNull Integer> pair;
        |
        |  public final @NonNull List<@NonNull Long> list;
        |
        |  public final @NonNull Set<@NonNull Long> set;
        |
        |  public final @NonNull Map<@NonNull Byte, @NonNull Short> map;
        |
        |  public final @NonNull List<@NonNull Integer> listing;
        |
        |  public final @NonNull Map<@NonNull URI, @NonNull Short> mapping;
        |
        |  public final Integer nullable;
      """
          .trimMargin()
      )
      .contains(
        """
        |  public static final class Foo {
        |    public final short uint8;
        |
        |    public final int uint16;
        |
        |    public final long uint32;
        |
        |    public final long uint;
        |
        |    public final byte int8;
        |
        |    public final short int16;
        |
        |    public final int int32;
        |
        |    public final @NonNull URI uri;
        |
        |    public final @NonNull List<@NonNull Long> list;
      """
          .trimMargin()
      )
  }

  @Test
  fun `nullable properties`() {
    var javaCode =
      generateJavaCode(
        """
      module mod
      
      foo: String
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(nonNullAnnotation = "com.example.Annotations\$NonNull"),
      )

    assertThat(javaCode)
      .contains("import com.example.Annotations;")
      .contains("public final @Annotations.NonNull String foo;")

    javaCode =
      generateJavaCode(
        """
      module mod
      
      foo: Int
      bar: Int?
      baz: Any
      qux: String
      foo2: List<String>?
      bar2: List<String?>
      baz2: List<String>
      qux2: List<Int>
    """
          .trimIndent()
      )

    assertThat(javaCode)
      .contains(
        """
      |public final class Mod {
      |  public final long foo;
      |
      |  public final Long bar;
      |
      |  public final Object baz;
      |
      |  public final @NonNull String qux;
      |
      |  public final List<@NonNull String> foo2;
      |
      |  public final @NonNull List<String> bar2;
      |
      |  public final @NonNull List<@NonNull String> baz2;
      |
      |  public final @NonNull List<@NonNull Long> qux2;
    """
          .trimMargin()
      )
  }

  @Test
  fun `user defined type aliases`() {
    val javaCode =
      generateJavaCode(
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
      )

    assertThat(javaCode)
      .compilesSuccessfully()
      .contains(
        """
        |public final class Mod {
        |  public final @NonNull String simple;
        |
        |  public final @NonNull String constrained;
        |
        |  public final @NonNull List<@NonNull Long> parameterized;
        |
        |  public final @NonNull List<@NonNull Long> recursive1;
        |
        |  public final @NonNull List<@NonNull String> recursive2;
      """
          .trimMargin()
      )
      .contains(
        """
        |  public static final class Foo {
        |    public final @NonNull String simple;
        |
        |    public final @NonNull String constrained;
        |
        |    public final @NonNull List<@NonNull Long> parameterized;
        |
        |    public final @NonNull List<@NonNull Long> recursive1;
        |
        |    public final @NonNull List<@NonNull String> recursive2;
      """
          .trimMargin()
      )
  }

  @Test
  fun `generic type aliases`() {
    val javaCode =
      generateJavaCode(
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

    assertThat(javaCode)
      .compilesSuccessfully()
      .contains(
        """
        |public final class Mod {
        |  public final @NonNull List<@NonNull Long> res1;
        |
        |  public final @NonNull List<@NonNull List<@NonNull String>> res2;
        |
        |  public final @NonNull Map<@NonNull Long, @NonNull String> res3;
        |
        |  public final @NonNull Map<@NonNull String, @NonNull Duration> res4;
        |
        |  public final @NonNull Map<Person, Person> res5;
        |
        |  public final @NonNull List<@NonNull Object> res6;
        |
        |  public final @NonNull Map<@NonNull Object, @NonNull Object> res7;
        |
        |  public final @NonNull Map<@NonNull String, @NonNull Object> res8;
        |
        |  public final @NonNull Map<@NonNull Object, @NonNull Object> res9;
      """
          .trimMargin()
      )
      .contains(
        """
        |  public static final class Foo {
        |    public final @NonNull List<@NonNull Long> res1;
        |
        |    public final @NonNull List<@NonNull List<@NonNull String>> res2;
        |
        |    public final @NonNull Map<@NonNull Long, @NonNull String> res3;
        |
        |    public final @NonNull Map<@NonNull String, @NonNull Duration> res4;
        |
        |    public final @NonNull Map<Person, Person> res5;
        |
        |    public final @NonNull List<@NonNull Object> res6;
        |
        |    public final @NonNull Map<@NonNull Object, @NonNull Object> res7;
        |
        |    public final @NonNull Map<@NonNull String, @NonNull Object> res8;
        |
        |    public final @NonNull Map<@NonNull Object, @NonNull Object> res9;
      """
          .trimMargin()
      )
  }

  @Test
  fun `union of string literals`() {
    val javaCode =
      generateJavaCode(
        """
      module mod

      x: "Pigeon"|"Barn Owl"|"Parrot"
    """
          .trimIndent()
      )

    assertThat(javaCode).compilesSuccessfully().contains("public final @NonNull String x;")
  }

  @Test
  fun `other union type`() {
    val e =
      assertThrows<JavaCodeGeneratorException> {
        generateJavaCode(
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
    val javaCode =
      generateJavaCode(
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

    assertThat(javaCode)
      .contains("public final @NonNull String v1;")
      .contains("public final @NonNull String v2;")
      .contains("public final @NonNull String v3;")
      .contains("public final @NonNull String v4;")
      .contains("public final @NonNull String v5;")
      .contains("public final @NonNull String v6;")
  }

  @Test
  fun `stringy type alias`() {
    val javaCode =
      generateJavaCode(
        """
      module mod

      typealias Version1 = "RELEASE"|String
      typealias Version2 = String|"RELEASE"
      typealias Version3 = "RELEASE"|String|"LATEST"
      typealias Version4 = Version3|String|"LATEST"
      typealias Version5 = (Version4|String)|("LATEST"|String)
      typealias Version6 = Version5
      
      v1: Version1
      v2: Version2
      v3: Version3
      v4: Version4
      v5: Version5
      v6: Version6
    """
          .trimIndent()
      )

    assertThat(javaCode)
      .contains("public final @NonNull String v1;")
      .contains("public final @NonNull String v2;")
      .contains("public final @NonNull String v3;")
      .contains("public final @NonNull String v4;")
      .contains("public final @NonNull String v5;")
      .contains("public final @NonNull String v6;")
  }

  @Test
  fun `custom constructor parameter annotation`() {
    val javaCode =
      generateJavaCode(
        """
      module my.mod

      name: String
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(paramsAnnotation = "org.project.MyAnnotation"),
      )

    assertThat(javaCode)
      .contains("import org.project.MyAnnotation;")
      .contains("public Mod(@MyAnnotation(\"name\") @NonNull String name)")
  }

  @Test
  fun `no constructor parameter annotation`() {
    val javaCode =
      generateJavaCode(
        """
      module my.mod

      name: String
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(paramsAnnotation = null),
      )

    assertThat(javaCode).contains("public Mod(@NonNull String name)")
  }

  @Test
  fun `spring boot config`() {
    val javaCode =
      generateJavaCode(
        """
      module my.mod

      server: Server

      class Server {
        port: Int
        urls: Listing<Uri>
      }
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(generateSpringBootConfig = true),
      )

    assertThat(javaCode)
      .contains(
        """
        |@ConfigurationProperties
        |public final class Mod {
      """
          .trimMargin()
      )
      .contains(
        """
        |  public final @NonNull Server server;
      """
          .trimMargin()
      )
      .contains(
        """
        |  @ConfigurationProperties("server")
        |  public static final class Server {
      """
          .trimMargin()
      )
      .contains(
        """
        |    public final long port;
        |
        |    public final @NonNull List<@NonNull URI> urls;
      """
          .trimMargin()
      )
      .doesNotContain("@ConstructorBinding")
      .doesNotContain("@Named")

    // not worthwhile to add spring & spring boot dependency just so that this test can compile
    // their annotations
    val javaCodeWithoutSpringAnnotations =
      javaCode.deleteLines { it.contains("ConfigurationProperties") }
    assertThat(javaCodeWithoutSpringAnnotations).compilesSuccessfully()
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
          .trimIndent(),
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
          .trimIndent(),
      )

    val javaSourceFiles = generateFiles(library, client)
    assertDoesNotThrow { InMemoryJavaCompiler.compile(javaSourceFiles.mapValues { it.value.text }) }

    val javaClientCode =
      javaSourceFiles.entries.find { (fileName, _) -> fileName.endsWith("Client.java") }!!.value
    assertThat(javaClientCode)
      .contains(
        """
        |public final class Client {
        |  public final @NonNull Library lib;
        |
        |  public final Library. @NonNull Person parrot;
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
          .trimIndent(),
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
          .trimIndent(),
      )

    val javaSourceFiles = generateFiles(base, derived)
    assertDoesNotThrow { InMemoryJavaCompiler.compile(javaSourceFiles.mapValues { it.value.text }) }

    val javaDerivedCode =
      javaSourceFiles.entries.find { (filename, _) -> filename.endsWith("Derived.java") }!!.value
    assertThat(javaDerivedCode)
      .contains(
        """
        |public final class Derived extends Base {
        |  public final Base. @NonNull Person person1;
        |
        |  public final @NonNull Person2 person2;
      """
          .trimMargin()
      )
  }

  @Test
  fun `empty module`() {
    val javaCode = generateJavaCode("module mod")
    assertThat(javaCode).contains("public final class Mod {")
  }

  @Test
  fun `extend module that only contains type aliases`() {
    val base =
      PklModule(
        "base",
        """
          abstract module base
    
          typealias Version = "LATEST"|String
        """
          .trimIndent(),
      )

    val derived =
      PklModule(
        "derived",
        """
          module derived
          
          extends "base.pkl"
          
          v: Version = "1.2.3"
        """
          .trimIndent(),
      )

    val javaSourceFiles = generateFiles(base, derived)
    assertDoesNotThrow { InMemoryJavaCompiler.compile(javaSourceFiles.mapValues { it.value.text }) }

    val javaDerivedCode =
      javaSourceFiles.entries.find { (filename, _) -> filename.endsWith("Derived.java") }!!.value
    assertThat(javaDerivedCode)
      .contains(
        """
        |public final class Derived extends Base {
        |  public final @NonNull String v;
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
          .trimIndent(),
      )
    val generated = generateFiles(pklModule)
    val expectedPropertyFile =
      "resources/META-INF/org/pkl/config/java/mapper/classes/org.pkl.Mod.properties"
    assertThat(generated).containsKey(expectedPropertyFile)
    val generatedFile = generated[expectedPropertyFile]!!
    assertThat(generatedFile)
      .contains("org.pkl.config.java.mapper.org.pkl.Mod\\#ModuleClass=org.pkl.Mod")
      .contains("org.pkl.config.java.mapper.org.pkl.Mod\\#Foo=org.pkl.Mod\$Foo")
      .contains("org.pkl.config.java.mapper.org.pkl.Mod\\#Bar=org.pkl.Mod\$Bar")
  }

  @Test
  fun `generated properties files with normalized java name`() {
    val pklModule =
      PklModule(
        "mod.pkl",
        """
          module my.mod
    
          foo: Foo
    
          bar: Bar
    
          class Foo {
            prop: String
          }
    
          class Bar {
            prop: Int
          }
        """
          .trimIndent(),
      )
    val generated = generateFiles(pklModule)
    val expectedPropertyFile =
      "resources/META-INF/org/pkl/config/java/mapper/classes/my.mod.properties"
    assertThat(generated).containsKey(expectedPropertyFile)
    val generatedFile = generated[expectedPropertyFile]!!
    assertThat(generatedFile)
      .contains("org.pkl.config.java.mapper.my.mod\\#ModuleClass=my.Mod")
      .contains("org.pkl.config.java.mapper.my.mod\\#Foo=my.Mod\$Foo")
      .contains("org.pkl.config.java.mapper.my.mod\\#Bar=my.Mod\$Bar")
  }

  @Test
  fun `generates serializable classes`() {
    val javaCode =
      generateJavaCode(
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
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(implementSerializable = true),
      )

    assertThat(javaCode)
      .contains("implements Serializable")
      .contains("private static final long serialVersionUID = 0L;")

    val classes = javaCode.compile()

    val smallStructCtor = classes.getValue("Mod\$SmallStruct").constructors.first()
    val smallStruct = smallStructCtor.newInstance("pigeon")

    val enumClass = classes.getValue("Mod\$Direction")
    val enumValue = enumClass.enumConstants.first()

    val bigStructCtor = classes.getValue("Mod\$BigStruct").constructors.first()
    val bigStruct =
      bigStructCtor.newInstance(
        true,
        42L,
        42.3,
        "string",
        Duration(5.0, DurationUnit.MINUTES),
        DataSize(3.0, DataSizeUnit.GIGABYTES),
        Pair(1, 2),
        Pair("pigeon", smallStruct),
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
        Pattern.compile("(i?)\\w*"),
        smallStruct,
        enumValue,
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
  fun `non-instantiable classes aren't made serializable`() {
    var javaCode =
      generateJavaCode(
        """
      module my.mod
      abstract class Foo { str: String }
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(implementSerializable = true),
      )

    assertThat(javaCode).doesNotContain("Serializable")

    javaCode =
      generateJavaCode(
        """
      module my.mod
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(implementSerializable = true),
      )

    assertThat(javaCode).doesNotContain("Serializable")
  }

  @Test
  fun `generates serializable module classes`() {
    val javaCode =
      generateJavaCode(
        """
      module Person
      name: String
      address: Address
      class Address { city: String }
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(implementSerializable = true),
      )

    assertThat(javaCode)
      .contains(
        """
        |public final class Person implements Serializable {
        |  private static final long serialVersionUID = 0L;
      """
          .trimMargin()
      )
      .contains(
        """
        |  public static final class Address implements Serializable {
        |    private static final long serialVersionUID = 0L;
      """
          .trimMargin()
      )
  }

  @Test
  fun `override property type`() {
    val javaCode =
      generateJavaCode(
        """
      module my.mod

      open class Foo

      class TheFoo extends Foo {
        fooProp: String
      }

      open class OpenClass {
        prop: Foo
      }

      class TheClass extends OpenClass {
        prop: TheFoo
      }
    """
          .trimIndent()
      )

    assertThat(javaCode)
      .compilesSuccessfully()
      .contains(
        """
        |  public static final class TheClass extends OpenClass {
        |    public final @NonNull TheFoo prop;
        |
        |    public TheClass(@Named("prop") @NonNull TheFoo prop) {
        |      super(prop);
        |      this.prop = prop;
        |    }
        |
        |    public TheClass withProp(@NonNull TheFoo prop) {
        |      return new TheClass(prop);
        |    }
      """
          .trimMargin()
      )
  }

  @Test
  fun `override property type, with getters`() {
    val javaCode =
      generateJavaCode(
        """
      module my.mod

      open class Foo

      class TheFoo extends Foo {
        fooProp: String
      }

      open class OpenClass {
        prop: Foo
      }

      class TheClass extends OpenClass {
        prop: TheFoo
      }
    """
          .trimIndent(),
        JavaCodeGeneratorOptions(generateGetters = true),
      )

    assertThat(javaCode)
      .compilesSuccessfully()
      .contains(
        """
        |  public static final class TheClass extends OpenClass {
        |    private final @NonNull TheFoo prop;
        |
        |    public TheClass(@Named("prop") @NonNull TheFoo prop) {
        |      super(prop);
        |      this.prop = prop;
        |    }
        |
        |    @Override
        |    public @NonNull TheFoo getProp() {
        |      return prop;
        |    }
      """
          .trimMargin()
      )
  }

  @Test
  fun `override names in a standalone module`() {
    val files =
      JavaCodeGeneratorOptions(
          renames = mapOf("a.b.c." to "x.y.z.", "d.e.f.AnotherModule" to "u.v.w.RenamedModule")
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
              .trimIndent(),
        )
        .toMutableMap()

    files.validateContents(
      "java/x/y/z/MyModule.java" to listOf("package x.y.z;", "public final class MyModule {"),
      "$MAPPER_PREFIX/a.b.c.MyModule.properties" to
        listOf("org.pkl.config.java.mapper.a.b.c.MyModule\\#ModuleClass=x.y.z.MyModule"),
      // ---
      "java/u/v/w/RenamedModule.java" to
        listOf("package u.v.w;", "public final class RenamedModule {"),
      "$MAPPER_PREFIX/d.e.f.AnotherModule.properties" to
        listOf("org.pkl.config.java.mapper.d.e.f.AnotherModule\\#ModuleClass=u.v.w.RenamedModule"),
    )
  }

  @Test
  fun `override names based on the longest prefix`() {
    val files =
      JavaCodeGeneratorOptions(
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
              .trimIndent(),
        )

    files.validateContents(
      "java/x/Module1.java" to listOf("package x;", "public final class Module1 {"),
      "$MAPPER_PREFIX/com.foo.bar.Module1.properties" to
        listOf("org.pkl.config.java.mapper.com.foo.bar.Module1\\#ModuleClass=x.Module1"),
      // ---
      "java/z/Module2.java" to listOf("package z;", "public final class Module2 {"),
      "$MAPPER_PREFIX/com.Module2.properties" to
        listOf("org.pkl.config.java.mapper.com.Module2\\#ModuleClass=z.Module2"),
      // ---
      "java/w/org/baz/Module3.java" to listOf("package w.org.baz;", "public final class Module3 {"),
      "$MAPPER_PREFIX/org.baz.Module3.properties" to
        listOf("org.pkl.config.java.mapper.org.baz.Module3\\#ModuleClass=w.org.baz.Module3"),
    )
  }

  @Test
  fun `override names in multiple modules using each other`() {
    val files =
      JavaCodeGeneratorOptions(
          renames =
            mapOf(
              "org.foo." to "com.foo.x.",
              "org.bar.Module2" to "org.bar.RenamedModule",
              "org.baz." to "com.baz.a.b.",
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
              .trimIndent(),
        )

    files.validateContents(
      "java/com/foo/x/Module1.java" to listOf("package com.foo.x;", "public final class Module1 {"),
      "$MAPPER_PREFIX/org.foo.Module1.properties" to
        listOf(
          "org.pkl.config.java.mapper.org.foo.Module1\\#ModuleClass=com.foo.x.Module1",
          "org.pkl.config.java.mapper.org.foo.Module1\\#Person=com.foo.x.Module1${'$'}Person",
        ),
      // ---
      "java/org/bar/RenamedModule.java" to
        listOf(
          "package org.bar;",
          "import com.foo.x.Module1;",
          "public final class RenamedModule {",
          "public final Module1. @NonNull Person owner;",
        ),
      "$MAPPER_PREFIX/org.bar.Module2.properties" to
        listOf(
          "org.pkl.config.java.mapper.org.bar.Module2\\#ModuleClass=org.bar.RenamedModule",
          "org.pkl.config.java.mapper.org.bar.Module2\\#Group=org.bar.RenamedModule${'$'}Group",
        ),
      // ---
      "java/com/baz/a/b/Module3.java" to
        listOf(
          "package com.baz.a.b;",
          "import org.bar.RenamedModule;",
          "public final class Module3 {",
          "public final RenamedModule. @NonNull Group owner;",
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
      JavaCodeGeneratorOptions(
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
              .trimIndent(),
        )

    files.validateContents(
      "java/x/y/z/renamed_module.java" to
        listOf("package x.y.z;", "public final class renamed_module {"),
      "$MAPPER_PREFIX/a.b.c.MyModule.properties" to
        listOf("org.pkl.config.java.mapper.a.b.c.MyModule\\#ModuleClass=x.y.z.renamed_module"),
      // ---
      "java/u/v/w/Lower_module.java" to
        listOf("package u.v.w;", "public final class Lower_module {"),
      "$MAPPER_PREFIX/d.e.f.lower_module.properties" to
        listOf("org.pkl.config.java.mapper.d.e.f.lower_module\\#ModuleClass=u.v.w.Lower_module"),
    )
  }

  @Test
  fun `equals,hashCode,toString work correctly for class that doesn't declare properties`() {
    val javaCode =
      generateJavaCode(
        """
      module my.mod

      open class Foo {
        name: String
      }
      
      class Bar extends Foo {}
    """
          .trimIndent()
      )

    val classes = javaCode.compile()
    val fooClass = classes.getValue("my.Mod\$Foo")
    val foo1 = fooClass.getDeclaredConstructor(String::class.java).newInstance("name1")
    val barClass = classes.getValue("my.Mod\$Bar")
    val bar1 = barClass.getDeclaredConstructor(String::class.java).newInstance("name1")
    val anotherBar1 = barClass.getDeclaredConstructor(String::class.java).newInstance("name1")
    val bar2 = barClass.getDeclaredConstructor(String::class.java).newInstance("name2")

    assertThat(bar1)
      .isEqualTo(bar1)
      .isEqualTo(anotherBar1)
      .isNotEqualTo(bar2)
      .isNotEqualTo(foo1)
      .hasSameHashCodeAs(bar1)
      .hasSameHashCodeAs(anotherBar1)
    assertThat(bar1.toString())
      .isEqualTo(
        """
        Bar {
          name = name1
        }
      """
          .trimIndent()
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

  private fun JavaCodeGeneratorOptions.generateFiles(
    vararg pklModules: PklModule
  ): Map<String, String> {
    val pklFiles = pklModules.map { it.writeToDisk(tempDir.resolve("pkl/${it.name}.pkl")) }
    val evaluator = Evaluator.preconfigured()
    return pklFiles.fold(mapOf()) { acc, pklFile ->
      val pklSchema = evaluator.evaluateSchema(path(pklFile))
      val generator = JavaCodeGenerator(pklSchema, this)
      acc + generator.output
    }
  }

  private fun JavaCodeGeneratorOptions.generateFiles(
    vararg pklModules: kotlin.Pair<String, String>
  ): Map<String, String> =
    generateFiles(*pklModules.map { (name, text) -> PklModule(name, text) }.toTypedArray())

  private fun generateFiles(vararg pklModules: PklModule): Map<String, JavaSourceCode> =
    JavaCodeGeneratorOptions().generateFiles(*pklModules).mapValues { JavaSourceCode(it.value) }

  private fun instantiateOtherAndPropertyTypes(): kotlin.Pair<Any, Any> {
    val otherCtor = propertyTypesClasses.getValue("my.Mod\$Other").constructors.first()
    val other = otherCtor.newInstance("pigeon")

    val enumClass = propertyTypesClasses.getValue("my.Mod\$Direction")
    val enumValue = enumClass.enumConstants.first()

    val propertyTypesCtor =
      propertyTypesClasses.getValue("my.Mod\$PropertyTypes").constructors.first()
    val propertyTypes =
      propertyTypesCtor.newInstance(
        true,
        42,
        42.3,
        "string",
        Duration(5.0, DurationUnit.MINUTES),
        DurationUnit.MINUTES,
        DataSize(3.0, DataSizeUnit.GIGABYTES),
        DataSizeUnit.GIGABYTES,
        "idea",
        (null as String?),
        Pair(1, 2),
        Pair("pigeon", other),
        listOf(1, 2, 3),
        listOf(other, other),
        listOf(1, 2, 3),
        listOf(other, other),
        setOf(1, 2, 3),
        setOf(other, other),
        mapOf(1 to "one", 2 to "two"),
        mapOf("one" to other, "two" to other),
        mapOf(1 to "one", 2 to "two"),
        mapOf("one" to other, "two" to other),
        other,
        Pattern.compile("(i?)\\w*"),
        other,
        other,
        enumValue,
      )

    return other to propertyTypes
  }

  private fun assertThat(actual: JavaSourceCode): JavaSourceCodeAssert =
    JavaSourceCodeAssert(actual)

  private data class JavaSourceCode(val text: String) {
    fun compile(): Map<String, Class<*>> =
      InMemoryJavaCompiler.compile(mapOf("/org/Mod.java" to text))

    fun deleteLines(predicate: (String) -> Boolean): JavaSourceCode =
      JavaSourceCode(text.lines().filterNot(predicate).joinToString("\n"))
  }

  private class JavaSourceCodeAssert(actual: JavaSourceCode) :
    AbstractAssert<JavaSourceCodeAssert, JavaSourceCode>(actual, JavaSourceCodeAssert::class.java) {
    fun contains(expected: String): JavaSourceCodeAssert {
      if (!actual.text.contains(expected)) {
        // check for equality to get better error output (IDE diff dialog)
        assertThat(actual.text).isEqualTo(expected)
      }
      return this
    }

    fun doesNotContain(expected: String): JavaSourceCodeAssert {
      assertThat(actual.text).doesNotContain(expected)
      return this
    }

    fun compilesSuccessfully(): JavaSourceCodeAssert {
      assertThatCode { actual.compile() }.doesNotThrowAnyException()
      return this
    }

    fun isEqualTo(expected: String): JavaSourceCodeAssert {
      assertThat(actual.text).isEqualTo(expected)
      return this
    }

    fun isEqualToResourceFile(fileName: String): JavaSourceCodeAssert {
      isEqualTo(IoUtils.readClassPathResourceAsString(javaClass, fileName))
      return this
    }
  }
}
