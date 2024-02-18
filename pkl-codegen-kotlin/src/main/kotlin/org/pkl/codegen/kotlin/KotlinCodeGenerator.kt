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
package org.pkl.codegen.kotlin

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.StringWriter
import java.net.URI
import java.util.*
import kotlinx.serialization.Serializable
import org.pkl.core.*
import org.pkl.core.util.CodeGeneratorUtils

data class KotlinCodegenOptions(
  /** The characters to use for indenting generated Kotlin code. */
  val indent: String = "  ",

  /** Kotlin package to use for generated code; if none is provided, the root package is used. */
  val kotlinPackage: String = "",

  /** Whether to generate KDoc based on doc comments for Pkl modules, classes, and properties. */
  val generateKdoc: Boolean = false,

  /** Whether to generate config classes for use with Spring Boot. */
  val generateSpringBootConfig: Boolean = false,

  /** Whether to make generated classes implement [java.io.Serializable] */
  val implementSerializable: Boolean = false,

  /** Whether to annotate data classes with [kotlinx.serialization.Serializable] */
  val implementKSerializable: Boolean = false,
)

class KotlinCodeGeneratorException(message: String) : RuntimeException(message)

/** Entrypoint for the Kotlin code generator API. */
class KotlinCodeGenerator(
  /** The schema for the module to generate */
  private val moduleSchema: ModuleSchema,

  /** The options to use for the code generator */
  private val options: KotlinCodegenOptions,
) {
  companion object {
    // Prevent class name from being replaced with shaded name
    // when pkl-codegen-kotlin is shaded and embedded in pkl-tools
    // (requires circumventing kotlinc constant folding).
    private val KOTLIN_TEXT_PACKAGE_NAME = buildString {
      append("kot")
      append("lin.")
      append("text")
    }

    // `StringBuilder::class.asClassName()` generates "java.lang.StringBuilder",
    // apparently because `StringBuilder` is an `expect class`.
    private val STRING_BUILDER = ClassName(KOTLIN_TEXT_PACKAGE_NAME, "StringBuilder")

    private val STRING = String::class.asClassName()
    private val ANY_NULL = ANY.copy(nullable = true)
    private val NOTHING = Nothing::class.asClassName()
    private val KOTLIN_PAIR = kotlin.Pair::class.asClassName()
    private val COLLECTION = Collection::class.asClassName()
    private val LIST = List::class.asClassName()
    private val SET = Set::class.asClassName()
    private val MAP = Map::class.asClassName()
    private val DURATION = Duration::class.asClassName()
    private val DURATION_UNIT = DurationUnit::class.asClassName()
    private val DATA_SIZE = DataSize::class.asClassName()
    private val DATA_SIZE_UNIT = DataSizeUnit::class.asClassName()
    private val PMODULE = PModule::class.asClassName()
    private val PCLASS = PClass::class.asClassName()
    private val REGEX = Regex::class.asClassName()
    private val URI = URI::class.asClassName()
    private val VERSION = Version::class.asClassName()

    private const val PROPERTY_PREFIX: String = "org.pkl.config.java.mapper."
  }

  val output: Map<String, String>
    get() {
      return mapOf(kotlinFileName to kotlinFile, propertyFileName to propertiesFile)
    }

  private val propertyFileName: String
    get() =
      "resources/META-INF/org/pkl/config/java/mapper/classes/${moduleSchema.moduleName}.properties"

  private val propertiesFile: String
    get() {
      val props = Properties()
      props["$PROPERTY_PREFIX${moduleSchema.moduleClass.qualifiedName}"] =
        moduleSchema.moduleClass.toKotlinPoetName().reflectionName()
      for (pClass in moduleSchema.classes.values) {
        props["$PROPERTY_PREFIX${pClass.qualifiedName}"] =
          pClass.toKotlinPoetName().reflectionName()
      }
      return StringWriter()
        .apply { props.store(this, "Kotlin mappings for Pkl module `${moduleSchema.moduleName}`") }
        .toString()
    }

  val kotlinFileName: String
    get() = buildString {
      append("kot")
      append("lin/${relativeOutputPathFor(moduleSchema.moduleName)}")
    }

  val kotlinPackage: String?
    get() = options.kotlinPackage.ifEmpty { null }

  val kotlinFile: String
    get() {
      if (moduleSchema.moduleUri.scheme == "pkl") {
        throw KotlinCodeGeneratorException(
          "Cannot generate Kotlin code for a Pkl standard library module (`${moduleSchema.moduleUri}`)."
        )
      }

      val pModuleClass = moduleSchema.moduleClass

      val hasProperties = pModuleClass.properties.any { !it.value.isHidden }
      val isGenerateClass = hasProperties || pModuleClass.isOpen || pModuleClass.isAbstract
      val packagePrefix = kotlinPackage?.let { "$it." } ?: ""
      val moduleType =
        if (isGenerateClass) {
          generateTypeSpec(pModuleClass, moduleSchema)
        } else {
          generateObjectSpec(pModuleClass)
        }

      for (pClass in moduleSchema.classes.values) {
        moduleType.addType(
          generateTypeSpec(pClass, moduleSchema)
            .apply {
              ensureSerializable()
              ensureKSerializable()
            }
            .build()
        )
      }

      // generate append method for module classes w/o parent class; reuse in subclasses and nested
      // classes
      val isGenerateAppendPropertyMethod =
        // check if we can inherit someone else's append method
        pModuleClass.superclass!!.info == PClassInfo.Module &&
          // check if anyone is (potentially) going to use our append method
          (pModuleClass.isOpen ||
            pModuleClass.isAbstract ||
            (hasProperties && !moduleType.modifiers.contains(KModifier.DATA)) ||
            moduleType.typeSpecs.any { !it.modifiers.contains(KModifier.DATA) })

      if (isGenerateAppendPropertyMethod) {
        val appendPropertyMethodModifier =
          if (pModuleClass.isOpen || pModuleClass.isAbstract) {
            // alternative is `@JvmStatic protected`
            // (`protected` alone isn't sufficient as of Kotlin 1.6)
            KModifier.PUBLIC
          } else KModifier.PRIVATE
        if (isGenerateClass) {
          moduleType.addType(
            TypeSpec.companionObjectBuilder()
              .addFunction(
                appendPropertyMethod().addModifiers(appendPropertyMethodModifier).build()
              )
              .build()
          )
        } else { // kotlin object
          moduleType.addFunction(
            appendPropertyMethod().addModifiers(appendPropertyMethodModifier).build()
          )
        }
      }

      val moduleName = moduleSchema.moduleName
      val index = moduleName.lastIndexOf(".")
      val packageName = if (index == -1) "" else moduleName.substring(0, index)
      val moduleTypeName = moduleName.substring(index + 1).replaceFirstChar { it.titlecaseChar() }

      val packagePath =
        if (packagePrefix.isNotBlank()) "$packagePrefix.$packageName" else packageName
      val fileSpec = FileSpec.builder(packagePath, moduleTypeName).indent(options.indent)

      for (typeAlias in moduleSchema.typeAliases.values) {
        if (typeAlias.aliasedType is PType.Alias) {
          // generate top-level type alias (Kotlin doesn't support nested type aliases)
          fileSpec.addTypeAlias(generateTypeAliasSpec(typeAlias).build())
        } else {
          val stringLiterals = mutableSetOf<String>()
          if (CodeGeneratorUtils.isRepresentableAsEnum(typeAlias.aliasedType, stringLiterals)) {
            // generate nested enum class
            moduleType.addType(generateEnumTypeSpec(typeAlias, stringLiterals).build())
          } else {
            // generate top-level type alias (Kotlin doesn't support nested type aliases)
            fileSpec.addTypeAlias(generateTypeAliasSpec(typeAlias).build())
          }
        }
      }

      fileSpec.addType(moduleType.build())
      return fileSpec.build().toString()
    }

  private fun relativeOutputPathFor(moduleName: String): String {
    val nameParts = moduleName.split(".")
    val dirPath = nameParts.dropLast(1).joinToString("/")
    val fileName = nameParts.last().replaceFirstChar { it.titlecaseChar() }
    return if (dirPath.isEmpty()) {
      "$fileName.kt"
    } else {
      "$dirPath/$fileName.kt"
    }
  }

  private fun generateObjectSpec(pClass: PClass): TypeSpec.Builder {
    val builder = TypeSpec.objectBuilder(pClass.toKotlinPoetName())
    val docComment = pClass.docComment
    if (docComment != null && options.generateKdoc) {
      builder.addKdoc(renderAsKdoc(docComment))
    }
    return builder
  }

  private fun generateTypeSpec(pClass: PClass, schema: ModuleSchema): TypeSpec.Builder {
    val isModuleClass = pClass == schema.moduleClass
    val kotlinPoetClassName = pClass.toKotlinPoetName()
    val superclass =
      pClass.superclass?.takeIf { it.info != PClassInfo.Typed && it.info != PClassInfo.Module }
    val superProperties = superclass?.allProperties?.filterValues { !it.isHidden } ?: mapOf()
    val properties = pClass.properties.filterValues { !it.isHidden }
    val allProperties = superProperties + properties

    fun PClass.Property.isRegex(): Boolean =
      (this.type as? PType.Class)?.pClass?.info == PClassInfo.Regex

    val containRegexProperty = properties.values.any { it.isRegex() }

    fun generateConstructor(): FunSpec {
      val builder = FunSpec.constructorBuilder()
      for ((name, property) in allProperties) {
        builder.addParameter(name, property.type.toKotlinPoetName())
      }
      return builder.build()
    }

    fun generateCopyMethod(parameters: Map<String, PClass.Property>, isOverride: Boolean): FunSpec {
      val methodBuilder = FunSpec.builder("copy").returns(kotlinPoetClassName)

      if (isOverride) {
        methodBuilder.addModifiers(KModifier.OVERRIDE)
      }
      if (pClass.isOpen || pClass.isAbstract) {
        methodBuilder.addModifiers(KModifier.OPEN)
      }

      for ((name, property) in parameters) {
        val paramBuilder = ParameterSpec.builder(name, property.type.toKotlinPoetName())
        if (!isOverride) {
          paramBuilder.defaultValue("this.%N", name)
        }
        methodBuilder.addParameter(paramBuilder.build())
      }

      val codeBuilder = CodeBlock.builder().add("return %T(", kotlinPoetClassName)
      var firstProperty = true
      for (name in allProperties.keys) {
        if (firstProperty) {
          codeBuilder.add("%N", name)
          firstProperty = false
        } else {
          codeBuilder.add(", %N", name)
        }
      }
      codeBuilder.add(")\n")

      return methodBuilder.addCode(codeBuilder.build()).build()
    }

    // besides generating copy method for the current class,
    // override copy methods inherited from parent classes
    fun generateCopyMethods(typeBuilder: TypeSpec.Builder) {
      var prevParameterCount = Int.MAX_VALUE
      for (currClass in generateSequence(pClass) { it.superclass }) {
        if (currClass.isAbstract) continue

        val currParameters = currClass.allProperties.filter { !it.value.isHidden }

        // avoid generating multiple methods with same no. of parameters
        if (currParameters.size < prevParameterCount) {
          val isOverride = currClass !== pClass || superclass != null && properties.isEmpty()
          typeBuilder.addFunction(generateCopyMethod(currParameters, isOverride))
          prevParameterCount = currParameters.size
        }
      }
    }

    fun generateEqualsMethod(): FunSpec {
      val builder =
        FunSpec.builder("equals")
          .addModifiers(KModifier.OVERRIDE)
          .addParameter("other", ANY_NULL)
          .returns(BOOLEAN)
          .addStatement("if (this === other) return true")
          // generating this.javaClass instead of class literal avoids a SpotBugs warning
          .addStatement("if (this.javaClass != other?.javaClass) return false")
          .addStatement("other as %T", kotlinPoetClassName)

      for ((propertyName, property) in allProperties) {
        val accessor = if (property.isRegex()) "%N.pattern" else "%N"
        builder.addStatement(
          "if (this.$accessor != other.$accessor) return false",
          propertyName,
          propertyName
        )
      }

      builder.addStatement("return true")
      return builder.build()
    }

    fun generateHashCodeMethod(): FunSpec {
      val builder =
        FunSpec.builder("hashCode")
          .addModifiers(KModifier.OVERRIDE)
          .returns(INT)
          .addStatement("var result = 1")

      for (propertyName in allProperties.keys) {
        // use Objects.hashCode() because Kotlin's Any?.hashCode()
        // doesn't work for platform types (will get NPE if null)
        builder.addStatement(
          "result = 31 * result + %T.hashCode(this.%N)",
          Objects::class,
          propertyName
        )
      }

      builder.addStatement("return result")
      return builder.build()
    }

    fun generateToStringMethod(): FunSpec {
      val builder = FunSpec.builder("toString").addModifiers(KModifier.OVERRIDE).returns(STRING)

      var builderSize = 50
      val appendBuilder = CodeBlock.builder()
      for (propertyName in allProperties.keys) {
        builderSize += 50
        appendBuilder.addStatement(
          "appendProperty(builder, %S, this.%N)",
          propertyName,
          propertyName
        )
      }

      builder
        .addStatement("val builder = %T(%L)", STRING_BUILDER, builderSize)
        .addStatement(
          // generate `::class.java.simpleName` instead of `::class.simpleName`
          // to avoid making user code depend on kotlin-reflect
          "builder.append(%T::class.java.simpleName).append(\" {\")",
          kotlinPoetClassName
        )
        .addCode(appendBuilder.build())
        // not using %S here because it generates `"\n" + "{"`
        // with a line break in the generated code after `+`
        .addStatement("builder.append(\"\\n}\")")
        .addStatement("return builder.toString()")

      return builder.build()
    }

    fun generateDeprecation(
      annotations: Collection<PObject>,
      addAnnotation: (AnnotationSpec) -> Unit
    ) {
      annotations
        .firstOrNull { it.classInfo == PClassInfo.Deprecated }
        ?.let { deprecation ->
          val builder = AnnotationSpec.builder(Deprecated::class)
          (deprecation["message"] as String?)?.let { builder.addMember("message = %S", it) }
          addAnnotation(builder.build())
        }
    }

    fun generateProperty(propertyName: String, property: PClass.Property): PropertySpec {
      val typeName = property.type.toKotlinPoetName()
      val builder = PropertySpec.builder(propertyName, typeName).initializer("%L", propertyName)

      generateDeprecation(property.annotations) { builder.addAnnotation(it) }

      val docComment = property.docComment
      if (docComment != null && options.generateKdoc) {
        builder.addKdoc(renderAsKdoc(docComment))
      }
      if (propertyName in superProperties) {
        builder.addModifiers(KModifier.OVERRIDE)
      }
      if (pClass.isOpen || pClass.isAbstract) {
        builder.addModifiers(KModifier.OPEN)
      }

      return builder.build()
    }

    fun generateSpringBootAnnotations(builder: TypeSpec.Builder) {
      builder.addAnnotation(
        ClassName("org.springframework.boot.context.properties", "ConstructorBinding")
      )

      if (isModuleClass) {
        builder.addAnnotation(
          ClassName("org.springframework.boot.context.properties", "ConfigurationProperties")
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
                ClassName("org.springframework.boot.context.properties", "ConfigurationProperties")
              )
              // use "value" instead of "prefix" to entice JavaPoet to generate a single-line
              // annotation
              // that can easily be filtered out by JavaCodeGeneratorTest.`spring boot config`
              .addMember("%S", modulePropertiesWithMatchingType.first().simpleName)
              .build()
          )
        }
      }
    }

    fun generateRegularClass(): TypeSpec.Builder {
      val builder = TypeSpec.classBuilder(kotlinPoetClassName)

      if (options.generateSpringBootConfig) {
        generateSpringBootAnnotations(builder)
      }

      builder.primaryConstructor(generateConstructor())

      val docComment = pClass.docComment
      if (docComment != null && options.generateKdoc) {
        builder.addKdoc(renderAsKdoc(docComment))
      }

      if (pClass.isAbstract) {
        builder.addModifiers(KModifier.ABSTRACT)
      } else if (pClass.isOpen) {
        builder.addModifiers(KModifier.OPEN)
      }

      superclass?.let { superclass ->
        val superclassName = superclass.toKotlinPoetName()
        builder.superclass(superclassName)
        for (propertyName in superProperties.keys) {
          builder.addSuperclassConstructorParameter(propertyName)
        }
      }

      for ((name, property) in properties) {
        builder.addProperty(generateProperty(name, property))
      }

      generateCopyMethods(builder)

      builder
        .addFunction(generateEqualsMethod())
        .addFunction(generateHashCodeMethod())
        .addFunction(generateToStringMethod())

      return builder
    }

    fun generateDataClass(): TypeSpec.Builder {
      val builder = TypeSpec.classBuilder(kotlinPoetClassName).addModifiers(KModifier.DATA)

      if (options.generateSpringBootConfig) {
        generateSpringBootAnnotations(builder)
      }

      builder.primaryConstructor(generateConstructor())

      generateDeprecation(pClass.annotations) { builder.addAnnotation(it) }

      val docComment = pClass.docComment
      if (docComment != null && options.generateKdoc) {
        builder.addKdoc(renderAsKdoc(docComment))
      }

      for ((name, property) in properties) {
        builder.addProperty(generateProperty(name, property))
      }

      // Regex requires special approach when compared to another Regex
      // So we need to override `.equals` method even for kotlin's `data class`es if
      // any of the properties is of Regex type
      if (containRegexProperty) {
        builder.addFunction(generateEqualsMethod())
      }

      return builder
    }

    return if (superclass == null && !pClass.isAbstract && !pClass.isOpen) generateDataClass()
    else generateRegularClass()
  }

  private fun TypeSpec.Builder.ensureKSerializable(): TypeSpec.Builder {
    if (!options.implementKSerializable) {
      return this
    }

    val spec = AnnotationSpec.builder(Serializable::class).build()
    if (!this.annotationSpecs.contains(spec)) {
      addAnnotation(spec)
    }
    return this
  }

  private fun TypeSpec.Builder.ensureSerializable(): TypeSpec.Builder {
    if (!options.implementSerializable) {
      return this
    }

    if (!this.superinterfaces.containsKey(java.io.Serializable::class.java.asTypeName())) {
      this.addSuperinterface(java.io.Serializable::class.java)
    }

    var useExistingCompanionBuilder = false
    val companionBuilder =
      this.typeSpecs
        .find { it.isCompanion }
        ?.let {
          useExistingCompanionBuilder = true
          it.toBuilder(TypeSpec.Kind.OBJECT)
        }
        ?: TypeSpec.companionObjectBuilder()

    if (!companionBuilder.propertySpecs.any { it.name == "serialVersionUID" })
      companionBuilder.addProperty(
        PropertySpec.builder(
            "serialVersionUID",
            Long::class.java,
            KModifier.PRIVATE,
            KModifier.CONST
          )
          .initializer("0L")
          .build()
      )

    if (!useExistingCompanionBuilder) {
      this.addType(companionBuilder.build())
    }

    return this
  }

  private fun generateEnumTypeSpec(
    typeAlias: TypeAlias,
    stringLiterals: Set<String>
  ): TypeSpec.Builder {
    val enumConstantToPklNames =
      stringLiterals
        .groupingBy { literal ->
          CodeGeneratorUtils.toEnumConstantName(literal)
            ?: throw KotlinCodeGeneratorException(
              "Cannot generate Kotlin enum class for Pkl type alias `${typeAlias.displayName}` " +
                "because string literal type \"$literal\" cannot be converted to a valid enum constant name."
            )
        }
        .reduce { enumConstantName, firstLiteral, secondLiteral ->
          throw KotlinCodeGeneratorException(
            "Cannot generate Kotlin enum class for Pkl type alias `${typeAlias.displayName}` " +
              "because string literal types \"$firstLiteral\" and \"$secondLiteral\" " +
              "would both be converted to enum constant name `$enumConstantName`."
          )
        }

    val builder =
      TypeSpec.enumBuilder(typeAlias.simpleName)
        .primaryConstructor(
          FunSpec.constructorBuilder().addParameter("value", String::class).build()
        )
        .addProperty(PropertySpec.builder("value", String::class).initializer("value").build())
        .addFunction(
          FunSpec.builder("toString")
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("return value")
            .build()
        )
    for ((enumConstantName, pklName) in enumConstantToPklNames) {
      builder.addEnumConstant(
        enumConstantName,
        TypeSpec.anonymousClassBuilder().addSuperclassConstructorParameter("%S", pklName).build()
      )
    }

    return builder
  }

  private fun generateTypeAliasSpec(typeAlias: TypeAlias): TypeAliasSpec.Builder {
    val builder =
      TypeAliasSpec.builder(typeAlias.simpleName, typeAlias.aliasedType.toKotlinPoetName())
    for (typeParameter in typeAlias.typeParameters) {
      builder.addTypeVariable(
        TypeVariableName(typeParameter.name, typeParameter.variance.toKotlinPoet())
      )
    }

    val docComment = typeAlias.docComment
    if (docComment != null && options.generateKdoc) {
      builder.addKdoc(renderAsKdoc(docComment))
    }

    return builder
  }

  private fun TypeParameter.Variance.toKotlinPoet(): KModifier? =
    when (this) {
      TypeParameter.Variance.COVARIANT -> KModifier.OUT
      TypeParameter.Variance.CONTRAVARIANT -> KModifier.IN
      else -> null
    }

  // do the minimum work necessary to avoid kotlin compile errors
  // generating idiomatic KDoc would require parsing doc comments, converting member links, etc.
  private fun renderAsKdoc(docComment: String): String = docComment

  private fun appendPropertyMethod() =
    FunSpec.builder("appendProperty")
      .addParameter("builder", STRING_BUILDER)
      .addParameter("name", STRING)
      .addParameter("value", ANY_NULL)
      .addStatement("builder.append(\"\\n  \").append(name).append(\" = \")")
      .addStatement("val lines = value.toString().split(\"\\n\")")
      .addStatement("builder.append(lines[0])")
      .beginControlFlow("for (i in 1..lines.lastIndex)")
      .addStatement("builder.append(\"\\n  \").append(lines[i])")
      .endControlFlow()

  private fun PClass.toKotlinPoetName(): ClassName {
    val index = moduleName.lastIndexOf(".")
    val packageName = if (index == -1) "" else moduleName.substring(0, index)
    val moduleTypeName = moduleName.substring(index + 1).replaceFirstChar { it.titlecaseChar() }
    val packagePrefix = kotlinPackage?.let { "$it." } ?: ""
    val renderedPackage =
      if (packagePrefix.isNotBlank()) "$packagePrefix.$packageName" else packageName

    return if (isModuleClass) {
      ClassName(renderedPackage, moduleTypeName)
    } else {
      ClassName(renderedPackage, moduleTypeName, simpleName)
    }
  }

  private fun TypeAlias.toKotlinPoetName(): ClassName {
    val index = moduleName.lastIndexOf(".")
    val packageName = if (index == -1) "" else moduleName.substring(0, index)

    return when {
      aliasedType is PType.Alias -> {
        // Kotlin type generated for [this] is a top-level type alias
        ClassName(packageName, simpleName)
      }
      CodeGeneratorUtils.isRepresentableAsEnum(aliasedType, null) -> {
        if (isStandardLibraryMember) {
          throw KotlinCodeGeneratorException(
            "Standard library typealias `${qualifiedName}` is not supported by Kotlin code generator." +
              " If you think this is an omission, please let us know."
          )
        }
        // Kotlin type generated for [this] is a nested enum class
        val moduleTypeName = moduleName.substring(index + 1).replaceFirstChar { it.titlecaseChar() }
        ClassName(packageName, moduleTypeName, simpleName)
      }
      else -> {
        // Kotlin type generated for [this] is a top-level type alias
        ClassName(packageName, simpleName)
      }
    }
  }

  private fun PType.toKotlinPoetName(): TypeName =
    when (this) {
      PType.UNKNOWN -> ANY_NULL
      PType.NOTHING -> NOTHING
      is PType.StringLiteral -> STRING
      is PType.Class -> {
        // if in doubt, spell it out
        when (val classInfo = pClass.info) {
          PClassInfo.Any -> ANY_NULL
          PClassInfo.Typed,
          PClassInfo.Dynamic -> ANY
          PClassInfo.Boolean -> BOOLEAN
          PClassInfo.String -> STRING
          // seems more useful to generate `Double` than `kotlin.Number`
          PClassInfo.Number -> DOUBLE
          PClassInfo.Int -> LONG
          PClassInfo.Float -> DOUBLE
          PClassInfo.Duration -> DURATION
          PClassInfo.DataSize -> DATA_SIZE
          PClassInfo.Pair ->
            KOTLIN_PAIR.parameterizedBy(
              if (typeArguments.isEmpty()) ANY_NULL else typeArguments[0].toKotlinPoetName(),
              if (typeArguments.isEmpty()) ANY_NULL else typeArguments[1].toKotlinPoetName()
            )
          PClassInfo.Collection ->
            COLLECTION.parameterizedBy(
              if (typeArguments.isEmpty()) ANY_NULL else typeArguments[0].toKotlinPoetName()
            )
          PClassInfo.List,
          PClassInfo.Listing ->
            LIST.parameterizedBy(
              if (typeArguments.isEmpty()) ANY_NULL else typeArguments[0].toKotlinPoetName()
            )
          PClassInfo.Set ->
            SET.parameterizedBy(
              if (typeArguments.isEmpty()) ANY_NULL else typeArguments[0].toKotlinPoetName()
            )
          PClassInfo.Map,
          PClassInfo.Mapping ->
            MAP.parameterizedBy(
              if (typeArguments.isEmpty()) ANY_NULL else typeArguments[0].toKotlinPoetName(),
              if (typeArguments.isEmpty()) ANY_NULL else typeArguments[1].toKotlinPoetName()
            )
          PClassInfo.Module -> PMODULE
          PClassInfo.Class -> PCLASS
          PClassInfo.Regex -> REGEX
          PClassInfo.Version -> VERSION
          else ->
            when {
              !classInfo.isStandardLibraryClass -> pClass.toKotlinPoetName()
              else ->
                throw KotlinCodeGeneratorException(
                  "Standard library class `${pClass.qualifiedName}` is not supported by Kotlin code generator. " +
                    "If you think this is an omission, please let us know."
                )
            }
        }
      }
      is PType.Nullable -> baseType.toKotlinPoetName().copy(nullable = true)
      is PType.Constrained -> baseType.toKotlinPoetName()
      is PType.Alias ->
        when (typeAlias.qualifiedName) {
          "pkl.base#NonNull" -> ANY
          // Not currently generating Kotlin unsigned types
          // because it's not clear if the benefits outweigh the drawbacks:
          // - breaking change
          // - Kotlin unsigned types aren't intended for domain modeling
          // - diverts from Java code generator
          // - doesn't increase safety
          //   - range already checked on Pkl side
          //   - conversion to signed type doesn't perform range check
          "pkl.base#Int8" -> BYTE
          "pkl.base#Int16",
          "pkl.base#UInt8" -> SHORT
          "pkl.base#Int32",
          "pkl.base#UInt16" -> INT
          "pkl.base#UInt",
          "pkl.base#UInt32" -> LONG
          "pkl.base#DurationUnit" -> DURATION_UNIT
          "pkl.base#DataSizeUnit" -> DATA_SIZE_UNIT
          "pkl.base#Uri" -> URI
          else -> {
            val className = typeAlias.toKotlinPoetName()
            when {
              typeAlias.typeParameters.isEmpty() -> className
              typeArguments.isEmpty() -> {
                // no type arguments provided for a type alias with type parameters -> fill in
                // `Any?` (equivalent of `unknown`)
                val typeArgs = Array(typeAlias.typeParameters.size) { ANY_NULL }
                className.parameterizedBy(*typeArgs)
              }
              else -> className.parameterizedBy(*typeArguments.toKotlinPoet())
            }
          }
        }
      is PType.Function ->
        throw KotlinCodeGeneratorException(
          "Pkl function types are not supported by the Kotlin code generator."
        )
      is PType.Union ->
        if (CodeGeneratorUtils.isRepresentableAsString(this)) STRING
        else
          throw KotlinCodeGeneratorException(
            "Pkl union types are not supported by the Kotlin code generator."
          )

      // occurs on RHS of generic type aliases
      is PType.TypeVariable -> TypeVariableName(typeParameter.name)
      else -> throw AssertionError("Encountered unexpected PType subclass: $this")
    }

  private fun List<PType>.toKotlinPoet(): Array<TypeName> =
    map { it.toKotlinPoetName() }.toTypedArray()
}
