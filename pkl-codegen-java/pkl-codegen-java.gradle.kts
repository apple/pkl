plugins {
  pklAllProjects
  pklKotlinLibrary
  pklPublishLibrary
}

dependencies {
  // CliJavaCodeGeneratorOptions exposes CliBaseOptions
  api(project(":pkl-commons-cli"))

  implementation(project(":pkl-commons"))
  implementation(project(":pkl-core"))
  implementation(libs.javaPoet)

  testImplementation(project(":pkl-config-java"))
  testImplementation(project(":pkl-commons-test"))
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
        url.set("https://github.com/apple/pkl/tree/main/pkl-codegen-java")
        description.set("""
          Java source code generator that generates corresponding Java classes for Pkl classes,
          simplifying consumption of Pkl configuration as statically typed Java objects.
        """.trimIndent())
      }
    }
  }
}
