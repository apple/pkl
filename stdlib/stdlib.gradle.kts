plugins {
  pklAllProjects
  base
  `maven-publish`
  id("com.diffplug.spotless")
  pklPublishLibrary
  signing
}

// create and publish a self-contained stdlib archive
// purpose is to provide non-jvm tools/projects with a versioned stdlib
val stdlibZip by tasks.registering(Zip::class) {
  destinationDirectory.set(file("build/libs"))
  archiveBaseName.set("pkl-stdlib")
  archiveVersion.set(project.version as String)
  into("org/pkl/stdlib") {
    from(projectDir)
    include("*.pkl")
  }
}
tasks.assemble {
  dependsOn(stdlibZip)
}

publishing {
  publications {
    create<MavenPublication>("stdlib") {
      artifactId = "pkl-stdlib"
      artifact(stdlibZip.flatMap { it.archiveFile })
      pom {
        description.set("Standard library for the Pkl programming language")
        url.set("https://github.com/apple/pkl/tree/main/stdlib")
      }
    }
  }
}

signing {
  sign(publishing.publications["stdlib"])
}

spotless {
  format("pkl") {
    target("*.pkl")
    licenseHeaderFile(rootProject.file("buildSrc/src/main/resources/license-header.line-comment.txt"), "/// ")
  }
}
