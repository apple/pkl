plugins {
  id("pklAllProjects")
  id("pklJavaLibrary")
  id("pklPublishLibrary")
}

description = "Pkl execution engine"

val pklDistribution: Configuration by configurations.creating

// Because pkl-executor doesn't depend on other Pkl modules
// (nor has overlapping dependencies that could cause a version conflict),
// clients are free to use different versions of pkl-executor and (say) pkl-config-java-all.
// (Pkl distributions used by EmbeddedExecutor are isolated via class loaders.)
dependencies {
  pklDistribution(project(":pkl-config-java", "fatJar"))

  implementation(libs.slf4jApi)

  testImplementation(projects.pklCommonsTest)
  testImplementation(projects.pklCore)
  testImplementation(libs.slf4jSimple)
}

// TODO why is this needed? Without this, we get error:
// `Entry org/pkl/executor/EmbeddedExecutor.java is a duplicate but no duplicate handling strategy has been set.`
// However, we do not have multiple of these Java files.
tasks.named<Jar>("sourcesJar") {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url = "https://github.com/apple/pkl/tree/main/pkl-executor"
        description = """
          Library for executing Pkl code in a sandboxed environment.
        """.trimIndent()
      }
    }
  }
}

sourceSets {
  main {
    java {
      srcDir("src/main/java")
    }
  }
}

tasks.test {
  // used by EmbeddedExecutorTest
  dependsOn(pklDistribution)
}
