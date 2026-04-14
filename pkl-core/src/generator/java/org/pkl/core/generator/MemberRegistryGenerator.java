/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.generator;

import com.oracle.truffle.api.dsl.GeneratedBy;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/**
 * Generates a subclass of `org.pkl.core.stdlib.registry.ExternalMemberRegistry` for each stdlib
 * module and a factory to instantiate them. Generated classes are written to
 * `generated/truffle/org/pkl/core/stdlib/registry`.
 *
 * <p>Inputs:
 *
 * <ul>
 *   <li>Generated Truffle node classes for stdlib members. These classes are located in subpackages
 *       of `org.pkl.core.stdlib` and identified via their `@GeneratedBy` annotations.
 *   <li>`@PklName` annotations on handwritten node classes from which Truffle node classes are
 *       generated.
 * </ul>
 */
public final class MemberRegistryGenerator extends AbstractProcessor {
  private static final String TRUFFLE_NODE_CLASS_SUFFIX = "NodeGen";
  private static final String TRUFFLE_NODE_FACTORY_SUFFIX = "NodesFactory";
  private static final String STDLIB_PACKAGE_NAME = "org.pkl.core.stdlib";
  private static final String REGISTRY_PACKAGE_NAME = STDLIB_PACKAGE_NAME + ".registry";
  private static final String MODULE_PACKAGE_NAME = "org.pkl.core.module";

  private static final ClassName EXTERNAL_MEMBER_REGISTRY_CLASS_NAME =
      ClassName.get(REGISTRY_PACKAGE_NAME, "ExternalMemberRegistry");
  private static final ClassName EMPTY_MEMBER_REGISTRY_CLASS_NAME =
      ClassName.get(REGISTRY_PACKAGE_NAME, "EmptyMemberRegistry");
  private static final ClassName MEMBER_REGISTRY_FACTORY_CLASS_NAME =
      ClassName.get(REGISTRY_PACKAGE_NAME, "MemberRegistryFactory");
  private static final ClassName MODULE_KEY_CLASS_NAME =
      ClassName.get(MODULE_PACKAGE_NAME, "ModuleKey");
  private static final ClassName MODULE_KEYS_CLASS_NAME =
      ClassName.get(MODULE_PACKAGE_NAME, "ModuleKeys");

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Set.of(GeneratedBy.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.RELEASE_17;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (annotations.isEmpty()) {
      return true;
    }

    var nodeClassesByPackage = collectNodeClasses(roundEnv);
    generateRegistryClasses(nodeClassesByPackage);
    generateRegistryFactoryClass(nodeClassesByPackage.keySet());
    return true;
  }

  private Map<PackageElement, List<TypeElement>> collectNodeClasses(RoundEnvironment roundEnv) {
    var nodeClasses = new ArrayList<TypeElement>();

    for (var element : roundEnv.getElementsAnnotatedWith(GeneratedBy.class)) {
      if (!(element instanceof TypeElement typeElement)) {
        continue;
      }
      if (!typeElement.getQualifiedName().toString().startsWith(STDLIB_PACKAGE_NAME)) {
        continue;
      }
      if (!typeElement.getSimpleName().toString().endsWith(TRUFFLE_NODE_CLASS_SUFFIX)) {
        continue;
      }
      nodeClasses.add(typeElement);
    }

    nodeClasses.sort(
        Comparator.comparing(
                (TypeElement element) -> {
                  var enclosingElement = element.getEnclosingElement();
                  return enclosingElement.getKind() == ElementKind.PACKAGE
                      ? ""
                      : enclosingElement.getSimpleName().toString();
                })
            .thenComparing(element -> element.getSimpleName().toString()));

    var result = new LinkedHashMap<PackageElement, List<TypeElement>>();
    for (var nodeClass : nodeClasses) {
      var pkg = processingEnv.getElementUtils().getPackageOf(nodeClass);
      result.computeIfAbsent(pkg, ignored -> new ArrayList<>()).add(nodeClass);
    }
    return result;
  }

  private void generateRegistryClasses(
      Map<PackageElement, List<TypeElement>> nodeClassesByPackage) {
    for (var entry : nodeClassesByPackage.entrySet()) {
      generateRegistryClass(entry.getKey(), entry.getValue());
    }
  }

  private void generateRegistryClass(PackageElement pkg, List<TypeElement> nodeClasses) {
    var pklModuleName = getAnnotatedPklName(pkg);
    if (pklModuleName == null) {
      pklModuleName = pkg.getSimpleName().toString();
    }

    var pklModuleNameCapitalized = capitalize(pklModuleName);
    var registryClassName =
        ClassName.get(REGISTRY_PACKAGE_NAME, pklModuleNameCapitalized + "MemberRegistry");

    TypeSpec.Builder registryClass =
        TypeSpec.classBuilder(registryClassName)
            .addJavadoc("Generated by {@link $L}.\n", getClass().getName())
            .addModifiers(Modifier.FINAL)
            .superclass(EXTERNAL_MEMBER_REGISTRY_CLASS_NAME);

    var constructor = MethodSpec.constructorBuilder();

    for (var nodeClass : nodeClasses) {
      var enclosingClass = nodeClass.getEnclosingElement();

      var pklClassName = getAnnotatedPklName(enclosingClass);
      if (pklClassName == null) {
        pklClassName =
            stripSuffix(enclosingClass.getSimpleName().toString(), TRUFFLE_NODE_FACTORY_SUFFIX);
      }

      var pklMemberName = getAnnotatedPklName(nodeClass);
      if (pklMemberName == null) {
        pklMemberName =
            stripSuffix(nodeClass.getSimpleName().toString(), TRUFFLE_NODE_CLASS_SUFFIX);
      }

      String pklMemberNameQualified;
      if (pklClassName.equals(pklModuleNameCapitalized)) {
        pklMemberNameQualified = "pkl." + pklModuleName + "#" + pklMemberName;
      } else {
        pklMemberNameQualified = "pkl." + pklModuleName + "#" + pklClassName + "." + pklMemberName;
      }

      registryClass.addOriginatingElement(nodeClass);
      constructor.addStatement("register($S, $T::create)", pklMemberNameQualified, nodeClass);
    }

    registryClass.addMethod(constructor.build());
    writeJavaFile(REGISTRY_PACKAGE_NAME, registryClass.build());
  }

  private void generateRegistryFactoryClass(Collection<PackageElement> packages) {
    var registryFactoryClass =
        TypeSpec.classBuilder(MEMBER_REGISTRY_FACTORY_CLASS_NAME)
            .addJavadoc("Generated by {@link $L}.\n", getClass().getName())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

    registryFactoryClass.addMethod(
        MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build());

    MethodSpec.Builder getMethod =
        MethodSpec.methodBuilder("get")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameter(MODULE_KEY_CLASS_NAME, "moduleKey")
            .returns(EXTERNAL_MEMBER_REGISTRY_CLASS_NAME)
            .beginControlFlow("if (!$T.isStdLibModule(moduleKey))", MODULE_KEYS_CLASS_NAME)
            .addStatement("return $T.INSTANCE", EMPTY_MEMBER_REGISTRY_CLASS_NAME)
            .endControlFlow()
            .beginControlFlow("switch (moduleKey.getUri().getSchemeSpecificPart())");

    for (var pkg : packages) {
      var pklModuleName = getAnnotatedPklName(pkg);
      if (pklModuleName == null) {
        pklModuleName = pkg.getSimpleName().toString();
      }

      var pklModuleNameCapitalized = capitalize(pklModuleName);
      var registryClassName =
          ClassName.get(REGISTRY_PACKAGE_NAME, pklModuleNameCapitalized + "MemberRegistry");

      registryFactoryClass.addOriginatingElement(pkg);
      getMethod.addCode("case $S:\n", pklModuleName);
      getMethod.addStatement("  return new $T()", registryClassName);
    }

    getMethod
        .addCode("default:\n")
        .addStatement("  return $T.INSTANCE", EMPTY_MEMBER_REGISTRY_CLASS_NAME)
        .endControlFlow();

    registryFactoryClass.addMethod(getMethod.build());
    writeJavaFile(REGISTRY_PACKAGE_NAME, registryFactoryClass.build());
  }

  private String getAnnotatedPklName(Element element) {
    for (var annotation : element.getAnnotationMirrors()) {
      var annotationName = annotation.getAnnotationType().asElement().getSimpleName().toString();

      if (annotationName.equals("PklName")) {
        return firstAnnotationValue(annotation).getValue().toString();
      }

      if (annotationName.equals("GeneratedBy")) {
        var value = firstAnnotationValue(annotation).getValue();
        if (value instanceof TypeMirror typeMirror) {
          var generatedByElement = processingEnv.getTypeUtils().asElement(typeMirror);
          if (generatedByElement != null) {
            return getAnnotatedPklName(generatedByElement);
          }
        }
      }
    }
    return null;
  }

  private static AnnotationValue firstAnnotationValue(AnnotationMirror annotation) {
    return annotation.getElementValues().values().iterator().next();
  }

  private void writeJavaFile(String packageName, TypeSpec typeSpec) {
    try {
      JavaFile.builder(packageName, typeSpec).build().writeTo(processingEnv.getFiler());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String stripSuffix(String value, String suffix) {
    return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
  }

  private static String capitalize(String value) {
    if (value.isEmpty()) {
      return value;
    }
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }
}
