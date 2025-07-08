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

import com.palantir.javapoet.*
import java.io.StringWriter
import java.lang.Deprecated
import java.nio.file.Path
import java.util.*
import java.util.function.Consumer
import java.util.regex.Pattern
import javax.lang.model.element.Modifier
import org.pkl.commons.NameMapper
import org.pkl.core.*
import org.pkl.core.util.CodeGeneratorUtils
import org.pkl.core.util.IoUtils

class JavaCodeGeneratorException(message: String) : RuntimeException(message)

@kotlin.Deprecated("renamed to JavaCodeGeneratorOptions", ReplaceWith("JavaCodeGeneratorOptions"))
typealias JavaCodegenOptions = JavaCodeGeneratorOptions

data class JavaCodeGeneratorOptions(
  /** The characters to use for indenting generated Java code. */
  val indent: String = "  ",

  /** Whether to add a <code>@Generated</code> annotation to the types to be generated. */
  val generatedAnnotation: Boolean = false,

  /**
   * Whether to generate public getter methods and protected final fields instead of public final
   * fields.
   */
  val generateGetters: Boolean = false,

  /** Whether to preserve Pkl doc comments by generating corresponding Javadoc comments. */
  val generateJavadoc: Boolean = false,

  /** Whether to generate config classes for use with Spring Boot. */
  val generateSpringBootConfig: Boolean = false,

  /**
   * Fully qualified name of the annotation type to use for annotating constructor parameters with
   * their name.
   *
   * The specified annotation type must have a `value` parameter of type [java.lang.String] or the
   * generated code may not compile.
   *
   * If set to `null`, constructor parameters are not annotated. The default value is `null` if
   * [generateSpringBootConfig] is `true` and `"org.pkl.config.java.mapper.Named"` otherwise.
   */
  val paramsAnnotation: String? =
    if (generateSpringBootConfig) null else "org.pkl.config.java.mapper.Named",

  /**
   * Fully qualified name of the annotation type to use for annotating non-null types.
   *
   * The specified annotation type must have a [java.lang.annotation.Target] of
   * [java.lang.annotation.ElementType.TYPE_USE] or the generated code may not compile. If set to
   * `null`, [org.pkl.config.java.mapper.NonNull] will be used.
   */
  val nonNullAnnotation: String? = null,

  /** Whether to generate classes that implement [java.io.Serializable]. */
  val implementSerializable: Boolean = false,

  /**
   * A mapping from Pkl module name prefixes to their replacements.
   *
   * Can be used when the class or package name in the generated source code should be different
   * from the corresponding name derived from the Pkl module declaration .
   */
  val renames: Map<String, String> = emptyMap(),

  /**
   * Whether to generate Java records and the related interfaces.
   *
   * This overrides any Java class generation related options!
   */
  val generateRecords: Boolean = false,

  /** Whether to generate JEP 468 like withers for records. */
  val useWithers: Boolean = false,

  /** Whether to generate Lombok Builders for records. */
  val useLombokBuilders: Boolean = false,
)

/** Entrypoint for the Java code generator API. */
class JavaRecordCodeGenerator(
  private val schema: ModuleSchema,
  private val codegenOptions: JavaCodeGeneratorOptions,
) {

  companion object {
    private val OBJECT = ClassName.get(Object::class.java)
    private val STRING = ClassName.get(String::class.java)
    private val DURATION = ClassName.get(Duration::class.java)
    private val DURATION_UNIT = ClassName.get(DurationUnit::class.java)
    private val DATA_SIZE = ClassName.get(DataSize::class.java)
    private val DATASIZE_UNIT = ClassName.get(DataSizeUnit::class.java)
    private val PAIR = ClassName.get(Pair::class.java)
    private val COLLECTION = ClassName.get(Collection::class.java)
    private val LIST = ClassName.get(List::class.java)
    private val SET = ClassName.get(Set::class.java)
    private val MAP = ClassName.get(Map::class.java)
    private val PMODULE = ClassName.get(PModule::class.java)
    private val PCLASS = ClassName.get(PClass::class.java)
    private val PATTERN = ClassName.get(Pattern::class.java)
    private val URI = ClassName.get(java.net.URI::class.java)
    private val VERSION = ClassName.get(Version::class.java)

    private const val PROPERTY_PREFIX: String = "org.pkl.config.java.mapper."

    private val commonCodePackage: String = "${this::class.java.packageName}.common.code"
    val commonCodePackageFile: Path =
      Path.of("java", commonCodePackage.replace(".", "/"), "Wither.java")

    private fun toClassName(fqn: String): ClassName {
      val index = fqn.lastIndexOf(".")
      if (index == -1) {
        throw JavaCodeGeneratorException(
          """
            Annotation `$fqn` is not a valid Java class.
            The name of the annotation should be the canonical Java name of the class, for example, `com.example.Foo`.
          """
            .trimIndent()
        )
      }
      val packageName = fqn.substring(0, index)
      val classParts = fqn.substring(index + 1).split('$')
      return if (classParts.size == 1) {
        ClassName.get(packageName, classParts.first())
      } else {
        ClassName.get(packageName, classParts.first(), *classParts.drop(1).toTypedArray())
      }
    }

    private fun nonNullAnnotation(codegenOptions: JavaCodeGeneratorOptions): AnnotationSpec {
      val annotation = codegenOptions.nonNullAnnotation
      val className =
        if (annotation == null) {
          ClassName.get("org.pkl.config.java.mapper", "NonNull")
        } else {
          toClassName(annotation)
        }
      return AnnotationSpec.builder(className).build()
    }

    // package $commonCodePackage;
    //
    // import java.util.function.Consumer;
    //
    // public interface Wither<@NonNull R extends @NonNull Record, @NonNull S> {
    //   @NonNull R with(@NonNull Consumer<@NonNull S> setter);
    // }
    fun generateCommonCode(codegenOptions: JavaCodeGeneratorOptions): String {
      val interfaceSpec =
        TypeSpec.interfaceBuilder("Wither")
          .addModifiers(Modifier.PUBLIC)
          .addTypeVariable(
            TypeVariableName.get(
                "R",
                ClassName.get("java.lang", "Record").annotated(nonNullAnnotation(codegenOptions)),
              )
              .annotated(nonNullAnnotation(codegenOptions)) as TypeVariableName
          )
          .addTypeVariable(
            TypeVariableName.get("S").annotated(nonNullAnnotation(codegenOptions))
              as TypeVariableName
          )
          .addMethod(
            MethodSpec.methodBuilder("with")
              .addParameter(
                ParameterSpec.builder(
                    ParameterizedTypeName.get(
                        ClassName.get("java.util.function", "Consumer"),
                        TypeVariableName.get("S").annotated(nonNullAnnotation(codegenOptions)),
                      )
                      .annotated(nonNullAnnotation(codegenOptions)),
                    "setter",
                  )
                  .build()
              )
              .returns(TypeVariableName.get("R").annotated(nonNullAnnotation(codegenOptions)))
              .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
              .build()
          )
          .build()

      return JavaFile.builder(commonCodePackage, interfaceSpec).build().toString()
    }
  }

  private data class GeneratedType(
    val typeClass: TypeSpec.Builder?,
    val typeInterface: TypeSpec.Builder?,
  )

  val output: Map<String, String>
    get() {
      return getJavaFiles() + (propertyFileName to propertiesFile)
    }

  private val propertyFileName: String
    get() =
      "resources/META-INF/org/pkl/config/java/mapper/classes/${IoUtils.encodePath(schema.moduleName)}.properties"

  private val propertiesFile: String
    get() {
      val props = Properties()
      props["$PROPERTY_PREFIX${schema.moduleClass.qualifiedName}"] =
        schema.moduleClass.toJavaPoetName().reflectionName()
      for (pClass in schema.classes.values) {
        props["$PROPERTY_PREFIX${pClass.qualifiedName}"] = pClass.toJavaPoetName().reflectionName()
      }
      return StringWriter()
        .apply { props.store(this, "Java mappings for Pkl module `${schema.moduleName}`") }
        .toString()
    }

  private fun getJavaFileName(isInterface: Boolean): String {
    val (packageName, className) = nameMapper.map(schema.moduleName)
    val dirPath = packageName.replace('.', '/')
    val fileName = "${if (isInterface) "I" else ""}${className}"
    return if (dirPath.isEmpty()) {
      "java/$fileName.java"
    } else {
      "java/$dirPath/$fileName.java"
    }
  }

  fun getJavaFiles(): Map<String, String> {
    if (schema.moduleUri.scheme == "pkl") {
      throw JavaCodeGeneratorException(
        "Cannot generate Java code for a Pkl standard library module (`${schema.moduleUri}`)."
      )
    }

    val pModuleClass = schema.moduleClass

    val generatedModule = generateTypeSpec(pModuleClass, schema)
    val moduleClass = generatedModule.typeClass!!
    val moduleInterface = generatedModule.typeInterface

    for (pClass in schema.classes.values) {
      val generatedType = generateTypeSpec(pClass, schema)
      generatedType.typeInterface?.let { moduleClass.addType(it.build()) }
      generatedType.typeClass?.let { moduleClass.addType(it.build()) }
    }

    for (typeAlias in schema.typeAliases.values) {
      val stringLiterals = mutableSetOf<String>()
      if (CodeGeneratorUtils.isRepresentableAsEnum(typeAlias.aliasedType, stringLiterals)) {
        moduleClass.addType(generateEnumTypeSpec(typeAlias, stringLiterals).build())
      }
    }

    val (packageName, _) = nameMapper.map(schema.moduleName)

    val modulePair: kotlin.Pair<String, String> =
      moduleClass.let {
        getJavaFileName(isInterface = false) to
          JavaFile.builder(packageName, moduleClass.build())
            .indent(codegenOptions.indent)
            .build()
            .toString()
      }

    val interfacePair: kotlin.Pair<String, String>? =
      moduleInterface?.let {
        getJavaFileName(isInterface = true) to
          JavaFile.builder(packageName, moduleInterface.build())
            .indent(codegenOptions.indent)
            .build()
            .toString()
      }

    return listOfNotNull(modulePair, interfacePair).toMap()
  }

  private fun generateTypeSpec(pClass: PClass, schema: ModuleSchema): GeneratedType {
    val isModuleClass = pClass == schema.moduleClass
    val javaPoetClassName = pClass.toJavaPoetName()
    val superclass =
      pClass.superclass?.takeIf { it.info != PClassInfo.Typed && it.info != PClassInfo.Module }
    val superProperties =
      superclass?.let { renameIfReservedWord(it.allProperties) }?.filterValues { !it.isHidden }
        ?: mapOf()
    val properties = renameIfReservedWord(pClass.properties).filterValues { !it.isHidden }
    val allProperties = superProperties + properties

    fun PClass.Property.isRegex(): Boolean =
      (this.type as? PType.Class)?.pClass?.info == PClassInfo.Regex

    // do the minimum work necessary to avoid (most) java compile errors
    // generating idiomatic Javadoc would require parsing doc comments, converting member links,
    // etc.
    // TODO: consider Java 23 https://openjdk.org/jeps/467
    fun renderAsJavadoc(docComment: String): String {
      val escaped = docComment.replace("*/", "*&#47;").trimEnd()
      return if (escaped[escaped.length - 1] != '\n') escaped + '\n' else escaped
    }

    fun generateDeprecation(
      annotations: Collection<PObject>,
      hasJavadoc: Boolean,
      addAnnotation: (Class<*>) -> Unit,
      addJavadoc: (String) -> Unit,
    ) {
      annotations
        .firstOrNull { it.classInfo == PClassInfo.Deprecated }
        ?.let { deprecation ->
          addAnnotation(Deprecated::class.java)
          if (codegenOptions.generateJavadoc) {
            (deprecation["message"] as String?)?.let {
              if (hasJavadoc) {
                addJavadoc("\n")
              }
              addJavadoc(renderAsJavadoc("@deprecated $it"))
            }
          }
        }
    }

    fun generateRecordDeprecationJavadoc(builder: TypeSpec.Builder) {
      // generate Javadoc @deprecated on type (and on type only!
      // should combine the type deprecation first, followed by parameters deprecations in order!
      // TODO: refactor into its own function
      if (codegenOptions.generateJavadoc) {
        val deprecationStart =
          listOf(
            CodeBlock.of(
              """
              @deprecated
              
              """
                .trimIndent()
            )
          )

        val typeDeprecation =
          pClass.annotations
            .firstOrNull { it.classInfo == PClassInfo.Deprecated }
            ?.let { deprecation ->
              (deprecation["message"] as String?)?.let {
                CodeBlock.of("""${'$'}L""", renderAsJavadoc(it))
              }
            }
            ?.let { listOf(it) } ?: emptyList()

        val propEntries =
          superProperties.entries.filterNot { (k, _) -> properties.containsKey(k) } +
            properties.entries

        val paramsDeprecations =
          propEntries
            .map { (k, v) ->
              v.annotations
                .firstOrNull { it.classInfo == PClassInfo.Deprecated }
                ?.let { deprecation ->
                  (deprecation["message"] as String?)?.let {
                    CodeBlock.of("""${'$'}N - ${'$'}L""", k, renderAsJavadoc(it))
                  }
                }
            }
            .filterNotNull()

        (typeDeprecation + paramsDeprecations)
          .takeIf { it.isNotEmpty() }
          ?.let {
            builder.addJavadoc(CodeBlock.join(deprecationStart + CodeBlock.join(it, "<BR>"), ""))
          }
      }
    }

    fun addCtorParameter(
      builder: MethodSpec.Builder,
      propJavaName: String,
      property: PClass.Property,
    ) {
      val paramBuilder = ParameterSpec.builder(property.type.toJavaPoetName(), propJavaName)
      if (paramsAnnotationName != null) {
        paramBuilder.addAnnotation(
          AnnotationSpec.builder(paramsAnnotationName)
            .addMember("value", "\$S", property.simpleName)
            .build()
        )
      }

      val hasJavadoc = property.docComment != null && codegenOptions.generateJavadoc

      if (hasJavadoc) {
        paramBuilder.addJavadoc(renderAsJavadoc(property.docComment!!))
      }

      generateDeprecation(property.annotations, hasJavadoc, { paramBuilder.addAnnotation(it) }, {})

      builder.addParameter(paramBuilder.build())
    }

    fun generateConstructor(isInstantiable: Boolean): MethodSpec {
      val builder =
        MethodSpec.constructorBuilder()
          // choose most restrictive access modifier possible
          .addModifiers(
            when {
              isInstantiable -> Modifier.PUBLIC
              pClass.isAbstract || pClass.isOpen -> Modifier.PROTECTED
              else -> Modifier.PRIVATE
            }
          )

      if (superProperties.isNotEmpty()) {
        for ((name, property) in superProperties) {
          if (properties.containsKey(name)) continue
          addCtorParameter(builder, name, property)
        }
      }

      for ((name, property) in properties) {
        addCtorParameter(builder, name, property)
      }

      return builder.build()
    }

    fun generateSpringBootAnnotations(builder: TypeSpec.Builder) {
      if (isModuleClass) {
        builder.addAnnotation(
          ClassName.get("org.springframework.boot.context.properties", "ConfigurationProperties")
        )
      } else {
        // not very efficient to repeat computing module property base types for every class
        val modulePropertiesWithMatchingType =
          schema.moduleClass.allProperties.values.filter { property ->
            var propertyType = property.type
            while (propertyType is PType.Constrained || propertyType is PType.Nullable) {
              if (propertyType is PType.Constrained) {
                propertyType = propertyType.baseType
              } else if (propertyType is PType.Nullable) {
                propertyType = propertyType.baseType
              }
            }
            propertyType is PType.Class && propertyType.pClass == pClass
          }
        if (modulePropertiesWithMatchingType.size == 1) {
          // exactly one module property has this type -> make it available for direct injection
          // (potential improvement: make type available for direct injection if it occurs exactly
          // once in property tree)
          builder.addAnnotation(
            AnnotationSpec.builder(
                ClassName.get(
                  "org.springframework.boot.context.properties",
                  "ConfigurationProperties",
                )
              )
              // use "value" instead of "prefix" to entice JavaPoet to generate a single-line
              // annotation
              // that can easily be filtered out by JavaCodeGeneratorTest.`spring boot config`
              .addMember("value", "\$S", modulePropertiesWithMatchingType.first().simpleName)
              .build()
          )
        }
      }
    }

    fun generateLombokAnnotations(builder: TypeSpec.Builder) {
      builder.addAnnotation(ClassName.get("lombok", "Builder"))
    }

    fun generateWither(builder: TypeSpec.Builder, constructorSpec: MethodSpec) {
      builder.addSuperinterface(
        ParameterizedTypeName.get(
          ClassName.get(commonCodePackage, "Wither"),
          TypeVariableName.get(javaPoetClassName.simpleName()),
          TypeVariableName.get(javaPoetClassName.simpleName() + ".Memento"),
        )
      )

      val withMethod =
        MethodSpec.methodBuilder("with")
          .addModifiers(Modifier.PUBLIC)
          .addParameter(
            ParameterizedTypeName.get(
                ClassName.get(Consumer::class.java),
                ClassName.get("", "Memento"),
              )
              .nullableIf(false),
            "setter",
            Modifier.FINAL,
          )
          .returns(ClassName.get("", javaPoetClassName.simpleName()).nullableIf(false))
          .addAnnotation(Override::class.java)
          .addCode(
            """
              final var memento = new Memento(this);
              setter.accept(memento);
              return memento.build();
            """
              .trimIndent()
          )
          .build()

      builder.addMethod(withMethod)

      // add Memento class
      val memento =
        TypeSpec.classBuilder("Memento")
          .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
          .addFields(
            constructorSpec.parameters().map {
              FieldSpec.builder(it.type(), it.name(), Modifier.PUBLIC).build()
            }
          )
          .addMethod(
            MethodSpec.constructorBuilder()
              .addModifiers(Modifier.PRIVATE)
              .addParameter(
                ClassName.get("", javaPoetClassName.simpleName()).nullableIf(false),
                "r",
                Modifier.FINAL,
              )
              .addCode(
                CodeBlock.builder()
                  .apply {
                    constructorSpec.parameters().forEach {
                      addStatement("${'$'}1N = r.${'$'}1N", it.name())
                    }
                  }
                  .build()
              )
              .build()
          )
          .addMethod(
            MethodSpec.methodBuilder("build")
              .addModifiers(Modifier.PRIVATE)
              .returns(ClassName.get("", javaPoetClassName.simpleName()).nullableIf(false))
              .addCode(
                CodeBlock.builder()
                  .addStatement(
                    "return new \$L(\$L)",
                    javaPoetClassName.simpleName(),
                    constructorSpec.parameters().joinToString { it.name() },
                  )
                  .build()
              )
              .build()
          )
          .build()

      builder.addType(memento)
    }

    fun generateInterface(): TypeSpec.Builder {
      val builder =
        TypeSpec.interfaceBuilder(
            "${if (pClass.isOpen) "I" else ""}${javaPoetClassName.simpleName()}"
          )
          .addModifiers(Modifier.PUBLIC)

      val docComment = pClass.docComment
      val hasJavadoc = docComment != null && codegenOptions.generateJavadoc
      if (hasJavadoc) {
        builder.addJavadoc(renderAsJavadoc(docComment!!))
      }

      generateDeprecation(
        pClass.annotations,
        hasJavadoc,
        { builder.addAnnotation(it) },
        { builder.addJavadoc(it) },
      )

      superclass?.let { builder.addSuperinterface(it.toJavaPoetName(forceInterface = true)) }

      for ((name, property) in properties) {
        val methodBuilder =
          MethodSpec.methodBuilder(name)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .returns(property.type.toJavaPoetName(forceInterface = true))

        val docComment = property.docComment
        val hasJavadoc = docComment != null && codegenOptions.generateJavadoc
        if (hasJavadoc) {
          methodBuilder.addJavadoc(renderAsJavadoc(docComment!!))
        }

        generateDeprecation(
          property.annotations,
          hasJavadoc,
          { methodBuilder.addAnnotation(it) },
          { methodBuilder.addJavadoc(it) },
        )

        builder.addMethod(methodBuilder.build())
      }

      return builder
    }

    @Suppress("DuplicatedCode")
    fun generateClass(): TypeSpec.Builder {
      val builder =
        TypeSpec.recordBuilder(javaPoetClassName.simpleName()).addModifiers(Modifier.PUBLIC)

      if (codegenOptions.generatedAnnotation) {
        val name = ClassName.get("org.pkl.config.java", "Generated")
        val generated = AnnotationSpec.builder(name).build()
        builder.addAnnotation(generated)
      }

      val docComment = pClass.docComment
      val hasJavadoc = docComment != null && codegenOptions.generateJavadoc
      if (hasJavadoc) {
        builder.addJavadoc(renderAsJavadoc(docComment!!))
      }

      generateDeprecation(
        pClass.annotations,
        hasJavadoc,
        { builder.addAnnotation(it) },
        //        { builder.addJavadoc(it) },
        {},
      )

      generateRecordDeprecationJavadoc(builder)

      if (codegenOptions.generateSpringBootConfig) {
        generateSpringBootAnnotations(builder)
      }

      superclass?.let { builder.addSuperinterface(it.toJavaPoetName(forceInterface = true)) }

      if (pClass.isOpen) {
        builder.addSuperinterface(pClass.toJavaPoetName(forceInterface = true))
      }

      // stateless final module classes are non-instantiable by choice
      val isInstantiable =
        !(pClass.isAbstract || (isModuleClass && !pClass.isOpen && allProperties.isEmpty()))

      val constructorSpec = generateConstructor(isInstantiable)
      builder.recordConstructor(constructorSpec)

      if (isInstantiable) {
        if (codegenOptions.implementSerializable) {
          builder.addSuperinterface(java.io.Serializable::class.java)
        }

        if (codegenOptions.useWithers) {
          generateWither(builder, constructorSpec)
        }

        if (codegenOptions.useLombokBuilders) {
          generateLombokAnnotations(builder)
        }
      }

      return builder
    }

    return when {
      pClass.isAbstract ->
        if (pClass.isModuleClass) GeneratedType(generateInterface(), null)
        else GeneratedType(null, generateInterface())

      pClass.isOpen -> GeneratedType(generateClass(), generateInterface())
      else -> GeneratedType(generateClass(), null)
    }
  }

  private fun generateEnumTypeSpec(
    typeAlias: TypeAlias,
    stringLiterals: Set<String>,
  ): TypeSpec.Builder {
    val enumConstantToPklNames =
      stringLiterals
        .groupingBy { literal ->
          CodeGeneratorUtils.toEnumConstantName(literal)
            ?: throw JavaCodeGeneratorException(
              "Cannot generate Java enum class for Pkl type alias `${typeAlias.displayName}` " +
                "because string literal type \"$literal\" cannot be converted to a valid enum constant name."
            )
        }
        .reduce { enumConstantName, firstLiteral, secondLiteral ->
          throw JavaCodeGeneratorException(
            "Cannot generate Java enum class for Pkl type alias `${typeAlias.displayName}` " +
              "because string literal types \"$firstLiteral\" and \"$secondLiteral\" " +
              "would both be converted to enum constant name `$enumConstantName`."
          )
        }

    val builder =
      TypeSpec.enumBuilder(typeAlias.simpleName)
        .addModifiers(Modifier.PUBLIC)
        .addField(String::class.java, "value", Modifier.PRIVATE)
        .addMethod(
          MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PRIVATE)
            .addParameter(String::class.java, "value")
            .addStatement("this.value = value")
            .build()
        )
        .addMethod(
          MethodSpec.methodBuilder("toString")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override::class.java)
            .returns(String::class.java)
            .addStatement("return this.value")
            .build()
        )

    for ((enumConstantName, pklName) in enumConstantToPklNames) {
      builder.addEnumConstant(
        enumConstantName,
        TypeSpec.anonymousClassBuilder("\$S", pklName).build(),
      )
    }

    return builder
  }

  private val paramsAnnotationName: ClassName? =
    codegenOptions.paramsAnnotation?.let { toClassName(it) }

  private fun PClass.toJavaPoetName(forceInterface: Boolean = false): ClassName {
    val (packageName, moduleClassName) = nameMapper.map(moduleName)
    return if (isModuleClass) {
      ClassName.get(packageName, "${if (forceInterface && isOpen) "I" else ""}${moduleClassName}")
    } else {
      ClassName.get(
        packageName,
        moduleClassName,
        "${if (forceInterface && isOpen) "I" else ""}${simpleName}",
      )
    }
  }

  // generated type is a nested enum class
  private fun TypeAlias.toJavaPoetName(): ClassName {
    val (packageName, moduleClassName) = nameMapper.map(moduleName)
    return ClassName.get(packageName, moduleClassName, simpleName)
  }

  /** Generate `List<? extends Foo>` if `Foo` is `abstract` or `open`, to allow subclassing. */
  private fun PType.toJavaPoetTypeArgumentName(forceInterface: Boolean = false): TypeName {
    val baseName = toJavaPoetName(boxed = true, forceInterface = forceInterface)
    return if (this is PType.Class && (pClass.isAbstract || pClass.isOpen)) {
      WildcardTypeName.subtypeOf(baseName)
    } else {
      baseName
    }
  }

  private fun PType.toJavaPoetName(
    nullable: Boolean = false,
    boxed: Boolean = false,
    forceInterface: Boolean = false,
  ): TypeName =
    when (this) {
      PType.UNKNOWN -> OBJECT.nullableIf(nullable)
      PType.NOTHING -> TypeName.VOID
      is PType.StringLiteral -> STRING.nullableIf(nullable)
      is PType.Class -> {
        // if in doubt, spell it out
        when (val classInfo = pClass.info) {
          PClassInfo.Any -> OBJECT
          PClassInfo.Typed,
          PClassInfo.Dynamic -> OBJECT.nullableIf(nullable)
          PClassInfo.Boolean -> TypeName.BOOLEAN.boxIf(boxed).nullableIf(nullable)
          PClassInfo.String -> STRING.nullableIf(nullable)
          // seems more useful to generate `double` than `java.lang.Number`
          PClassInfo.Number -> TypeName.DOUBLE.boxIf(boxed).nullableIf(nullable)
          PClassInfo.Int -> TypeName.LONG.boxIf(boxed).nullableIf(nullable)
          PClassInfo.Float -> TypeName.DOUBLE.boxIf(boxed).nullableIf(nullable)
          PClassInfo.Duration -> DURATION.nullableIf(nullable)
          PClassInfo.DataSize -> DATA_SIZE.nullableIf(nullable)
          PClassInfo.Pair ->
            ParameterizedTypeName.get(
                PAIR,
                if (typeArguments.isEmpty()) {
                  OBJECT
                } else {
                  typeArguments[0].toJavaPoetTypeArgumentName(forceInterface = forceInterface)
                },
                if (typeArguments.isEmpty()) {
                  OBJECT
                } else {
                  typeArguments[1].toJavaPoetTypeArgumentName(forceInterface = forceInterface)
                },
              )
              .nullableIf(nullable)

          PClassInfo.Collection ->
            ParameterizedTypeName.get(
                COLLECTION,
                if (typeArguments.isEmpty()) {
                  OBJECT
                } else {
                  typeArguments[0].toJavaPoetTypeArgumentName(forceInterface = forceInterface)
                },
              )
              .nullableIf(nullable)

          PClassInfo.List,
          PClassInfo.Listing -> {
            ParameterizedTypeName.get(
                LIST,
                if (typeArguments.isEmpty()) {
                  OBJECT
                } else {
                  typeArguments[0].toJavaPoetTypeArgumentName(forceInterface = forceInterface)
                },
              )
              .nullableIf(nullable)
          }

          PClassInfo.Set ->
            ParameterizedTypeName.get(
                SET,
                if (typeArguments.isEmpty()) {
                  OBJECT
                } else {
                  typeArguments[0].toJavaPoetTypeArgumentName(forceInterface = forceInterface)
                },
              )
              .nullableIf(nullable)

          PClassInfo.Map,
          PClassInfo.Mapping ->
            ParameterizedTypeName.get(
                MAP,
                if (typeArguments.isEmpty()) {
                  OBJECT
                } else {
                  typeArguments[0].toJavaPoetTypeArgumentName(forceInterface = forceInterface)
                },
                if (typeArguments.isEmpty()) {
                  OBJECT
                } else {
                  typeArguments[1].toJavaPoetTypeArgumentName(forceInterface = forceInterface)
                },
              )
              .nullableIf(nullable)

          PClassInfo.Module -> PMODULE.nullableIf(nullable)
          PClassInfo.Class -> PCLASS.nullableIf(nullable)
          PClassInfo.Regex -> PATTERN.nullableIf(nullable)
          PClassInfo.Version -> VERSION.nullableIf(nullable)
          else ->
            when {
              !classInfo.isStandardLibraryClass ->
                pClass.toJavaPoetName(forceInterface = forceInterface).nullableIf(nullable)

              else ->
                throw JavaCodeGeneratorException(
                  "Standard library class `${pClass.qualifiedName}` is not supported by Java code generator. " +
                    "If you think this is an omission, please let us know."
                )
            }
        }
      }

      is PType.Nullable ->
        baseType.toJavaPoetName(forceInterface = forceInterface, nullable = true, boxed = true)

      is PType.Constrained ->
        baseType.toJavaPoetName(forceInterface = forceInterface, nullable = nullable, boxed = boxed)

      is PType.Alias ->
        when (typeAlias.qualifiedName) {
          "pkl.base#NonNull" -> OBJECT.nullableIf(nullable)
          "pkl.base#Int8" -> TypeName.BYTE.boxIf(boxed).nullableIf(nullable)
          "pkl.base#Int16",
          "pkl.base#UInt8" -> TypeName.SHORT.boxIf(boxed).nullableIf(nullable)
          "pkl.base#Int32",
          "pkl.base#UInt16" -> TypeName.INT.boxIf(boxed).nullableIf(nullable)
          "pkl.base#UInt",
          "pkl.base#UInt32" -> TypeName.LONG.boxIf(boxed).nullableIf(nullable)
          "pkl.base#DurationUnit" -> DURATION_UNIT.nullableIf(nullable)
          "pkl.base#DataSizeUnit" -> DATASIZE_UNIT.nullableIf(nullable)
          "pkl.base#Uri" -> URI.nullableIf(nullable)
          else -> {
            if (CodeGeneratorUtils.isRepresentableAsEnum(aliasedType, null)) {
              if (typeAlias.isStandardLibraryMember) {
                throw JavaCodeGeneratorException(
                  "Standard library typealias `${typeAlias.qualifiedName}` is not supported by Java code generator. " +
                    "If you think this is an omission, please let us know."
                )
              } else {
                // reference generated enum class
                typeAlias.toJavaPoetName().nullableIf(nullable)
              }
            } else {
              // inline type alias
              aliasedType.toJavaPoetName(nullable)
            }
          }
        }

      is PType.Function ->
        throw JavaCodeGeneratorException(
          "Pkl function types are not supported by the Java code generator."
        )

      is PType.Union ->
        if (CodeGeneratorUtils.isRepresentableAsString(this)) STRING.nullableIf(nullable)
        else
          throw JavaCodeGeneratorException(
            "Pkl union types are not supported by the Java code generator."
          )

      else ->
        // should never encounter PType.TypeVariableNode because it can only occur in stdlib classes
        throw AssertionError("Encountered unexpected PType subclass: $this")
    }

  private fun TypeName.nullableIf(isNullable: Boolean): TypeName =
    if (isPrimitive && isNullable) box()
    else if (isPrimitive || isNullable) this else annotated(nonNullAnnotation(codegenOptions))

  private fun TypeName.boxIf(shouldBox: Boolean): TypeName = if (shouldBox) box() else this

  private fun <T> renameIfReservedWord(map: Map<String, T>): Map<String, T> {
    return map.mapKeys { (key, _) ->
      if (key in javaReservedWords) {
        generateSequence("_$key") { "_$it" }.first { it !in map.keys }
      } else key
    }
  }

  private val nameMapper = NameMapper(codegenOptions.renames)
}

internal val javaReservedWords =
  setOf(
    "_", // java 9+
    "abstract",
    "assert",
    "boolean",
    "break",
    "byte",
    "case",
    "catch",
    "char",
    "class",
    "const",
    "continue",
    "default",
    "double",
    "do",
    "else",
    "enum",
    "extends",
    "false",
    "final",
    "finally",
    "float",
    "for",
    "goto",
    "if",
    "implements",
    "import",
    "instanceof",
    "int",
    "interface",
    "long",
    "native",
    "new",
    "null",
    "package",
    "private",
    "protected",
    "public",
    "return",
    "record",
    "short",
    "static",
    "strictfp",
    "super",
    "switch",
    "synchronized",
    "this",
    "throw",
    "throws",
    "transient",
    "true",
    "try",
    "void",
    "volatile",
    "while",
  )
