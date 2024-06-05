plugins {
  pklAllProjects
  pklJavaLibrary
  pklKotlinLibrary
  pklNativeBuild
}

dependencies {
  implementation(projects.pklCore)
  implementation(libs.msgpack)
  implementation(libs.truffleApi)
  implementation(libs.antlrRuntime)

  testImplementation(projects.pklCommonsTest)
}

tasks.test {
  inputs.dir("src/test/files/SnippetTests/input")
    .withPropertyName("snippetTestsInput")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  inputs.dir("src/test/files/SnippetTests/output")
    .withPropertyName("snippetTestsOutput")
    .withPathSensitivity(PathSensitivity.RELATIVE)
  exclude("**/NativeServerTest.*")
}

private fun Test.configureNativeTest() {
  testClassesDirs = files(tasks.test.get().testClassesDirs)
  classpath = tasks.test.get().classpath
  include("**/NativeServerTest.*")
}

val testMacExecutableAarch64 by tasks.registering(Test::class) {
  dependsOn(":pkl-cli:macExecutableAarch64")
  configureNativeTest()
}

val testMacExecutableAmd64 by tasks.registering(Test::class) {
  dependsOn(":pkl-cli:macExecutableAmd64")
  configureNativeTest()
}

val testLinuxExecutableAmd64 by tasks.registering(Test::class) {
  dependsOn(":pkl-cli:linuxExecutableAmd64")
  configureNativeTest()
}

val testLinuxExecutableAarch64 by tasks.registering(Test::class) {
  dependsOn(":pkl-cli:linuxExecutableAarch64")
  configureNativeTest()
}

val testAlpineExecutableAmd64 by tasks.registering(Test::class) {
  dependsOn(":pkl-cli:alpineExecutableAmd64")
  configureNativeTest()
}

val testWindowsExecutableAmd64 by tasks.registering(Test::class) {
  dependsOn(":pkl-cli:windowsExecutableAmd64")
  configureNativeTest()
}

val testNative by tasks.existing
testNative {
  when {
    buildInfo.os.isMacOsX -> {
      dependsOn(testMacExecutableAmd64)
      if (buildInfo.arch == "aarch64") {
        dependsOn(testMacExecutableAarch64)
      }
    }
    buildInfo.os.isWindows -> {
      dependsOn(testWindowsExecutableAmd64)
    }
    buildInfo.os.isLinux && buildInfo.arch == "aarch64" -> {
      dependsOn(testLinuxExecutableAarch64)
    }
    buildInfo.os.isLinux && buildInfo.arch == "amd64" -> {
      dependsOn(testLinuxExecutableAmd64)
      if (buildInfo.hasMuslToolchain) {
        dependsOn(testAlpineExecutableAmd64)
      }
    }
  }
}
