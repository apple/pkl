plugins {
  id("pklAllProjects")
  id("pklJvmLibrary")
  id("pklKotlinLibrary")
  id("pklPublishLibrary")
}

description = "Pkl runtime library for JVM"

dependencies {
  // CliJavaCodeGeneratorOptions exposes CliBaseOptions
  api(projects.pklCommonsCli)

  implementation(projects.pklCommons)
  implementation(projects.pklCore)
  implementation(libs.javaPoet)

  testImplementation(projects.pklConfigJava)
  testImplementation(projects.pklCommonsTest)
}

// with `org.gradle.parallel=true` and without the line below, `test` strangely runs into:
// java.lang.NoClassDefFoundError: Lorg/pkl/config/java/ConfigEvaluator;
// perhaps somehow related to InMemoryJavaCompiler?
tasks.test {
  mustRunAfter(":pkl-config-java:testFatJar")
}

tasks.jar {
  manifest {
    attributes += mapOf("Main-Class" to "org.pkl.codegen.java.Main")
  }
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url = "https://github.com/apple/pkl/tree/main/pkl-codegen-java"
        description = """
          Java source code generator that generates corresponding Java classes for Pkl classes,
          simplifying consumption of Pkl configuration as statically typed Java objects.
        """.trimIndent()
      }
    }
  }
}
