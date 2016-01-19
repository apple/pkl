import org.gradle.api.publish.maven.tasks.GenerateMavenPom

plugins {
  `maven-publish`
}

val sourcesJar by tasks.existing
val javadocJar by tasks.existing

publishing {
  publications {
    create<MavenPublication>("library") {
      from(components["java"])
    }
  }
}

val validatePom by tasks.registering {
  val generatePomFileForLibraryPublication by tasks.existing(GenerateMavenPom::class)
  val outputFile = file("$buildDir/validatePom") // dummy output to satisfy up-to-date check

  dependsOn(generatePomFileForLibraryPublication)
  inputs.file(generatePomFileForLibraryPublication.get().destination)
  outputs.file(outputFile)

  doLast {
    outputFile.delete()

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

    outputFile.writeText("OK")
  }
}

tasks.publish {
  dependsOn(validatePom)
}
