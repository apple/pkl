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
  inputs.dir("src/test/files/SnippetTests/input")
  inputs.dir("src/test/files/SnippetTests/output")
}
