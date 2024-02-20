plugins {
  pklAllProjects
  pklKotlinLibrary
  pklPublishLibrary
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-codegen-kotlin")
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
  implementation(projects.pklCommons)
  api(projects.pklCommonsCli)
  api(projects.pklCore)
  
  implementation(libs.kotlinPoet)
  implementation(libs.kotlinReflect)

  testImplementation(projects.pklConfigKotlin)
  testImplementation(projects.pklCommonsTest)
  testImplementation(libs.kotlinCompilerEmbeddable)
  testRuntimeOnly(libs.kotlinScriptingCompilerEmbeddable)
  testRuntimeOnly(libs.kotlinScriptUtil)
}
