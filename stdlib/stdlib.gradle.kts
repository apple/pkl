plugins {
  id("pklAllProjects")
  base
  `maven-publish`
  id("com.diffplug.spotless")
  id("pklPublishLibrary")
  signing
}

description = "Pkl language standard library"

// create and publish a self-contained stdlib archive
// purpose is to provide non-jvm tools/projects with a versioned stdlib
val stdlibZip by tasks.registering(Zip::class) {
  destinationDirectory = layout.buildDirectory.dir("libs")
  archiveBaseName = "pkl-stdlib"
  archiveVersion = project.version as String
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
        description = "Standard library for the Pkl programming language"
        url = "https://github.com/apple/pkl/tree/main/stdlib"
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
    licenseHeaderFile(rootProject.file("build-logic/src/main/resources/license-header.line-comment.txt"), "/// ")
  }
}
