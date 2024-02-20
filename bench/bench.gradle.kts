plugins {
  id("pklAllProjects")
  id("pklJavaLibrary")
  id("pklGraalVm")
  id("me.champeau.jmh")
}

val truffle: Configuration by configurations.creating
val graal: Configuration by configurations.creating

description = "JMH benchmarks for Pkl Core"

dependencies {
  jmh(projects.pklCore)
  // necessary because antlr4-runtime is declared as implementation dependency in pkl-core.gradle
  jmh(libs.antlrRuntime)
  truffle(libs.truffleApi)
  graal(libs.graalCompiler)
}

jmh {
  //include = ["fib_class_java"]
  //include = ["fib_class_constrained1", "fib_class_constrained2"]
  jmhVersion = libs.versions.jmh
  // jvmArgsAppend = "-Dgraal.TruffleCompilationExceptionsAreFatal=true " +
  //  "-Dgraal.Dump=Truffle,TruffleTree -Dgraal.TraceTruffleCompilation=true " +
  //  "-Dgraal.TruffleFunctionInlining=false"
  jvm = "${buildInfo.graalVm.baseDir}/bin/java"
  // see: https://docs.oracle.com/en/graalvm/enterprise/20/docs/graalvm-as-a-platform/implement-language/#disable-class-path-separation
  jvmArgs = listOf(
    // one JVM arg per list element doesn't work, but the following does
    "-Dgraalvm.locatorDisabled=true --module-path=${truffle.asPath} --upgrade-module-path=${graal.asPath}"
  )
  includeTests = false
  //threads = Runtime.runtime.availableProcessors() / 2 + 1
  //synchronizeIterations = false
}

tasks.named("jmh") {
  dependsOn(":installGraalVm")
}

// Prevent this error which occurs when building in IntelliJ:
// "Entry org/pkl/core/fib_class_typed.pkl is a duplicate but no duplicate handling strategy has been set."
tasks.processJmhResources {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
