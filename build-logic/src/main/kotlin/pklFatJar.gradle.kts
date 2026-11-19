/*
 * Copyright © 2024-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*

plugins {
  `java-library`
  `maven-publish`
  id("com.gradleup.shadow")
  id("pklJvmSourceBundle")
}

// make fat Jar available to other subprojects
val fatJarConfiguration: Configuration = configurations.create("fatJar")

val fatJarPublication: MavenPublication = publishing.publications.create<MavenPublication>("fatJar")

val relocations =
  mapOf(
    // pkl-core dependencies
    "org.organicdesign.fp." to "org.pkl.thirdparty.paguro.",
    "org.snakeyaml.engine." to "org.pkl.thirdparty.snakeyaml.engine.",
    "org.msgpack." to "org.pkl.thirdparty.msgpack.",
    "org.w3c.dom." to "org.pkl.thirdparty.w3c.dom.",
    "com.oracle.svm.core." to "org.pkl.thirdparty.svm.",

    // pkl-cli dependencies
    "org.jline." to "org.pkl.thirdparty.jline.",
    "com.github.ajalt.clikt." to "org.pkl.thirdparty.clikt.",
    "com.github.ajalt.colormath." to "org.pkl.thirdparty.colormath.",
    "com.github.ajalt.mordant." to "org.pkl.thirdparty.mordant.",
    "com.sun.jna." to "org.pkl.thirdparty.jna.",
    "kotlin." to "org.pkl.thirdparty.kotlin.",
    "kotlinx." to "org.pkl.thirdparty.kotlinx.",
    "org.intellij." to "org.pkl.thirdparty.intellij.",
    "org.fusesource.jansi." to "org.pkl.thirdparty.jansi.",
    "org.fusesource.hawtjni." to "org.pkl.thirdparty.hawtjni.",

    // pkl-doc dependencies
    "org.commonmark." to "org.pkl.thirdparty.commonmark.",
    "org.jetbrains." to "org.pkl.thirdparty.jetbrains.",
    "_COROUTINE." to "org.pkl.thirdparty.kotlinx._COROUTINE.",

    // pkl-config-java dependencies
    "io.leangen.geantyref." to "org.pkl.thirdparty.geantyref.",

    // pkl-codegen-java dependencies
    "com.palantir.javapoet." to "org.pkl.thirdparty.javapoet.",

    // pkl-codegen-kotlin dependencies
    "com.squareup.kotlinpoet." to "org.pkl.thirdparty.kotlinpoet.",

    // pkl-profiler dependencies
    "com.google.protobuf." to "org.pkl.thirdparty.com.google.protobuf.",
    "com.google.perftools." to "org.pkl.thirdparty.com.google.perftools.",
    "google.protobuf." to "org.pkl.thirdparty.google.protobuf.",
  )

for ((key, value) in relocations) {
  if (!key.endsWith(".")) {
    throw GradleException(
      "Invalid relocation `\"$key\" to \"$value\"`: `$key` should end with a dot"
    )
  }
  if (!value.endsWith(".")) {
    throw GradleException(
      "Invalid relocation `\"$key\" to \"$value\"`: `$value` should end with a dot"
    )
  }
}

val nonRelocations = listOf("com/oracle/truffle/", "org/graalvm/", "org/jspecify/")

// kotlin-reflect cannot be relocated: it resolves its implementation classes
// (e.g. `ReflectionFactoryImpl`) and `kotlin.Metadata` annotations by name, and
// declares services under its own name in META-INF/services.
// `DefaultConstructorMarker` is loaded by name from `KDeclarationContainerImpl`.
// https://github.com/apple/pkl/issues/794
val kotlinUnrelocatedNames =
  listOf("kotlin.reflect.", "kotlin.jvm.internal.DefaultConstructorMarker")

// entry-path forms of `kotlinUnrelocatedNames`, e.g. `kotlin/reflect/`
val kotlinUnrelocatedPaths = kotlinUnrelocatedNames.map { it.replace('.', '/') }

fun relocatedName(name: String): String =
  relocations.entries.first { name.startsWith(it.key) }.let { it.value + name.removePrefix(it.key) }

tasks.shadowJar {
  inputs.property("relocations", relocations)

  archiveClassifier.set(null as String?)

  configurations = listOf(project.configurations.runtimeClasspath.get())

  addMultiReleaseAttribute = true

  // not required at runtime / fat JARs can't be used in native-image builds anyway
  exclude("org/pkl/cli/svm/**")

  exclude("META-INF/maven/**")
  exclude("META-INF/upgrade/**")

  exclude("DebugProbesKt.bin")

  val info = project.extensions.getByType<BuildInfo>()
  val minimumJvmTarget = JavaVersion.toVersion(info.jvmTarget)

  manifest.attributes(
    // Certain exports need to be added to the Java modulepath for Java 17 to work properly with
    // shaded JARs. See the following link for an explanation of this syntax:
    // https://bugs.openjdk.org/browse/JDK-8335225
    "Add-Exports" to info.jpmsExportsForJarManifest
  )

  // effectively, this results in calls excluding:
  // `META-INF/versions/{18-25}/**`
  // at the time of this writing; multi-release JARs beyond JDK 21 break the current
  // version of the Shadow plugin, and aren't needed for Truffle's use by Pkl.
  JavaVersionRange.startingAt(JavaLanguageVersion.of(minimumJvmTarget.majorVersion.toInt() + 1))
    .forEach { exclude("META-INF/versions/${it.asInt()}/**") }

  exclude("module-info.*")

  for ((from, to) in relocations) {
    relocate(from, to) {
      if (from == "kotlin.") {
        for (name in kotlinUnrelocatedNames) {
          exclude(if (name.endsWith(".")) "$name**" else name)
        }
        // kotlin-reflect loads `.kotlin_builtins` resources by literal
        // classpath path, so they must not be relocated.
        exclude("kotlin/**/*.kotlin_builtins")
        // kotlin-reflect stores platform `kotlin.*` names as string literals
        // (e.g. the name tables in `JavaToKotlinClassMap`) and looks them up
        // by their original values, so they must not be rewritten. This also
        // preserves qualified names, serializer names, and `kotlin.Metadata`
        // annotation descriptors used outside kotlin-reflect.
        skipStringConstants = true
      }
    }
  }

  // necessary for service files to be adapted to relocation
  mergeServiceFiles()
}

shadow { addShadowVariantIntoJavaComponent = false }

val testFatJar =
  tasks.register<Test>("testFatJar") {
    testClassesDirs = files(tasks.test.get().testClassesDirs)
    classpath =
      // compiled test classes
      sourceSets.test.get().output +
        // fat Jar
        tasks.shadowJar.get().outputs.files +
        // test-only dependencies
        // (test dependencies that are also main dependencies must already be contained in fat Jar;
        // to verify that, we don't want to include them here)
        (configurations.testRuntimeClasspath.get() - configurations.runtimeClasspath.get())
  }

tasks.check { dependsOn(testFatJar) }

val validateFatJar =
  tasks.register("validateFatJar") {
    val outputFile = layout.buildDirectory.file("validateFatJar/result.txt")
    inputs.files(tasks.shadowJar)
    inputs.property("nonRelocations", nonRelocations)
    outputs.file(outputFile)

    doLast {
      val unshadowedFiles = mutableListOf<String>()
      zipTree(tasks.shadowJar.get().outputs.files.singleFile).visit {
        val fileDetails = this
        val path = fileDetails.relativePath.pathString
        if (
          !(fileDetails.isDirectory ||
            path.startsWith("org/pkl/") ||
            path.startsWith("META-INF/") ||
            // kotlin-reflect entries and `.kotlin_builtins` resource files are
            // deliberately not relocated; see the `exclude` calls in
            // `tasks.shadowJar`.
            (path.startsWith("kotlin/") && path.endsWith(".kotlin_builtins")) ||
            kotlinUnrelocatedPaths.any { path.startsWith(it) } ||
            nonRelocations.any { path.startsWith(it) })
        ) {
          // don't throw exception inside `visit`
          // as this gives a misleading "Could not expand ZIP" error message
          unshadowedFiles.add(path)
        }
      }
      if (unshadowedFiles.isEmpty()) {
        outputFile.get().asFile.writeText("SUCCESS")
      } else {
        outputFile.get().asFile.writeText("FAILURE")
        throw GradleException("Found unshadowed files:\n" + unshadowedFiles.joinToString("\n"))
      }
    }
  }

tasks.check { dependsOn(validateFatJar) }

val fatSourcesJar =
  tasks.register<MergeSourcesJars>("fatSourcesJar") {
    plugins.withId("pklJavaLibrary") { inputJars.from(tasks.named("sourcesJar")) }
    inputJars.from(configurations.named("firstPartySourcesJars"))
    inputJars.from(
      tasks.named<ResolveSourcesJars>("resolveSourcesJars").map { fileTree(it.outputDir) }
    )

    mergedBinaryJars.from(tasks.shadowJar)
    relocatedPackages.set(relocations)
    excludedPackages.set(kotlinUnrelocatedNames)
    outputJar.fileProvider(
      provider {
        file(tasks.shadowJar.get().archiveFile.get().asFile.path.replace(".jar", "-sources.jar"))
      }
    )
  }

val validateFatSourcesJar =
  tasks.register("validateFatSourcesJar") {
    val outputFile = layout.buildDirectory.file("validateFatSourcesJar/result.txt")
    inputs.files(tasks.shadowJar, fatSourcesJar.flatMap { it.outputJar })
    inputs.property("relocations", relocations)
    inputs.property("kotlinUnrelocatedNames", kotlinUnrelocatedNames)
    outputs.file(outputFile)

    doLast {
      val relocatedPaths = kotlinUnrelocatedNames.map { relocatedName(it).replace('.', '/') }

      val binaryPaths = mutableListOf<String>()
      zipTree(tasks.shadowJar.get().outputs.files.singleFile).visit {
        if (!isDirectory) binaryPaths.add(relativePath.pathString)
      }

      val sourcePaths = mutableListOf<String>()
      val problems = mutableListOf<String>()
      zipTree(fatSourcesJar.get().outputJar.get().asFile).visit {
        if (!isDirectory) {
          val path = relativePath.pathString
          sourcePaths.add(path)
          for (relocatedPath in relocatedPaths) {
            if (path.startsWith(relocatedPath)) {
              problems.add("source for unrelocated name was relocated: $path")
            }
          }
          if (path.endsWith(".kt") || path.endsWith(".java")) {
            val text = open().bufferedReader().use { it.readText() }
            for (name in kotlinUnrelocatedNames) {
              if (text.contains(relocatedName(name))) {
                problems.add("unrelocated name $name was rewritten in $path")
              }
            }
          }
        }
      }

      for (unrelocatedPath in kotlinUnrelocatedPaths.filter { it.endsWith("/") }) {
        if (
          binaryPaths.any { it.startsWith(unrelocatedPath) } &&
            sourcePaths.none { it.startsWith(unrelocatedPath) }
        ) {
          problems.add("sources missing for unrelocated package: $unrelocatedPath")
        }
      }

      if (problems.isEmpty()) {
        outputFile.get().asFile.writeText("SUCCESS")
      } else {
        outputFile.get().asFile.writeText("FAILURE")
        throw GradleException("Found relocated or missing sources:\n" + problems.joinToString("\n"))
      }
    }
  }

tasks.check { dependsOn(validateFatSourcesJar) }

artifacts { add("fatJar", tasks.shadowJar) }

publishing {
  publications {
    named<MavenPublication>("fatJar") {
      from(components["shadow"])

      // sources Jar is fat
      artifact(fatSourcesJar.flatMap { it.outputJar.asFile }) { classifier = "sources" }

      plugins.withId("pklJavaLibrary") {
        val javadocJar = tasks.named<Jar>("javadocJar")
        // Javadoc Jar is not fat (didn't invest effort)
        artifact(javadocJar.flatMap { it.archiveFile }) { classifier = "javadoc" }
      }
    }
  }
}
