import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import java.nio.charset.StandardCharsets
import java.util.Base64

plugins {
  `maven-publish`
  signing
}

publishing {
  publications {
    components.findByName("java")?.let { javaComponent ->
      create<MavenPublication>("library") {
        from(javaComponent)
      }
    }
    withType<MavenPublication>().configureEach {
      pom {
        name.set(artifactId)
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

val validatePom by tasks.registering {
  if (tasks.findByName("generatePomFileForLibraryPublication") == null) {
    return@registering
  }
  val generatePomFileForLibraryPublication by tasks.existing(GenerateMavenPom::class)
  val outputFile = layout.buildDirectory.dir("validatePom")

  dependsOn(generatePomFileForLibraryPublication)
  inputs.file(generatePomFileForLibraryPublication.get().destination)
  outputs.file(outputFile)

  doLast {
    outputFile.get().asFile.delete()

    val pomFile = generatePomFileForLibraryPublication.get().destination
    assert(pomFile.exists())

    val text = pomFile.readText()

    run {
      val unresolvedVersion = Regex("<version>.*[+,()\\[\\]].*</version>")
      val matches = unresolvedVersion.findAll(text).toList()
      if (matches.isNotEmpty()) {
        throw GradleException(
          """
        Found unresolved version selector(s) in generated POM:
        ${matches.joinToString("\n") { it.groupValues[0] }}
      """.trimIndent()
        )
      }
    }

    val buildInfo = project.extensions.getByType<BuildInfo>()
    if (buildInfo.isReleaseBuild) {
      val snapshotVersion = Regex("<version>.*-SNAPSHOT</version>")
      val matches = snapshotVersion.findAll(text).toList()
      if (matches.isNotEmpty()) {
        throw GradleException(
          """
        Found snapshot version(s) in generated POM of Pkl release version:
        ${matches.joinToString("\n") { it.groupValues[0] }}
      """.trimIndent()
        )
      }
    }

    outputFile.get().asFile.writeText("OK")
  }
}

tasks.publish {
  dependsOn(validatePom)
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
  publishing.publications.findByName("library")?.let { sign(it) }
}

artifacts {
  project.tasks.findByName("javadocJar")?.let { archives(it) }
  project.tasks.findByName("sourcesJar")?.let { archives(it) }
}
