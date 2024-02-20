plugins {
  id("pklAllProjects")
  id("pklJvmLibrary")
  id("pklKotlinLibrary")
  id("pklPublishLibrary")
}

description = "Pkl code generator for Kotlin"

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url = "https://github.com/apple/pkl/tree/main/pkl-codegen-kotlin"
        description = """
          Kotlin source code generator that generates corresponding Kotlin classes for Pkl classes,
          simplifying consumption of Pkl configuration as statically typed Kotlin objects.
        """.trimIndent()
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
  implementation(libs.kotlinxSerializationCore) {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
  }

  testImplementation(projects.pklConfigKotlin)
  testImplementation(projects.pklCommonsTest)
  testImplementation(libs.kotlinCompilerEmbeddable)
  testRuntimeOnly(libs.kotlinScriptingCompilerEmbeddable)
  testRuntimeOnly(libs.kotlinScriptingJsr223)
}
