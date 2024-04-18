plugins {
  pklAllProjects
  pklJavaLibrary
  pklKotlinLibrary
}

dependencies {
  implementation(projects.pklCore)
  implementation(libs.msgpack)
  implementation(libs.truffleApi)
  implementation(libs.antlrRuntime)

  testImplementation(projects.pklCommonsTest)
}

tasks.test {
  inputs.dir("src/test/files/SnippetTests/input").withPropertyName("snippetTestsInput").withPathSensitivity(PathSensitivity.RELATIVE)
  inputs.dir("src/test/files/SnippetTests/output").withPropertyName("snippetTestsOutput").withPathSensitivity(PathSensitivity.RELATIVE)
}
