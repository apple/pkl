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
package org.pkl.codegen.java

import com.palantir.javapoet.*
import java.io.StringWriter
import java.lang.Deprecated
import java.net.URI
import java.util.*
import java.util.regex.Pattern
import javax.lang.model.element.Modifier
import kotlin.AssertionError
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.RuntimeException
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.apply
import kotlin.let
import kotlin.takeIf
import kotlin.to
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
  val renames: Map<String, String> = emptyMap()
)

/** Entrypoint for the Java code generator API. */
class JavaCodeGenerator(
  private val schema: ModuleSchema,
  private val codegenOptions: JavaCodeGeneratorOptions
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
    private val URI = ClassName.get(URI::class.java)
    private val VERSION = ClassName.get(Version::class.java)

    private const val PROPERTY_PREFIX: String = "org.pkl.config.java.mapper."

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
  }

  val output: Map<String, String>
    get() {
      return mapOf(javaFileName to javaFile, propertyFileName to propertiesFile)
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

  private val nonNullAnnotation: AnnotationSpec
    get() {
      val annotation = codegenOptions.nonNullAnnotation
      val className =
        if (annotation == null) {
          ClassName.get("org.pkl.config.java.mapper", "NonNull")
        } else {
          toClassName(annotation)
        }
      return AnnotationSpec.builder(className).build()
    }

  private val javaFileName: String
    get() {
      val (packageName, className) = nameMapper.map(schema.moduleName)
      val dirPath = packageName.replace('.', '/')
      return if (dirPath.isEmpty()) {
        "java/$className.java"
      } else {
        "java/$dirPath/$className.java"
      }
    }

  val javaFile: String
    get() {
      if (schema.moduleUri.scheme == "pkl") {
        throw JavaCodeGeneratorException(
          "Cannot generate Java code for a Pkl standard library module (`${schema.moduleUri}`)."
        )
      }

      val pModuleClass = schema.moduleClass
      val moduleClass = generateTypeSpec(pModuleClass, schema)

      for (pClass in schema.classes.values) {
        moduleClass.addType(generateTypeSpec(pClass, schema).build())
      }

      for (typeAlias in schema.typeAliases.values) {
        val stringLiterals = mutableSetOf<String>()
        if (CodeGeneratorUtils.isRepresentableAsEnum(typeAlias.aliasedType, stringLiterals)) {
          moduleClass.addType(generateEnumTypeSpec(typeAlias, stringLiterals).build())
        }
      }
      // generate static append method for module classes w/o parent class; reuse in subclasses and
      // nested classes
      if (pModuleClass.superclass!!.info == PClassInfo.Module) {
        val modifier =
          if (pModuleClass.isOpen || pModuleClass.isAbstract) Modifier.PROTECTED
          else Modifier.PRIVATE
        moduleClass.addMethod(appendPropertyMethod().addModifiers(modifier).build())
      }

      val (packageName, _) = nameMapper.map(schema.moduleName)

      return JavaFile.builder(packageName, moduleClass.build())
        .indent(codegenOptions.indent)
        .build()
        .toString()
    }

  private fun generateTypeSpec(pClass: PClass, schema: ModuleSchema): TypeSpec.Builder {
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

    fun addCtorParameter(
      builder: MethodSpec.Builder,
      propJavaName: String,
      property: PClass.Property
    ) {
      val paramBuilder = ParameterSpec.builder(property.type.toJavaPoetName(), propJavaName)
      if (paramsAnnotationName != null) {
        paramBuilder.addAnnotation(
          AnnotationSpec.builder(paramsAnnotationName)
            .addMember("value", "\$S", property.simpleName)
            .build()
        )
      }
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
        // $W inserts space or newline (automatic line wrapping)
        val callArgsStr = superProperties.keys.joinToString(",\$W")
        // use kotlin interpolation rather than javapoet $L interpolation
        // as otherwise the $W won't get processed
        builder.addStatement("super($callArgsStr)")
      }

      for ((name, property) in properties) {
        addCtorParameter(builder, name, property)
        builder.addStatement("this.\$N = \$N", name, name)
      }

      return builder.build()
    }

    fun generateEqualsMethod(): MethodSpec {
      val builder =
        MethodSpec.methodBuilder("equals")
          .addModifiers(Modifier.PUBLIC)
          .addAnnotation(Override::class.java)
          .addParameter(Object::class.java, "obj")
          .returns(Boolean::class.java)
          .addStatement("if (this == obj) return true")
          .addStatement("if (obj == null) return false")
          // generating this.getClass() instead of class literal avoids a SpotBugs warning
          .addStatement("if (this.getClass() != obj.getClass()) return false")
          .addStatement("\$T other = (\$T) obj", javaPoetClassName, javaPoetClassName)

      for ((propertyName, property) in allProperties) {
        val accessor = if (property.isRegex()) "\$N.pattern()" else "\$N"
        builder.addStatement(
          "if (!\$T.equals(this.$accessor, other.$accessor)) return false",
          Objects::class.java,
          propertyName,
          propertyName
        )
      }

      builder.addStatement("return true")
      return builder.build()
    }

    fun generateHashCodeMethod(): MethodSpec {
      val builder =
        MethodSpec.methodBuilder("hashCode")
          .addModifiers(Modifier.PUBLIC)
          .addAnnotation(Override::class.java)
          .returns(Int::class.java)
          .addStatement("int result = 1")

      for ((propertyName, property) in allProperties) {
        val accessor = if (property.isRegex()) "this.\$N.pattern()" else "this.\$N"
        builder.addStatement(
          "result = 31 * result + \$T.hashCode($accessor)",
          Objects::class.java,
          propertyName
        )
      }

      builder.addStatement("return result")
      return builder.build()
    }

    fun generateToStringMethod(): MethodSpec {
      val builder =
        MethodSpec.methodBuilder("toString")
          .addModifiers(Modifier.PUBLIC)
          .addAnnotation(Override::class.java)
          .returns(String::class.java)

      var builderSize = 50
      val appendBuilder = CodeBlock.builder()
      for (propertyName in allProperties.keys) {
        builderSize += 50
        appendBuilder.addStatement(
          "appendProperty(builder, \$S, this.\$N)",
          propertyName,
          propertyName
        )
      }

      builder
        .addStatement(
          "\$T builder = new \$T(\$L)",
          StringBuilder::class.java,
          StringBuilder::class.java,
          builderSize
        )
        .addStatement("builder.append(\$T.class.getSimpleName()).append(\" {\")", javaPoetClassName)
        .addCode(appendBuilder.build())
        // not using $S here because it generates `"\n" + "{"`
        // with a line break in the generated code after `+`
        .addStatement("builder.append(\"\\n}\")")
        .addStatement("return builder.toString()")

      return builder.build()
    }

    // do the minimum work necessary to avoid (most) java compile errors
    // generating idiomatic Javadoc would require parsing doc comments, converting member links,
    // etc.
    fun renderAsJavadoc(docComment: String): String {
      val escaped = docComment.replace("*/", "*&#47;")
      return if (escaped[escaped.length - 1] != '\n') escaped + '\n' else escaped
    }

    fun generateDeprecation(
      annotations: Collection<PObject>,
      hasJavadoc: Boolean,
      addAnnotation: (Class<*>) -> Unit,
      addJavadoc: (String) -> Unit
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

    fun generateField(propertyName: String, property: PClass.Property): FieldSpec {
      val builder = FieldSpec.builder(property.type.toJavaPoetName(), propertyName)

      val docComment = property.docComment
      val hasJavadoc =
        docComment != null && codegenOptions.generateJavadoc && !codegenOptions.generateGetters
      if (hasJavadoc) {
        builder.addJavadoc(renderAsJavadoc(docComment!!))
      }

      if (codegenOptions.generateGetters) {
        builder.addModifiers(
          if (pClass.isAbstract || pClass.isOpen) Modifier.PROTECTED else Modifier.PRIVATE
        )
      } else {
        generateDeprecation(
          property.annotations,
          hasJavadoc,
          { builder.addAnnotation(it) },
          { builder.addJavadoc(it) }
        )
        builder.addModifiers(Modifier.PUBLIC)
      }
      builder.addModifiers(Modifier.FINAL)

      return builder.build()
    }

    @Suppress("DuplicatedCode")
    fun generateGetter(
      propertyName: String,
      property: PClass.Property,
      isOverridden: Boolean
    ): MethodSpec {
      val propertyType = property.type
      val isBooleanProperty =
        propertyType is PType.Class && propertyType.pClass.info == PClassInfo.Boolean
      val methodName =
        (if (isBooleanProperty) "is" else "get") +
          // can use original name here (property.name rather than propertyName)
          // because getter name cannot possibly conflict with reserved words
          property.simpleName.replaceFirstChar { it.titlecaseChar() }

      val builder =
        MethodSpec.methodBuilder(methodName)
          .addModifiers(Modifier.PUBLIC)
          .returns(propertyType.toJavaPoetName())
          .addStatement("return \$N", propertyName)
      if (isOverridden) {
        builder.addAnnotation(Override::class.java)
      }

      val docComment = property.docComment
      val hasJavadoc = docComment != null && codegenOptions.generateJavadoc
      if (hasJavadoc) {
        builder.addJavadoc(renderAsJavadoc(docComment!!))
      }

      generateDeprecation(
        property.annotations,
        hasJavadoc,
        { builder.addAnnotation(it) },
        { builder.addJavadoc(it) }
      )

      return builder.build()
    }

    fun generateWithMethod(propertyName: String, property: PClass.Property): MethodSpec {
      val methodName = "with" + property.simpleName.replaceFirstChar { it.titlecaseChar() }

      val methodBuilder =
        MethodSpec.methodBuilder(methodName)
          .addModifiers(Modifier.PUBLIC)
          .addParameter(property.type.toJavaPoetName(), propertyName)
          .returns(javaPoetClassName)

      generateDeprecation(
        property.annotations,
        false,
        { methodBuilder.addAnnotation(it) },
        { methodBuilder.addJavadoc(it) }
      )

      val codeBuilder = CodeBlock.builder()
      codeBuilder.add("return new \$T(", javaPoetClassName)
      var firstProperty = true
      for (name in superProperties.keys) {
        if (name in properties) continue
        if (firstProperty) {
          firstProperty = false
          codeBuilder.add("\$N", name)
        } else {
          codeBuilder.add(", \$N", name)
        }
      }
      for (name in properties.keys) {
        if (firstProperty) {
          firstProperty = false
          codeBuilder.add("\$N", name)
        } else {
          codeBuilder.add(", \$N", name)
        }
      }
      codeBuilder.add(");\n")

      methodBuilder.addCode(codeBuilder.build())
      return methodBuilder.build()
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
                  "ConfigurationProperties"
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

    @Suppress("DuplicatedCode")
    fun generateClass(): TypeSpec.Builder {
      val builder =
        TypeSpec.classBuilder(javaPoetClassName.simpleName()).addModifiers(Modifier.PUBLIC)

      // stateless final module classes are non-instantiable by choice
      val isInstantiable =
        !(pClass.isAbstract || (isModuleClass && !pClass.isOpen && allProperties.isEmpty()))

      if (codegenOptions.implementSerializable && isInstantiable) {
        builder.addSuperinterface(java.io.Serializable::class.java)
        builder.addField(generateSerialVersionUIDField())
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
        { builder.addJavadoc(it) }
      )

      if (!isModuleClass) {
        builder.addModifiers(Modifier.STATIC)
      }

      if (pClass.isAbstract) {
        builder.addModifiers(Modifier.ABSTRACT)
      } else if (!pClass.isOpen) {
        builder.addModifiers(Modifier.FINAL)
      }

      if (codegenOptions.generateSpringBootConfig) {
        generateSpringBootAnnotations(builder)
      }

      builder.addMethod(generateConstructor(isInstantiable))

      superclass?.let { builder.superclass(it.toJavaPoetName()) }

      // generate fields, plus getter methods and either setters or `with` methods in alternating
      // order
      // `with` methods also need to be generated for superclass properties so that return type is
      // self type
      for ((name, property) in allProperties) {
        if (name in properties) {
          builder.addField(generateField(name, property))
          if (codegenOptions.generateGetters) {
            val isOverridden = name in superProperties
            builder.addMethod(generateGetter(name, property, isOverridden))
          }
        }
        if (isInstantiable) {
          builder.addMethod(generateWithMethod(name, property))
        }
      }

      if (isInstantiable) {
        builder
          .addMethod(generateEqualsMethod())
          .addMethod(generateHashCodeMethod())
          .addMethod(generateToStringMethod())
      }

      return builder
    }

    return generateClass()
  }

  private fun generateSerialVersionUIDField(): FieldSpec {
    return FieldSpec.builder(Long::class.java, "serialVersionUID", Modifier.PRIVATE)
      .addModifiers(Modifier.STATIC, Modifier.FINAL)
      .initializer("0L")
      .build()
  }

  private fun generateEnumTypeSpec(
    typeAlias: TypeAlias,
    stringLiterals: Set<String>
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
        TypeSpec.anonymousClassBuilder("\$S", pklName).build()
      )
    }

    return builder
  }

  private val paramsAnnotationName: ClassName? =
    codegenOptions.paramsAnnotation?.let { toClassName(it) }

  private fun appendPropertyMethod() =
    MethodSpec.methodBuilder("appendProperty")
      .addModifiers(Modifier.STATIC)
      .addParameter(StringBuilder::class.java, "builder")
      .addParameter(String::class.java, "name")
      .addParameter(Object::class.java, "value")
      .addStatement("builder.append(\"\\n  \").append(name).append(\" = \")")
      .addStatement(
        "\$T lines = \$T.toString(value).split(\"\\n\")",
        ArrayTypeName.of(String::class.java),
        Objects::class.java
      )
      .addStatement("builder.append(lines[0])")
      .beginControlFlow("for (int i = 1; i < lines.length; i++)")
      .addStatement("builder.append(\"\\n  \").append(lines[i])")
      .endControlFlow()

  private fun PClass.toJavaPoetName(): ClassName {
    val (packageName, moduleClassName) = nameMapper.map(moduleName)
    return if (isModuleClass) {
      ClassName.get(packageName, moduleClassName)
    } else {
      ClassName.get(packageName, moduleClassName, simpleName)
    }
  }

  // generated type is a nested enum class
  private fun TypeAlias.toJavaPoetName(): ClassName {
    val (packageName, moduleClassName) = nameMapper.map(moduleName)
    return ClassName.get(packageName, moduleClassName, simpleName)
  }

  /** Generate `List<? extends Foo>` if `Foo` is `abstract` or `open`, to allow subclassing. */
  private fun PType.toJavaPoetTypeArgumentName(): TypeName {
    val baseName = toJavaPoetName(nullable = false, boxed = true)
    return if (this is PType.Class && (pClass.isAbstract || pClass.isOpen)) {
      WildcardTypeName.subtypeOf(baseName)
    } else {
      baseName
    }
  }

  private fun PType.toJavaPoetName(nullable: Boolean = false, boxed: Boolean = false): TypeName =
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
                  typeArguments[0].toJavaPoetTypeArgumentName()
                },
                if (typeArguments.isEmpty()) {
                  OBJECT
                } else {
                  typeArguments[1].toJavaPoetTypeArgumentName()
                }
              )
              .nullableIf(nullable)
          PClassInfo.Collection ->
            ParameterizedTypeName.get(
                COLLECTION,
                if (typeArguments.isEmpty()) {
                  OBJECT
                } else {
                  typeArguments[0].toJavaPoetTypeArgumentName()
                }
              )
              .nullableIf(nullable)
          PClassInfo.List,
          PClassInfo.Listing -> {
            ParameterizedTypeName.get(
                LIST,
                if (typeArguments.isEmpty()) {
                  OBJECT
                } else {
                  typeArguments[0].toJavaPoetTypeArgumentName()
                }
              )
              .nullableIf(nullable)
          }
          PClassInfo.Set ->
            ParameterizedTypeName.get(
                SET,
                if (typeArguments.isEmpty()) {
                  OBJECT
                } else {
                  typeArguments[0].toJavaPoetTypeArgumentName()
                }
              )
              .nullableIf(nullable)
          PClassInfo.Map,
          PClassInfo.Mapping ->
            ParameterizedTypeName.get(
                MAP,
                if (typeArguments.isEmpty()) {
                  OBJECT
                } else {
                  typeArguments[0].toJavaPoetTypeArgumentName()
                },
                if (typeArguments.isEmpty()) {
                  OBJECT
                } else {
                  typeArguments[1].toJavaPoetTypeArgumentName()
                }
              )
              .nullableIf(nullable)
          PClassInfo.Module -> PMODULE.nullableIf(nullable)
          PClassInfo.Class -> PCLASS.nullableIf(nullable)
          PClassInfo.Regex -> PATTERN.nullableIf(nullable)
          PClassInfo.Version -> VERSION.nullableIf(nullable)
          else ->
            when {
              !classInfo.isStandardLibraryClass -> pClass.toJavaPoetName().nullableIf(nullable)
              else ->
                throw JavaCodeGeneratorException(
                  "Standard library class `${pClass.qualifiedName}` is not supported by Java code generator. " +
                    "If you think this is an omission, please let us know."
                )
            }
        }
      }
      is PType.Nullable -> baseType.toJavaPoetName(nullable = true, boxed = true)
      is PType.Constrained -> baseType.toJavaPoetName(nullable = nullable, boxed = boxed)
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
    else if (isPrimitive || isNullable) this else annotated(nonNullAnnotation)

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
    "while"
  )
