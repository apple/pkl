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

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.io.StringWriter
import java.net.URI
import java.util.*
import org.pkl.commons.NameMapper
import org.pkl.core.*
import org.pkl.core.util.CodeGeneratorUtils
import org.pkl.core.util.IoUtils

@Deprecated("renamed to KotlinCodeGeneratorOptions", ReplaceWith("KotlinCodeGeneratorOptions"))
typealias KotlinCodegenOptions = KotlinCodeGeneratorOptions

data class KotlinCodeGeneratorOptions(
  /** The characters to use for indenting generated Kotlin code. */
  val indent: String = "  ",

  /** Whether to generate KDoc based on doc comments for Pkl modules, classes, and properties. */
  val generateKdoc: Boolean = false,

  /** Whether to generate config classes for use with Spring Boot. */
  val generateSpringBootConfig: Boolean = false,

  /** Whether to make generated classes implement [java.io.Serializable] */
  val implementSerializable: Boolean = false,

  /**
   * A mapping from Pkl module name prefixes to their replacements.
   *
   * Can be used when the class or package name in the generated source code should be different
   * from the corresponding name derived from the Pkl module declaration .
   */
  val renames: Map<String, String> = emptyMap(),
)

class KotlinCodeGeneratorException(message: String) : RuntimeException(message)

/** Entrypoint for the Kotlin code generator API. */
class KotlinCodeGenerator(
  /** The schema for the module to generate */
  private val moduleSchema: ModuleSchema,

  /** The options to use for the code generator */
  private val options: KotlinCodeGeneratorOptions,
) {
  companion object {
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
      "resources/META-INF/org/pkl/config/java/mapper/classes/${IoUtils.encodePath(moduleSchema.moduleName)}.properties"

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

  private val kotlinFileName: String
    get() = buildString {
      val (packageName, className) = nameMapper.map(moduleSchema.moduleName)
      val dirPath = packageName.split('.').joinToString("/", transform = IoUtils::encodePath)
      val fileName = IoUtils.encodePath(className)
      append("kot")
      append("lin/")
      if (dirPath.isNotEmpty()) {
        append("$dirPath/")
      }
      append("$fileName.kt")
    }

  val kotlinFile: String
    get() {
      if (moduleSchema.moduleUri.scheme == "pkl") {
        throw KotlinCodeGeneratorException(
          "Cannot generate Kotlin code for a Pkl standard library module (`${moduleSchema.moduleUri}`)."
        )
      }

      val pModuleClass = moduleSchema.moduleClass

      val hasModuleProperties = pModuleClass.properties.any { !it.value.isHidden }
      val isGenerateModuleClass =
        hasModuleProperties || pModuleClass.isOpen || pModuleClass.isAbstract

      fun generateCompanionRelatedCode(
        builder: TypeSpec.Builder,
        isModuleType: Boolean = false
      ): TypeSpec.Builder {
        // ensure that at most one companion object is generated for this type
        val companionObjectBuilder: Lazy<TypeSpec.Builder> = lazy {
          TypeSpec.companionObjectBuilder()
        }

        // generate serialization code
        if (
          options.implementSerializable &&
            (!isModuleType || isGenerateModuleClass) &&
            !builder.modifiers.contains(KModifier.ABSTRACT)
        ) {
          builder.addSuperinterface(java.io.Serializable::class.java)
          companionObjectBuilder.value.addProperty(
            PropertySpec.builder(
                "serialVersionUID",
                Long::class.java,
                KModifier.PRIVATE,
                KModifier.CONST
              )
              .initializer("0L")
              .build()
          )
        }

        if (companionObjectBuilder.isInitialized()) {
          builder.addType(companionObjectBuilder.value.build())
        }

        return builder
      }

      val moduleType =
        if (isGenerateModuleClass) {
          generateTypeSpec(pModuleClass, moduleSchema)
        } else {
          generateObjectSpec(pModuleClass)
        }

      for (pClass in moduleSchema.classes.values) {
        moduleType.addType(
          generateCompanionRelatedCode(generateTypeSpec(pClass, moduleSchema)).build()
        )
      }

      val moduleName = moduleSchema.moduleName

      val (packageName, moduleTypeName) = nameMapper.map(moduleName)

      val fileSpec = FileSpec.builder(packageName, moduleTypeName).indent(options.indent)

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

      fileSpec.addType(generateCompanionRelatedCode(moduleType, isModuleType = true).build())
      return fileSpec.build().toString()
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
      for ((index, name) in allProperties.keys.withIndex()) {
        codeBuilder.add(if (index == 0) "%N" else ", %N", name)
      }
      codeBuilder.add(")\n")

      return methodBuilder.addCode(codeBuilder.build()).build()
    }

    fun inheritsCopyMethodWithSameArity(): Boolean {
      val nearestNonAbstractAncestor =
        generateSequence(pClass.superclass) { it.superclass }.firstOrNull { !it.isAbstract }
          ?: return false
      return nearestNonAbstractAncestor.allProperties.values.count { !it.isHidden } ==
        allProperties.size
    }

    // besides generating copy method for current class,
    // override copy methods inherited from parent classes
    fun generateCopyMethods(typeBuilder: TypeSpec.Builder) {
      // copy methods don't make sense for abstract classes
      if (pClass.isAbstract) return

      var prevParameterCount = Int.MAX_VALUE
      for (currClass in generateSequence(pClass) { it.superclass }) {
        if (currClass.isAbstract) continue

        val currParameters = currClass.allProperties.filter { !it.value.isHidden }

        // avoid generating multiple methods with same no. of parameters
        if (currParameters.size < prevParameterCount) {
          val isOverride = currClass !== pClass || inheritsCopyMethodWithSameArity()
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

      for ((propertyName, property) in allProperties) {
        val accessor = if (property.isRegex()) "this.%N.pattern" else "this.%N"
        // use Objects.hashCode() because Kotlin's Any?.hashCode()
        // doesn't work for platform types (will get NPE if null)
        builder.addStatement(
          "result = 31 * result + %T.hashCode($accessor)",
          Objects::class,
          propertyName
        )
      }

      builder.addStatement("return result")
      return builder.build()
    }

    // produce same output as default toString() method of data classes
    fun generateToStringMethod(): FunSpec {
      return FunSpec.builder("toString")
        .addModifiers(KModifier.OVERRIDE)
        .returns(STRING)
        .addStatement(
          "return %P",
          CodeBlock.builder()
            .apply {
              add("%L", pClass.toKotlinPoetName().simpleName)
              add("(")
              for ((index, propertyName) in allProperties.keys.withIndex()) {
                add(if (index == 0) "%L" else ", %L", propertyName)
                add("=$")
                add("%N", propertyName)
              }
              add(")")
            }
            .build()
        )
        .build()
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

      if (!pClass.isAbstract) {
        generateCopyMethods(builder)
        builder
          .addFunction(generateEqualsMethod())
          .addFunction(generateHashCodeMethod())
          .addFunction(generateToStringMethod())
      }

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

      var hasRegex = false
      for ((name, property) in properties) {
        hasRegex = hasRegex || property.isRegex()
        builder.addProperty(generateProperty(name, property))
      }

      // kotlin.text.Regex (and java.util.regex.Pattern) defines equality as identity.
      // To match Pkl semantics and compare regexes by their String pattern,
      // override equals and hashCode if the data class has a property of type Regex.
      if (hasRegex) {
        builder.addFunction(generateEqualsMethod()).addFunction(generateHashCodeMethod())
      }

      return builder
    }

    return if (superclass == null && !pClass.isAbstract && !pClass.isOpen) generateDataClass()
    else generateRegularClass()
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

  private fun PClass.toKotlinPoetName(): ClassName {
    val (packageName, moduleTypeName) = nameMapper.map(moduleName)
    return if (isModuleClass) {
      ClassName(packageName, moduleTypeName)
    } else {
      ClassName(packageName, moduleTypeName, simpleName)
    }
  }

  private fun TypeAlias.toKotlinPoetName(): ClassName {
    val (packageName, moduleTypeName) = nameMapper.map(moduleName)

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

  private val nameMapper = NameMapper(options.renames)
}
