plugins {
  pklAllProjects
  pklKotlinLibrary
}

dependencies {
  implementation(projects.pklCore)
  implementation(libs.antlrRuntime)
  implementation(libs.lsp4j)
}

tasks.test {
}
