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
import java.util.function.BiPredicate
import java.util.function.Consumer
import java.util.regex.Pattern
import org.assertj.core.api.AbstractCharSequenceAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assumptions.assumeThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.Parameter
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.pkl.core.*
import org.pkl.core.ModuleSource.path
import org.pkl.core.ModuleSource.text

@Tag("generate-records")
@ParameterizedClass
@EnumSource
class JavaRecordCodeGeneratorTest {

  enum class JavaRecordGenerationOption(
    val generateRecords: Boolean = true,
    val useWithers: Boolean = false,
    val useLombokBuilders: Boolean = false,
    val interpolated: Regex,
  ) {
    PLAIN(true, false, false, Regex("<0<(.+?)>0>", RegexOption.DOT_MATCHES_ALL)),
    WITHERS(true, true, false, Regex("<1<(.+?)>1>", RegexOption.DOT_MATCHES_ALL)),
    LOMBOK_BUILDERS(true, false, true, Regex("<2<(.+?)>2>", RegexOption.DOT_MATCHES_ALL));

    fun interpolate(input: CharSequence) = run {
      val current = this
      entries.asSequence().fold(input) { acc, v ->
        when (v) {
          current -> v.interpolated.replace(acc, "$1")
          else -> v.interpolated.replace(acc, "")
        }
      }
    }
  }

  @Parameter lateinit var recordGenOption: JavaRecordGenerationOption

  companion object {
    private const val MAPPER_PREFIX = "resources/META-INF/org/pkl/config/java/mapper/classes"

    private fun generateCommonCode(options: JavaCodeGeneratorOptions = JavaCodeGeneratorOptions()) =
      JavaRecordCodeGenerator.generateCommonCode(options)

    private val commonCodeSource: kotlin.Pair<String, String> =
      "org/pkl/codegen/java/common/code/Wither.java" to generateCommonCode()

    private fun inMemoryCompile(sourceFiles: Map<String, String>): Map<String, Class<*>> {
      return InMemoryJavaCompiler.compile(sourceFiles + commonCodeSource)
    }
  }

  private fun CharSequence.interpolate(): CharSequence = recordGenOption.interpolate(this)

  //  private fun expectedMatcher(s: String): String {
  //    val witherRegex = Regex("""record\\s+\\w+\\s*\(.+?\\s+implements.+?(?:,\\s*)?Wither<.+>""")
  //    val lombokRegex =
  //      Regex("""@Builder\\s+(?:@\\w+\(.+?\)\\s+)*(?:public\\s*)?record\\s+\\w+\\s*\(""")
  //    val plainRegex = Regex("""record\\s+\\w+\\s*\(""")
  //    return ""
  //  }

  private val simpleClass: Class<*> by lazy {
    generateJavaCode(
        """
        module my.mod

        class Simple {
          str: String
          list: List<Int>
        }
      """
          .trimIndent(),
        javaCodeGeneratorOptions(),
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
      """,
      javaCodeGeneratorOptions(),
    )
  }

  private val propertyTypesClasses: Map<String, Class<*>> by lazy { propertyTypesSources.compile() }

  private fun generateJavaCode(
    pklCode: String,
    options: JavaCodeGeneratorOptions = javaCodeGeneratorOptions(),
  ): JavaSourceCode {
    val module = Evaluator.preconfigured().evaluateSchema(text(pklCode))
    val generator = JavaRecordCodeGenerator(module, options)
    return generator
      .getJavaFiles()
      .also { check(it.size == 1) { "code generation only for a non-open module" } }
      .values
      .first()
      .let { JavaSourceCode(it) }
  }

  private fun generateJavaCodeForOpenModule(
    pklCode: String,
    options: JavaCodeGeneratorOptions = javaCodeGeneratorOptions(),
  ): List<JavaSourceCode> {
    val module = Evaluator.preconfigured().evaluateSchema(text(pklCode))
    val generator = JavaRecordCodeGenerator(module, options)
    return generator.getJavaFiles().map { (_, v) -> JavaSourceCode(v) }
  }

  private fun javaCodeGeneratorOptions(
    option: JavaRecordGenerationOption = recordGenOption,
    indent: String = "  ",
    generateGetters: Boolean = false,
    generateJavadoc: Boolean = false,
    generateSpringBootConfig: Boolean = false,
    paramsAnnotation: String? =
      if (generateSpringBootConfig) null else "org.pkl.config.java.mapper.Named",
    nonNullAnnotation: String? = null,
    implementSerializable: Boolean = false,
    renames: Map<String, String> = emptyMap(),
  ) =
    JavaCodeGeneratorOptions(
      generateRecords = option.generateRecords,
      useWithers = option.useWithers,
      useLombokBuilders = option.useLombokBuilders,
      renames = renames,
      generateGetters = generateGetters,
      generateJavadoc = generateJavadoc,
      generateSpringBootConfig = generateSpringBootConfig,
      implementSerializable = implementSerializable,
      paramsAnnotation = paramsAnnotation,
      nonNullAnnotation = nonNullAnnotation,
      indent = indent,
    )

  @TempDir lateinit var tempDir: Path

  @Test
  fun testCommonCode() {
    assumeThat(recordGenOption).isEqualTo(JavaRecordGenerationOption.WITHERS)
    val javaCode = generateCommonCode(javaCodeGeneratorOptions())
    assertThat(javaCode)
      .containsIgnoringWhitespaces(
        """
          package org.pkl.codegen.java.common.code;
          
          import java.lang.Record;
          import java.util.function.Consumer;
          import org.pkl.config.java.mapper.NonNull;
          
          public interface Wither<@NonNull R extends @NonNull Record, @NonNull S> {
            @NonNull R with(@NonNull Consumer<@NonNull S> setter);
          }
        """
          .trimMargin()
      )
  }

  @Test
  fun testCommonCodeWithNonNullAnnotation() {
    assumeThat(recordGenOption).isEqualTo(JavaRecordGenerationOption.WITHERS)
    val javaCode =
      generateCommonCode(javaCodeGeneratorOptions(nonNullAnnotation = "very.custom.HelloNull"))

    assertThat(javaCode)
      .containsIgnoringWhitespaces(
        """
          package org.pkl.codegen.java.common.code;
          
          import java.lang.Record;
          import java.util.function.Consumer;
          import very.custom.HelloNull;
          
          public interface Wither<@HelloNull R extends @HelloNull Record, @HelloNull S> {
            @HelloNull R with(@HelloNull Consumer<@HelloNull S> setter);
          }
        """
          .trimMargin()
      )
  }

  @Test
  fun testEquals() {
    assumeThat(recordGenOption).isNotEqualTo(JavaRecordGenerationOption.LOMBOK_BUILDERS)
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
    assumeThat(recordGenOption).isNotEqualTo(JavaRecordGenerationOption.LOMBOK_BUILDERS)
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
    assumeThat(recordGenOption).isNotEqualTo(JavaRecordGenerationOption.LOMBOK_BUILDERS)
    val (_, propertyTypes) = instantiateOtherAndPropertyTypes()

    assertThat(propertyTypes.toString())
      .isEqualToIgnoringWhitespace(
        """
          PropertyTypes[
            _boolean=true, 
            _int=42, 
            _float=42.3, 
            string=string, 
            duration=5.min, 
            durationUnit=min, 
            dataSize=3.gb, 
            dataSizeUnit=gb, 
            nullable=idea, 
            nullable2=null, 
            pair=Pair(1, 2), 
            pair2=Pair(pigeon, Other[name=pigeon]), 
            coll=[1, 2, 3], 
            coll2=[Other[name=pigeon], Other[name=pigeon]], 
            list=[1, 2, 3], 
            list2=[Other[name=pigeon], 
            Other[name=pigeon]], 
            set=[1, 2, 3], 
            set2=[Other[name=pigeon]], 
            map={1=one, 2=two}, 
            map2={one=Other[name=pigeon], 
            two=Other[name=pigeon]}, 
            container={1=one, 2=two}, 
            container2={one=Other[name=pigeon], 
            two=Other[name=pigeon]}, 
            other=Other[name=pigeon], 
            regex=(i?)\w*, 
            any=Other[name=pigeon], 
            nonNull=Other[name=pigeon], 
            _enum=north
          ]
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
        javaCodeGeneratorOptions(generateJavadoc = true),
      )
    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
            /**
             * @deprecated
             * deprecatedProperty - property deprecation message
             */
            <2< @Builder >2> 
            public record ClassWithDeprecatedProperty(
                @Named("deprecatedProperty") @Deprecated long deprecatedProperty)
        """
          .trimMargin()
          .interpolate()
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
        javaCodeGeneratorOptions(generateJavadoc = true),
      )
    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
            /**
             * @deprecated
             * class deprecation message
             */
            @Deprecated
            <2< @Builder >2> 
            public record DeprecatedClass(
                @Named("propertyOfDeprecatedClass") long propertyOfDeprecatedClass)
        """
          .trimMargin()
          .interpolate()
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
        javaCodeGeneratorOptions(generateJavadoc = generateJavadoc),
      )

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          @Deprecated
          <2< @Builder >2>
          public record DeprecatedModule(
              @Named("propertyInDeprecatedModuleClass") long propertyInDeprecatedModuleClass)
        """
          .trimMargin()
          .interpolate()
      )

    if (generateJavadoc) {
      assertThat(javaCode.text)
        .containsIgnoringWhitespaces(
          """
            /**
             * @deprecated
             * module class deprecation message
             */
          """
            .trimMargin()
            .interpolate()
        )
    } else {
      assertThat(javaCode.text).doesNotContain("* @deprecated")
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
        javaCodeGeneratorOptions(generateJavadoc = generateJavadoc),
      )

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          public record ClassWithDeprecatedProperty(
              @Named("deprecatedProperty") @Deprecated long deprecatedProperty)
        """
          .trimMargin()
          .interpolate()
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

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
            @Deprecated
            <2< @Builder >2> 
            public record DeprecatedClass(
                @Named("propertyOfDeprecatedClass") long propertyOfDeprecatedClass)
        """
          .trimMargin()
          .interpolate()
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

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          @Deprecated
          <2< @Builder >2> 
          public record DeprecatedModule(
              @Named("propertyInDeprecatedModuleClass") long propertyInDeprecatedModuleClass)
        """
          .trimMargin()
          .interpolate()
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
        javaCodeGeneratorOptions(generateJavadoc = true),
      )

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          /**
           * @deprecated
           * deprecatedProperty - property is deprecated
           *
           * @param deprecatedProperty Documenting deprecatedProperty
           */
          <2< @Builder >2> 
          public record Text(
              @Named("deprecatedProperty") @Deprecated long deprecatedProperty)
        """
          .trimMargin()
          .interpolate()
      )
  }

  @Test
  fun `deprecation with message and doc comment on the same property, for 2 properties`() {
    val javaCode =
      generateJavaCode(
        """
      /// Documenting deprecatedProperty 1
      @Deprecated { message = "property 1 is deprecated" }
      deprecatedProperty1: Int
      /// Documenting deprecatedProperty 2
      @Deprecated { message = "property 2 is deprecated" }
      deprecatedProperty2: Int
    """,
        javaCodeGeneratorOptions(generateJavadoc = true),
      )

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          /**
           * @deprecated
           * deprecatedProperty1 - property 1 is deprecated
           * <BR>deprecatedProperty2 - property 2 is deprecated
           *
           * @param deprecatedProperty1 Documenting deprecatedProperty 1
           * @param deprecatedProperty2 Documenting deprecatedProperty 2
           */
          <2< @Builder >2> 
          public record Text(@Named("deprecatedProperty1") @Deprecated long deprecatedProperty1,
              @Named("deprecatedProperty2") @Deprecated long deprecatedProperty2)
        """
          .trimMargin()
          .interpolate()
      )
  }

  @Test
  fun `deprecation with message and doc comment on the same property, for abstract class`() {
    val javaCode =
      generateJavaCode(
        """
      abstract class Foo {
        /// Documenting deprecatedProperty 1
        @Deprecated { message = "property 1 is deprecated" }
        deprecatedProperty1: Int
      }
    """,
        javaCodeGeneratorOptions(generateJavadoc = true),
      )

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          public interface Foo {
            /**
             * Documenting deprecatedProperty 1
             *
             * @deprecated property 1 is deprecated
             */
            @Deprecated
            long deprecatedProperty1();
          }
        """
          .trimMargin()
          .interpolate()
      )
  }

  @Test
  fun `deprecation with message and doc comment on an abstract class`() {
    val javaCode =
      generateJavaCode(
        """
      /// Documenting Foo
      @Deprecated { message = "Foo is deprecated" }
      abstract class Foo {
        /// Documenting deprecatedProperty 1
        @Deprecated { message = "property 1 is deprecated" }
        deprecatedProperty1: Int
      }
    """,
        javaCodeGeneratorOptions(generateJavadoc = true),
      )

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          /**
           * Documenting Foo
           *
           * @deprecated Foo is deprecated
           */
          @Deprecated
          public interface Foo {
            /**
             * Documenting deprecatedProperty 1
             *
             * @deprecated property 1 is deprecated
             */
            @Deprecated
            long deprecatedProperty1();
          }
        """
          .trimMargin()
          .interpolate()
      )
  }

  @Test
  fun `deprecation with message and doc comment on an open class`() {
    val javaCode =
      generateJavaCode(
        """
      /// Documenting Foo
      @Deprecated { message = "Foo is deprecated" }
      open class Foo {
        /// Documenting deprecatedProperty 1
        @Deprecated { message = "property 1 is deprecated" }
        deprecatedProperty1: Int
      }
    """,
        javaCodeGeneratorOptions(generateJavadoc = true),
      )

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          /**
           * Documenting Foo
           *
           * @deprecated Foo is deprecated
           */
          @Deprecated
          public interface IFoo {
            /**
             * Documenting deprecatedProperty 1
             *
             * @deprecated property 1 is deprecated
             */
            @Deprecated
            long deprecatedProperty1();
          }
        
          /**
           * Documenting Foo
           * @deprecated
           * Foo is deprecated
           * <BR>deprecatedProperty1 - property 1 is deprecated
           *
           * @param deprecatedProperty1 Documenting deprecatedProperty 1
           */
          @Deprecated
          <2< @Builder >2> 
          public record Foo(
              @Named("deprecatedProperty1") @Deprecated long deprecatedProperty1) implements IFoo
        """
          .trimMargin()
          .interpolate()
      )
  }

  @Test
  fun properties() {
    assumeThat(recordGenOption).isNotEqualTo(JavaRecordGenerationOption.LOMBOK_BUILDERS)
    val (other, propertyTypes) = instantiateOtherAndPropertyTypes()

    assertThat(other).extracting("name").isEqualTo("pigeon")

    assertThat(propertyTypes).extracting("_boolean").isEqualTo(true)
    assertThat(propertyTypes).extracting("_int").isEqualTo(42L)
    assertThat(propertyTypes).extracting("_float").isEqualTo(42.3)
    assertThat(propertyTypes).extracting("string").isEqualTo("string")
    assertThat(propertyTypes).extracting("duration").isEqualTo(Duration(5.0, DurationUnit.MINUTES))
    assertThat(propertyTypes)
      .extracting("dataSize")
      .isEqualTo(DataSize(3.0, DataSizeUnit.GIGABYTES))
    assertThat(propertyTypes).extracting("nullable").isEqualTo("idea")
    assertThat(propertyTypes).extracting("nullable2").isEqualTo(null as String?)
    assertThat(propertyTypes).extracting("list").isEqualTo(listOf(1, 2, 3))
    assertThat(propertyTypes).extracting("list2").isEqualTo(listOf(other, other))
    assertThat(propertyTypes).extracting("set").isEqualTo(setOf(1, 2, 3))
    assertThat(propertyTypes).extracting("set2").isEqualTo(setOf(other))
    assertThat(propertyTypes).extracting("map").isEqualTo(mapOf(1 to "one", 2 to "two"))
    assertThat(propertyTypes).extracting("map2").isEqualTo(mapOf("one" to other, "two" to other))
    assertThat(propertyTypes).extracting("container").isEqualTo(mapOf(1 to "one", 2 to "two"))
    assertThat(propertyTypes)
      .extracting("container2")
      .isEqualTo(mapOf("one" to other, "two" to other))
    assertThat(propertyTypes).extracting("other").isEqualTo(other)
    assertThat(propertyTypes).extracting("regex").isInstanceOf(Pattern::class.java)
    assertThat(propertyTypes).extracting("any").isEqualTo(other)
    assertThat(propertyTypes).extracting("nonNull").isEqualTo(other)
  }

  @Test
  fun `properties 2`() {
    assertThat(propertyTypesSources).isEqualToResourceFile("PropertyTypesRecord.jva")
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
      .satisfies({
        assertThat(it)
          .containsIgnoringWhitespaces(
            """
            public record Foo(@Named("other") long other,
                @Named("bar") @NonNull Bar bar)
          """
              .trimMargin()
              .interpolate()
          )
          .containsIgnoringWhitespaces(
            """
            public record Bar(@Named("foo") @NonNull Foo foo,
                @Named("other") @NonNull String other)
          """
              .trimMargin()
              .interpolate()
          )
      })
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
        javaCodeGeneratorOptions(),
      )

    assertThat(javaCode)
      .compilesSuccessfully()
      .satisfies({
        assertThat(it)
          .containsIgnoringWhitespaces(
            """
              public record Mod() {
                public interface Foo {
                  long one();
                }
            """
              .trimIndent()
              .interpolate()
          )
          .containsIgnoringWhitespaces(
            """
                public interface INone extends Foo {
                }

                <2< @Builder >2>
                public record None(@Named("one") long one) implements Foo, INone
            """
              .trimIndent()
              .interpolate()
          )
          .containsIgnoringWhitespaces(
            """
                public interface IBar extends INone {
                  String two();
                }

                <2< @Builder >2>
                public record Bar(@Named("one") long one,
                    @Named("two") String two)  implements INone, IBar
            """
              .trimIndent()
              .interpolate()
          )
          .containsIgnoringWhitespaces(
            """
                <2< @Builder >2>
                public record Baz(@Named("one") long one, @Named("two") String two,
                    @Named("three") @NonNull Duration three) implements IBar
            """
              .trimIndent()
              .interpolate()
          )
      })
      .isEqualToResourceFile("InheritanceRecord.jva")
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

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
            public record Foo()
        """
          .trimMargin()
          .interpolate()
      )
      .containsIgnoringWhitespaces(
        """
            public interface Bar {
            }
        """
          .trimMargin()
          .interpolate()
      )
      .containsIgnoringWhitespaces(
        """
            public record Baz() implements Bar
        """
          .trimMargin()
          .interpolate()
      )
  }

  @Test
  fun `stateless module classes`() {
    var javaCode = generateJavaCode("module my.mod")
    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          package my;
          
          public record Mod() {
          }
        """
          .trimMargin()
          .interpolate()
      )

    javaCode = generateJavaCode("abstract module my.mod")
    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          package my;
          
          public interface Mod {
          }
        """
          .trimMargin()
          .interpolate()
      )

    val javaCodes = generateJavaCodeForOpenModule("open module my.mod")

    assertThat(javaCodes)
      .anySatisfy {
        assertThat(it.text)
          .containsIgnoringWhitespaces(
            """
          public record Mod() implements IMod
        """
              .trimMargin()
              .interpolate()
          )
      }
      .anySatisfy {
        assertThat(it.text)
          .containsIgnoringWhitespaces(
            """
          public interface IMod {
          }
        """
              .trimMargin()
              .interpolate()
          )
      }
  }

  @Test
  fun `reserved words`() {
    assumeThat(recordGenOption).isNotEqualTo(JavaRecordGenerationOption.LOMBOK_BUILDERS)
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
  fun `'with' methods`() {
    assumeThat(recordGenOption).isEqualTo(JavaRecordGenerationOption.WITHERS)
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
    """,
        javaCodeGeneratorOptions(),
      )

    assertThat(javaCode)
      .compilesSuccessfully()
      .satisfies({
        assertThat(it)
          .containsIgnoringWhitespaces(
            """
                public interface Foo {
                  long x();
                }
            """
              .trimIndent()
          )
          .containsIgnoringWhitespaces(
            """
              <2< @Builder >2>
              public record Bar(@Named("x") long x,
                  @Named("y") @NonNull String y) implements Foo <1<, Wither<Bar, Bar.Memento> >1> {
                  <1<
                @Override
                public @NonNull Bar with(final @NonNull Consumer<Memento> setter) {
                  final var memento = new Memento(this);
                  setter.accept(memento);
                  return memento.build();
                }
            
                public static final class Memento {
                  public long x;
            
                  public @NonNull String y;
            
                  private Memento(final @NonNull Bar r) {
                    x = r.x;
                    y = r.y;
                  }
            
                  private @NonNull Bar build() {
                    return new Bar(x, y);
                  }
                }
              }
              >1>
            }
            """
              .trimIndent()
              .interpolate()
          )
      })
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

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          public record Mod(@Named("pigeon") @NonNull String pigeon,
              @Named("parrot") @NonNull String parrot)
        """
          .trimMargin()
          .interpolate()
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

    assertThat(javaCode.text)
      .doesNotContain("String pigeon1")
      .contains("@NonNull String parrot1")
      .doesNotContain("String pigeon2")
      .contains("@NonNull String parrot2")
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
        javaCodeGeneratorOptions(generateJavadoc = true),
      )

    assertThat(javaCode).compilesSuccessfully().isEqualToResourceFile("JavadocRecord.jva")
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
        javaCodeGeneratorOptions(generateJavadoc = true),
      )

    assertThat(javaCode)
      .compilesSuccessfully()
      .satisfies({
        assertThat(it)
          .containsIgnoringWhitespaces(
            """
              /**
               * @param pigeon module property comment.
               * can contain /* and *&#47; characters.
               */
              <2< @Builder >2>
              public record Mod(
                  @Named("pigeon") Mod. @NonNull Person pigeon)
            """
              .trimIndent()
              .interpolate()
          )
          .containsIgnoringWhitespaces(
            """
            /**
             * @param name class property comment.
             * can contain /* and *&#47; characters.
             */
              <2< @Builder >2> 
            public record Person(
                @Named("name") @NonNull String name)
          """
              .trimMargin()
              .interpolate()
          )
      })
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
      .satisfies({
        assertThat(it)
          .containsIgnoringWhitespaces(
            """
              public record Mod(@Named("uint8") short uint8, @Named("uint16") int uint16,
                  @Named("uint32") long uint32, @Named("uint") long uint, @Named("int8") byte int8,
                  @Named("int16") short int16, @Named("int32") int int32, @Named("uri") @NonNull URI uri,
                  @Named("pair") @NonNull Pair<@NonNull Short, @NonNull Integer> pair,
                  @Named("list") @NonNull List<@NonNull Long> list, @Named("set") @NonNull Set<@NonNull Long> set,
                  @Named("map") @NonNull Map<@NonNull Byte, @NonNull Short> map,
                  @Named("listing") @NonNull List<@NonNull Integer> listing,
                  @Named("mapping") @NonNull Map<@NonNull URI, @NonNull Short> mapping,
                  @Named("nullable") Integer nullable)
            """
              .trimMargin()
              .interpolate()
          )
          .containsIgnoringWhitespaces(
            """
                public record Foo(@Named("uint8") short uint8, @Named("uint16") int uint16,
                    @Named("uint32") long uint32, @Named("uint") long uint, @Named("int8") byte int8,
                    @Named("int16") short int16, @Named("int32") int int32, @Named("uri") @NonNull URI uri,
                    @Named("list") @NonNull List<@NonNull Long> list)
            """
              .trimMargin()
              .interpolate()
          )
      })
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
        javaCodeGeneratorOptions(nonNullAnnotation = "com.example.Annotations\$NonNull"),
      )

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces("import com.example.Annotations;")
      .containsIgnoringWhitespaces(
        """
        public record Mod(
            @Named("foo") @Annotations.NonNull String foo)
      """
          .trimIndent()
      )

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

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          public record Mod(@Named("foo") long foo, @Named("bar") Long bar, @Named("baz") Object baz,
              @Named("qux") @NonNull String qux, @Named("foo2") List<@NonNull String> foo2,
              @Named("bar2") @NonNull List<String> bar2, @Named("baz2") @NonNull List<@NonNull String> baz2,
              @Named("qux2") @NonNull List<@NonNull Long> qux2)
        """
          .trimMargin()
          .interpolate()
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
      .satisfies({
        assertThat(it)
          .containsIgnoringWhitespaces(
            """
              public record Mod(@Named("simple") @NonNull String simple,
                  @Named("constrained") @NonNull String constrained,
                  @Named("parameterized") @NonNull List<@NonNull Long> parameterized,
                  @Named("recursive1") @NonNull List<@NonNull Long> recursive1,
                  @Named("recursive2") @NonNull List<@NonNull String> recursive2)
            """
              .trimMargin()
              .interpolate()
          )
          .containsIgnoringWhitespaces(
            """
              public record Foo(@Named("simple") @NonNull String simple,
                  @Named("constrained") @NonNull String constrained,
                  @Named("parameterized") @NonNull List<@NonNull Long> parameterized,
                  @Named("recursive1") @NonNull List<@NonNull Long> recursive1,
                  @Named("recursive2") @NonNull List<@NonNull String> recursive2)
            """
              .trimMargin()
              .interpolate()
          )
      })
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
      .satisfies({
        assertThat(it)
          .containsIgnoringWhitespaces(
            """
          public record Mod(@Named("res1") @NonNull List<@NonNull Long> res1,
              @Named("res2") @NonNull List<@NonNull List<@NonNull String>> res2,
              @Named("res3") @NonNull Map<@NonNull Long, @NonNull String> res3,
              @Named("res4") @NonNull Map<@NonNull String, @NonNull Duration> res4,
              @Named("res5") @NonNull Map<Mod.Person, Mod.Person> res5,
              @Named("res6") @NonNull List<@NonNull Object> res6,
              @Named("res7") @NonNull Map<@NonNull Object, @NonNull Object> res7,
              @Named("res8") @NonNull Map<@NonNull String, @NonNull Object> res8,
              @Named("res9") @NonNull Map<@NonNull Object, @NonNull Object> res9)
          """
              .trimMargin()
              .interpolate()
          )
          .containsIgnoringWhitespaces(
            """
            public record Person(
                @Named("name") @NonNull String name)
        """
              .trimMargin()
              .interpolate()
          )
          .containsIgnoringWhitespaces(
            """
            public record Foo(@Named("res1") @NonNull List<@NonNull Long> res1,
                @Named("res2") @NonNull List<@NonNull List<@NonNull String>> res2,
                @Named("res3") @NonNull Map<@NonNull Long, @NonNull String> res3,
                @Named("res4") @NonNull Map<@NonNull String, @NonNull Duration> res4,
                @Named("res5") @NonNull Map<Person, Person> res5,
                @Named("res6") @NonNull List<@NonNull Object> res6,
                @Named("res7") @NonNull Map<@NonNull Object, @NonNull Object> res7,
                @Named("res8") @NonNull Map<@NonNull String, @NonNull Object> res8,
                @Named("res9") @NonNull Map<@NonNull Object, @NonNull Object> res9)
        """
              .trimMargin()
              .interpolate()
          )
      })
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

    assertThat(javaCode)
      .compilesSuccessfully()
      .satisfies({
        assertThat(it)
          .containsIgnoringWhitespaces("""public record Mod(@Named("x") @NonNull String x)""")
      })
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

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
        public record Mod(@Named("v1") @NonNull String v1, @Named("v2") @NonNull String v2,
            @Named("v3") @NonNull String v3, @Named("v4") @NonNull String v4,
            @Named("v5") @NonNull String v5,
            @Named("v6") @NonNull String v6)
      """
          .trimIndent()
      )
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

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
        public record Mod(@Named("v1") @NonNull String v1, @Named("v2") @NonNull String v2,
            @Named("v3") @NonNull String v3, @Named("v4") @NonNull String v4,
            @Named("v5") @NonNull String v5,
            @Named("v6") @NonNull String v6)
      """
          .trimIndent()
      )
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
        javaCodeGeneratorOptions(paramsAnnotation = "org.project.MyAnnotation"),
      )

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces("import org.project.MyAnnotation;")
      .containsIgnoringWhitespaces(
        """public record Mod(@MyAnnotation("name") @NonNull String name)"""
      )
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
        javaCodeGeneratorOptions(paramsAnnotation = null),
      )

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces("""public record Mod(@NonNull String name)""")
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
        javaCodeGeneratorOptions(generateSpringBootConfig = true),
      )

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          @ConfigurationProperties
          <2< @Builder >2>
          public record Mod(Mod. @NonNull Server server)
       """
          .trimMargin()
          .interpolate()
      )
      .containsIgnoringWhitespaces(
        """
          @ConfigurationProperties("server")
          <2< @Builder >2>
          public record Server(long port,
              @NonNull List<@NonNull URI> urls)
        """
          .trimMargin()
          .interpolate()
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
    if (recordGenOption != JavaRecordGenerationOption.LOMBOK_BUILDERS) {
      assertDoesNotThrow { inMemoryCompile(javaSourceFiles.mapValues { it.value.text }) }
    }

    val javaClientCode =
      javaSourceFiles.entries.find { (fileName, _) -> fileName.endsWith("Client.java") }!!.value
    assertThat(javaClientCode.text)
      .containsIgnoringWhitespaces(
        """
          public record Client(@Named("lib") @NonNull Library lib,
              @Named("parrot") Library. @NonNull Person parrot)
        """
          .trimMargin()
          .interpolate()
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
    if (recordGenOption != JavaRecordGenerationOption.LOMBOK_BUILDERS) {
      assertDoesNotThrow { inMemoryCompile(javaSourceFiles.mapValues { it.value.text }) }
    }

    val javaDerivedCode =
      javaSourceFiles.entries.find { (filename, _) -> filename.endsWith("Derived.java") }!!.value
    assertThat(javaDerivedCode.text)
      .containsIgnoringWhitespaces(
        """
          <2< @Builder >2> 
          public record Derived(@Named("pigeon") Base. @NonNull Person pigeon,
              @Named("person1") Base. @NonNull Person person1,
              @Named("person2") Derived. @NonNull Person2 person2) implements IBase
        """
          .trimMargin()
          .interpolate()
      )
      .containsIgnoringWhitespaces(
        """
          <2< @Builder >2> 
          public record Person2(@Named("name") @NonNull String name,
              @Named("age") long age) implements Base.IPerson
        """
          .trimMargin()
          .interpolate()
      )
  }

  @Test
  fun `empty module`() {
    val javaCode = generateJavaCode("module mod")
    assertThat(javaCode.text).containsIgnoringWhitespaces("public record Mod() {}")
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
    if (recordGenOption != JavaRecordGenerationOption.LOMBOK_BUILDERS) {
      assertDoesNotThrow { inMemoryCompile(javaSourceFiles.mapValues { it.value.text }) }
    }

    val javaDerivedCode =
      javaSourceFiles.entries.find { (filename, _) -> filename.endsWith("Derived.java") }!!.value
    assertThat(javaDerivedCode.text)
      .containsIgnoringWhitespaces(
        """
          public record Derived(
              @Named("v") @NonNull String v) implements Base
        """
          .trimMargin()
          .interpolate()
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
    assumeThat(recordGenOption).isNotEqualTo(JavaRecordGenerationOption.LOMBOK_BUILDERS)
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
        javaCodeGeneratorOptions(implementSerializable = true),
      )

    assertThat(javaCode.text).containsIgnoringWhitespaces("implements Serializable")

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

      val patternCompare = BiPredicate { p1: Pattern, p2: Pattern ->
        p1.toString().equals(p2.toString())
      }

      assertThat(restoredInstance!!)
        .usingRecursiveComparison()
        .withEqualsForType(patternCompare, Pattern::class.java)
        .isEqualTo(instance)
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
        javaCodeGeneratorOptions(implementSerializable = true),
      )

    assertThat(javaCode.text).doesNotContain("Serializable")

    javaCode =
      generateJavaCode(
        """
      module my.mod
    """
          .trimIndent(),
        javaCodeGeneratorOptions(implementSerializable = true),
      )

    assertThat(javaCode.text).doesNotContain("Serializable")
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
        javaCodeGeneratorOptions(implementSerializable = true),
      )

    assertThat(javaCode.text)
      .containsIgnoringWhitespaces(
        """
          <2< @Builder >2>
          public record Person(@Named("name") @NonNull String name,
              @Named("address") Person. @NonNull Address address) implements Serializable
        """
          .trimMargin()
          .interpolate()
      )
      .containsIgnoringWhitespaces(
        """
          <2< @Builder >2>
          public record Address(
              @Named("city") @NonNull String city) implements Serializable <1< ,Wither<Address, Address.Memento> >1> {
        """
          .trimMargin()
          .interpolate()
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
          .trimIndent(),
        javaCodeGeneratorOptions(),
      )

    assertThat(javaCode)
      .compilesSuccessfully()
      .satisfies({
        assertThat(it)
          .containsIgnoringWhitespaces(
            """
              public record Mod() {
                public interface IFoo {
                }
                
                <2< @Builder >2>
                public record Foo() implements IFoo <1<, Wither<Foo, Foo.Memento> >1> {
                  <1<
                  @Override
                  public @NonNull Foo with(final @NonNull Consumer<Memento> setter) {
                    final var memento = new Memento(this);
                    setter.accept(memento);
                    return memento.build();
                  }
              
                  public static final class Memento {
                    private Memento(final @NonNull Foo r) {
                    }
              
                    private @NonNull Foo build() {
                      return new Foo();
                    }
                  }
                  >1>
                }
              
                <2< @Builder >2>
                public record TheFoo(
                    @Named("fooProp") @NonNull String fooProp) implements IFoo <1< , Wither<TheFoo, TheFoo.Memento> >1> {
                  <1<  
                  @Override
                  public @NonNull TheFoo with(final @NonNull Consumer<Memento> setter) {
                    final var memento = new Memento(this);
                    setter.accept(memento);
                    return memento.build();
                  }
              
                  public static final class Memento {
                    public @NonNull String fooProp;
              
                    private Memento(final @NonNull TheFoo r) {
                      fooProp = r.fooProp;
                    }
              
                    private @NonNull TheFoo build() {
                      return new TheFoo(fooProp);
                    }
                  }
                  >1>
                }
              
                public interface IOpenClass {
                  @NonNull IFoo prop();
                }
              
                <2< @Builder >2>
                public record OpenClass(
                    @Named("prop") @NonNull Foo prop) implements IOpenClass <1< , Wither<OpenClass, OpenClass.Memento> >1> {
                  <1<  
                  @Override
                  public @NonNull OpenClass with(final @NonNull Consumer<Memento> setter) {
                    final var memento = new Memento(this);
                    setter.accept(memento);
                    return memento.build();
                  }
              
                  public static final class Memento {
                    public @NonNull Foo prop;
              
                    private Memento(final @NonNull OpenClass r) {
                      prop = r.prop;
                    }
              
                    private @NonNull OpenClass build() {
                      return new OpenClass(prop);
                    }
                  }
                  >1>
                }
              
                <2< @Builder >2>
                public record TheClass(
                    @Named("prop") @NonNull TheFoo prop) implements IOpenClass <1< , Wither<TheClass, TheClass.Memento> >1> {
                  <1<  
                  @Override
                  public @NonNull TheClass with(final @NonNull Consumer<Memento> setter) {
                    final var memento = new Memento(this);
                    setter.accept(memento);
                    return memento.build();
                  }
              
                  public static final class Memento {
                    public @NonNull TheFoo prop;
              
                    private Memento(final @NonNull TheClass r) {
                      prop = r.prop;
                    }
              
                    private @NonNull TheClass build() {
                      return new TheClass(prop);
                    }
                  }
                  >1>
                }
              }
            """
              .trimMargin()
              .interpolate()
          )
      })
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
        javaCodeGeneratorOptions(generateGetters = true),
      )

    assertThat(javaCode)
      .compilesSuccessfully()
      .satisfies({ assertThat(it).doesNotContainPattern("""get\w+[(]""") })
  }

  @Test
  fun `override names in a standalone module`() {
    val files =
      javaCodeGeneratorOptions(
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
      "java/x/y/z/MyModule.java" to listOf("package x.y.z;", "public record MyModule("),
      "$MAPPER_PREFIX/a.b.c.MyModule.properties" to
        listOf("org.pkl.config.java.mapper.a.b.c.MyModule\\#ModuleClass=x.y.z.MyModule"),
      // ---
      "java/u/v/w/RenamedModule.java" to listOf("package u.v.w;", "public record RenamedModule("),
      "$MAPPER_PREFIX/d.e.f.AnotherModule.properties" to
        listOf("org.pkl.config.java.mapper.d.e.f.AnotherModule\\#ModuleClass=u.v.w.RenamedModule"),
    )
  }

  @Test
  fun `override names based on the longest prefix`() {
    val files =
      javaCodeGeneratorOptions(
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
      "java/x/Module1.java" to listOf("package x;", "public record Module1("),
      "$MAPPER_PREFIX/com.foo.bar.Module1.properties" to
        listOf("org.pkl.config.java.mapper.com.foo.bar.Module1\\#ModuleClass=x.Module1"),
      // ---
      "java/z/Module2.java" to listOf("package z;", "public record Module2("),
      "$MAPPER_PREFIX/com.Module2.properties" to
        listOf("org.pkl.config.java.mapper.com.Module2\\#ModuleClass=z.Module2"),
      // ---
      "java/w/org/baz/Module3.java" to listOf("package w.org.baz;", "public record Module3("),
      "$MAPPER_PREFIX/org.baz.Module3.properties" to
        listOf("org.pkl.config.java.mapper.org.baz.Module3\\#ModuleClass=w.org.baz.Module3"),
    )
  }

  @Test
  fun `override names in multiple modules using each other`() {
    val files =
      javaCodeGeneratorOptions(
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
      "java/com/foo/x/Module1.java" to listOf("package com.foo.x;", "public record Module1("),
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
          "public record RenamedModule(",
          """@Named("owner") Module1. @NonNull Person owner""",
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
          "public record Module3(",
          """@Named("owner") RenamedModule. @NonNull Group owner""",
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
      javaCodeGeneratorOptions(
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
      "java/x/y/z/renamed_module.java" to listOf("package x.y.z;", "public record renamed_module("),
      "$MAPPER_PREFIX/a.b.c.MyModule.properties" to
        listOf("org.pkl.config.java.mapper.a.b.c.MyModule\\#ModuleClass=x.y.z.renamed_module"),
      // ---
      "java/u/v/w/Lower_module.java" to listOf("package u.v.w;", "public record Lower_module("),
      "$MAPPER_PREFIX/d.e.f.lower_module.properties" to
        listOf("org.pkl.config.java.mapper.d.e.f.lower_module\\#ModuleClass=u.v.w.Lower_module"),
    )
  }

  @Test
  fun `equals,hashCode,toString work correctly for class that doesn't declare properties`() {
    assumeThat(recordGenOption).isNotEqualTo(JavaRecordGenerationOption.LOMBOK_BUILDERS)
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
    assertThat(bar1.toString()).isEqualToIgnoringWhitespace("Bar[name=name1]")
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
      val generator = JavaRecordCodeGenerator(pklSchema, this)
      acc + generator.output
    }
  }

  private fun JavaCodeGeneratorOptions.generateFiles(
    vararg pklModules: kotlin.Pair<String, String>
  ): Map<String, String> =
    generateFiles(*pklModules.map { (name, text) -> PklModule(name, text) }.toTypedArray())

  private fun generateFiles(vararg pklModules: PklModule): Map<String, JavaSourceCode> =
    javaCodeGeneratorOptions().generateFiles(*pklModules).mapValues { JavaSourceCode(it.value) }

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
    JavaSourceCodeAssert(actual, recordGenOption)

  private data class JavaSourceCode(val text: String) {
    val lines: List<CharSequence> by lazy { text.split(Regex("\\n")) }

    fun compile(): Map<String, Class<*>> = inMemoryCompile(mapOf("/org/Mod.java" to text))

    fun deleteLines(predicate: (String) -> Boolean): JavaSourceCode =
      JavaSourceCode(text.lines().filterNot(predicate).joinToString("\n"))
  }

  private class JavaSourceCodeAssert(
    val actualCode: JavaSourceCode,
    val recordGenOption: JavaRecordGenerationOption,
  ) :
    AbstractCharSequenceAssert<JavaSourceCodeAssert, CharSequence>(
      actualCode.text,
      JavaSourceCodeAssert::class.java,
    ) {

    //    fun contains(expected: String): JavaSourceCodeAssert {
    //      val modExpected = interpolate(expected, recordGenOption)
    //      if (!actual.contains(modExpected)) {
    //        // check for equality to get better error output (IDE diff dialog)
    //        assertThat(actual).isEqualTo(modExpected)
    //      }
    //      return this
    //    }

    override fun contains(vararg values: CharSequence?): JavaSourceCodeAssert {
      super.contains(*interpolate(*values))
      return this
    }

    private fun interpolate(vararg values: CharSequence?) =
      values
        .asSequence()
        .map { it?.let { recordGenOption.interpolate(it) } }
        .toList()
        .toTypedArray()

    override fun containsIgnoringWhitespaces(vararg values: CharSequence?): JavaSourceCodeAssert {
      super.containsIgnoringWhitespaces(*interpolate(*values))
      return this
    }

    override fun doesNotContain(vararg values: CharSequence?): JavaSourceCodeAssert {
      super.doesNotContain(*interpolate(*values))
      return this
    }

    fun compilesSuccessfully(): JavaSourceCodeAssert {
      //      assumeThat(recordGenOption).isNotEqualTo(JavaRecordGenerationOption.LOMBOK_BUILDERS)
      if (recordGenOption != JavaRecordGenerationOption.LOMBOK_BUILDERS) {
        assertThatCode { actualCode.compile() }.doesNotThrowAnyException()
      }

      return this
    }

    override fun isEqualTo(expected: Any?): JavaSourceCodeAssert {
      when (expected) {
        is CharSequence -> super.isEqualTo(recordGenOption.interpolate(expected))
        else -> super.isEqualTo(expected)
      }
      return this
    }

    override fun isEqualToIgnoringWhitespace(expected: CharSequence): JavaSourceCodeAssert {
      super.isEqualToIgnoringWhitespace(recordGenOption.interpolate(expected))
      return this
    }

    fun containsAll(vararg values: CharSequence): JavaSourceCodeAssert {
      assertThat(actualCode.lines).containsAll(values.asIterable())
      return this
    }

    fun isEqualToResourceFile(fileName: String): JavaSourceCodeAssert {
      val partition: kotlin.Pair<List<CharSequence>, List<CharSequence>> =
        javaClass.getResourceAsStream(fileName)?.bufferedReader()?.use {
          recordGenOption.interpolate(it.readText()).splitToSequence(Regex("\\n")).partition {
            it.startsWith("import ")
          }
        } ?: kotlin.Pair(emptyList(), emptyList())

      containsAll(*partition.first.toTypedArray())
        .containsIgnoringWhitespaces(*partition.second.toTypedArray())
      return this
    }
  }
}
