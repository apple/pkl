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

val nativeTest by tasks.registering(Test::class) {
  dependsOn(":pkl-cli:assembleNative")
  testClassesDirs = files(tasks.test.get().testClassesDirs)
  classpath = tasks.test.get().classpath
  include("**/NativeServerTest.*")
}

val testNative by tasks.existing
testNative {
  dependsOn(nativeTest)
}

