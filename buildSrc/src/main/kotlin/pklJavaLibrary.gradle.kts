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
@file:Suppress("HttpUrlsUsage")

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  `java-library`
  id("pklKotlinTest")
  id("com.diffplug.spotless")
}

// make sources Jar available to other subprojects
val sourcesJarConfiguration = configurations.register("sourcesJar")

// Version Catalog library symbols.
val libs = the<LibrariesForLibs>()

java {
  withSourcesJar() // creates `sourcesJar` task
  withJavadocJar()
}

artifacts {
  // make sources Jar available to other subprojects
  add("sourcesJar", tasks["sourcesJar"])
}

spotless {
  java {
    googleJavaFormat(libs.versions.googleJavaFormat.get())
    targetExclude("**/generated/**", "**/build/**")
    licenseHeaderFile(rootProject.file("buildSrc/src/main/resources/license-header.star-block.txt"))
  }
  kotlin {
    ktfmt(libs.versions.ktfmt.get()).googleStyle()
    targetExclude("**/generated/**", "**/build/**")
    licenseHeaderFile(rootProject.file("buildSrc/src/main/resources/license-header.star-block.txt"))
  }
}

tasks.compileKotlin { enabled = false }

tasks.jar {
  manifest {
    attributes += mapOf("Automatic-Module-Name" to "org.${project.name.replace("-", ".")}")
  }
}

tasks.javadoc {
  classpath = sourceSets.main.get().output + sourceSets.main.get().compileClasspath
  source = sourceSets.main.get().allJava
  title = "${project.name} ${project.version} API"
  (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

val workAroundKotlinGradlePluginBug by
  tasks.registering {
    doLast {
      // Works around this problem, which sporadically appears and disappears in different
      // subprojects:
      // A problem was found with the configuration of task ':pkl-executor:compileJava' (type
      // 'JavaCompile').
      // > Directory '[...]/pkl/pkl-executor/build/classes/kotlin/main'
      // specified for property 'compileKotlinOutputClasses' does not exist.
      layout.buildDirectory.dir("classes/kotlin/main").get().asFile.mkdirs()
    }
  }

tasks.compileJava {
  dependsOn(workAroundKotlinGradlePluginBug)
  // TODO: determine correct limits for Truffle specializations
  // (see https://graalvm.slack.com/archives/CNQSB2DHD/p1712380902746829)
  options.compilerArgs.add("-Atruffle.dsl.SuppressWarnings=truffle-limit")
}
