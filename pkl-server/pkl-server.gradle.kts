plugins {
  pklAllProjects
  pklJavaLibrary
  pklKotlinLibrary
}

dependencies {
  implementation(project(":pkl-core"))
  implementation(libs.msgpack)
  implementation(libs.truffleApi)
  implementation(libs.antlrRuntime)

  testImplementation(project(":pkl-commons-test"))
}

tasks.test {
  inputs.dir("src/test/files/SnippetTests/input")
  inputs.dir("src/test/files/SnippetTests/output")
}
