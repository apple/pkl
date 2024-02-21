import java.nio.charset.StandardCharsets
import java.util.*

plugins {
  id("pklAllProjects")
  id("pklFatJar")
  signing
}

description = "Pkl tooling and utilities"

val dummy: SourceSet by sourceSets.creating

val firstPartySourcesJars by configurations.existing

// Note: pkl-tools cannot (easily) contain pkl-config-kotlin 
// because pkl-tools ships with a shaded Kotlin stdlib.
dependencies {
  // Use scope `api` so that other subprojects 
  // can declare a normal project dependency on this project, 
  // which is desirable for IntelliJ integration.
  // The published fat JAR doesn't declare any dependencies.
  api(projects.pklCli)
  api(projects.pklCodegenJava)
  api(projects.pklCodegenKotlin)
  api(projects.pklConfigJava)
  api(projects.pklCore)
  api(projects.pklDoc)

  // used by `pklFatJar` plugin (ideally this would be inferred automatically)
  firstPartySourcesJars(project(":pkl-cli", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-codegen-java", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-codegen-kotlin", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-config-java", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-core", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-doc", "sourcesJar"))
}

// TODO: need to figure out how to properly generate javadoc here.
// For now, we'll include a dummy javadoc jar.
val javadocDummy by tasks.creating(Javadoc::class) {
  source = dummy.allJava
}

java {
  withJavadocJar()
}

val javadocJar by tasks.existing(Jar::class) {
  from(javadocDummy.outputs.files)
  archiveBaseName = "pkl-tools-all"
}

tasks.shadowJar {
  archiveBaseName = "pkl-tools-all"
}

publishing {
  publications {
    named<MavenPublication>("fatJar") {
      // don't use `-all` suffix because this is the only JAR we publish
      artifactId = "pkl-tools"
      // add dummy javadoc jar to publication
      artifact(javadocJar.flatMap { it.archiveFile }) {
        classifier = "javadoc"
      }
      pom {
        url = "https://github.com/apple/pkl/tree/main/pkl-tools"
        description = "Fat Jar containing pkl-cli, pkl-codegen-java, " +
          "pkl-codegen-kotlin, pkl-config-java, pkl-core, pkl-doc, " +
          "and their shaded third-party dependencies."
        name = artifactId
        // keep in sync with pklPublishLibrary
        licenses {
          license {
            name = "The Apache Software License, Version 2.0"
            url = "https://github.com/apple/pkl/blob/main/LICENSE.txt"
          }
        }
        developers {
          developer {
            id = "pkl-authors"
            name = "The Pkl Authors"
            email = "pkl-oss@group.apple.com"
          }
        }
        scm {
          connection = "scm:git:git://github.com/apple/pkl.git"
          developerConnection = "scm:git:ssh://github.com/apple/pkl.git"

          val buildInfo = project.extensions.getByType<BuildInfo>()
          url = "https://github.com/apple/pkl/tree/${buildInfo.commitish}"
        }
        issueManagement {
          system = "GitHub Issues"
          url = "https://github.com/apple/pkl/issues"
        }
        ciManagement {
          system = "Circle CI"
          url = "https://app.circleci.com/pipelines/github/apple/pkl"
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
