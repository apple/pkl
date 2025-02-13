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
plugins {
  pklAllProjects
  pklJavaLibrary
  id("me.champeau.jmh")
}

val truffle: Configuration by configurations.creating
val graal: Configuration by configurations.creating

dependencies {
  jmh(projects.pklCore)
  jmh(projects.pklCommonsTest)
  truffle(libs.truffleApi)
  graal(libs.graalCompiler)
}

jmh {
  // include = ["fib_class_java"]
  // include = ["fib_class_constrained1", "fib_class_constrained2"]
  jmhVersion.set(libs.versions.jmh)
  // jvmArgsAppend = "-Dgraal.TruffleCompilationExceptionsAreFatal=true " +
  //  "-Dgraal.Dump=Truffle,TruffleTree -Dgraal.TraceTruffleCompilation=true " +
  //  "-Dgraal.TruffleFunctionInlining=false"
  jvm.set("${buildInfo.graalVmAmd64.baseDir}/bin/java")
  // see:
  // https://docs.oracle.com/en/graalvm/enterprise/20/docs/graalvm-as-a-platform/implement-language/#disable-class-path-separation
  jvmArgs.set(
    listOf(
      // one JVM arg per list element doesn't work, but the following does
      "-Dgraalvm.locatorDisabled=true --module-path=${truffle.asPath} --upgrade-module-path=${graal.asPath}"
    )
  )
  includeTests.set(false)
  // threads = Runtime.runtime.availableProcessors() / 2 + 1
  // synchronizeIterations = false
}

tasks.named("jmh") { dependsOn(":installGraalVmAmd64") }

// Prevent this error which occurs when building in IntelliJ:
// "Entry org/pkl/core/fib_class_typed.pkl is a duplicate but no duplicate handling strategy has
// been set."
tasks.processJmhResources { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
