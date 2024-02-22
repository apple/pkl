plugins {
  id("pklAllProjects")
  id("pklJvmLibrary")
  id("pklPureKotlin")
}

description = "Pkl packaging server"

dependencies {
  implementation(projects.pklCore)
  implementation(libs.msgpack)
  implementation(libs.truffleApi)
  implementation(libs.antlrRuntime)

  testImplementation(projects.pklCommonsTest)
}

testing.suites {
  @Suppress("UnstableApiUsage") val unitTests by creating(JvmTestSuite::class) {
    useJUnitJupiter(libs.versions.junit)
    useKotlinTest(libs.versions.kotlin)
  }
}

val unitTests by tasks.getting(Test::class) {
  testClassesDirs = files(tasks.test.get().testClassesDirs)
  classpath = tasks.test.get().classpath
}

tasks.test {
  inputs.dir("src/test/files/SnippetTests/input")
  inputs.dir("src/test/files/SnippetTests/output")
  dependsOn(unitTests)

  useJUnitPlatform {
    includeEngines("BinaryEvaluatorSnippetTestEngine")
  }
}
