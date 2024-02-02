import java.nio.charset.StandardCharsets
import java.util.*

plugins {
  pklAllProjects
  pklFatJar
  signing
}

val firstPartySourcesJars by configurations.existing

java {
  // create an empty javadoc jar so Maven Central is happy
  withJavadocJar()
}

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
    named<MavenPublication>("fatJar") {
      // don't use `-all` suffix because this is the only JAR we publish
      artifactId = "pkl-tools"
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-tools")
        description.set("Fat Jar containing pkl-cli, pkl-codegen-java, " +
          "pkl-codegen-kotlin, pkl-config-java, pkl-core, pkl-doc, " +
          "and their shaded third-party dependencies.")
        name.set(artifactId)
        // keep in sync with pklPublishLibrary
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("https://github.com/apple/pkl/blob/main/LICENSE.txt")
          }
        }
        developers {
          developer {
            id.set("pkl-authors")
            name.set("The Pkl Authors")
            email.set("pkl-oss@group.apple.com")
          }
        }
        scm {
          connection.set("scm:git:git://github.com/apple/pkl.git")
          developerConnection.set("scm:git:ssh://github.com/apple/pkl.git")
          val buildInfo = project.extensions.getByType<BuildInfo>()
          url.set("https://github.com/apple/pkl/tree/${buildInfo.commitish}")
        }
        issueManagement {
          system.set("GitHub Issues")
          url.set("https://github.com/apple/pkl/issues")
        }
        ciManagement {
          system.set("Circle CI")
          url.set("https://app.circleci.com/pipelines/github/apple/pkl")
        }
      }
    }
  }
}

signing {
  // provided as env vars `ORG_GRADLE_PROJECT_signingKey` and `ORG_GRADLE_PROJECT_signingPassword`
  // in CI.
  val signingKey = (findProperty("signingKey") as String?)
    ?.let { Base64.getDecoder().decode(it).toString(StandardCharsets.US_ASCII) }
  val signingPassword = findProperty("signingPassword") as String?
  if (signingKey != null && signingPassword != null) {
    useInMemoryPgpKeys(signingKey, signingPassword)
  }
  sign(publishing.publications["fatJar"])
}
