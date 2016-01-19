plugins {
  pklAllProjects
  pklFatJar
  pklPublishLibrary
  pklJavaLibrary
}

val firstPartySourcesJars by configurations.existing

// Note: pkl-tools cannot (easily) contain pkl-config-kotlin 
// because pkl-tools ships with a shaded Kotlin stdlib.
dependencies {
  // Use scope `api` so that other subprojects 
  // can declare a normal project dependency on this project, 
  // which is desirable for IntelliJ integration.
  // The published fat JAR doesn't declare any dependencies.
  api(project(":pkl-cli"))
  api(project(":pkl-codegen-java"))
  api(project(":pkl-codegen-kotlin"))
  api(project(":pkl-config-java"))
  api(project(":pkl-core"))
  api(project(":pkl-doc"))
  
  // used by `pklFatJar` plugin (ideally this would be inferred automatically)
  firstPartySourcesJars(project(":pkl-cli", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-codegen-java", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-codegen-kotlin", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-config-java", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-core", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-doc", "sourcesJar"))
}

tasks.shadowJar {
  archiveBaseName.set("pkl-tools-all")
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-tools")
        description.set("""
          The suite of libraries and tools within the Pkl JVM ecosystem.
        """.trimIndent())
      }
    }
    named<MavenPublication>("fatJar") {
      // don't use `-all` suffix because this is the only JAR we publish
      artifactId = "pkl-tools"
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-tools")
        description.set("Fat Jar containing pkl-cli, pkl-codegen-java, " +
          "pkl-codegen-kotlin, pkl-config-java, pkl-core, pkl-doc, " +
          "and their shaded third-party dependencies.")
      }
    }
  }
}
