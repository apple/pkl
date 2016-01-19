plugins {
  pklAllProjects
  pklKotlinLibrary
  pklPublishLibrary
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/pkl-lang/pkl/tree/dev/pkl-kotlin-codegen")
        description.set("""
          Kotlin source code generator that generates corresponding Kotlin classes for Pkl classes,
          simplifying consumption of Pkl configuration as statically typed Kotlin objects.
        """.trimIndent())
      }
    }
  }
}

tasks.jar {
  manifest {
    attributes += mapOf("Main-Class" to "org.pkl.codegen.kotlin.Main")
  }
}

dependencies {
  implementation(project(":pkl-commons"))
  api(project(":pkl-commons-cli"))
  api(project(":pkl-core"))
  
  implementation(libs.kotlinPoet)
  implementation(libs.kotlinReflect)

  testImplementation(project(":pkl-config-kotlin"))
  testImplementation(project(":pkl-commons-test"))
  testImplementation(libs.kotlinCompilerEmbeddable)
  testRuntimeOnly(libs.kotlinScriptingCompilerEmbeddable)
  testRuntimeOnly(libs.kotlinScriptUtil)
}
