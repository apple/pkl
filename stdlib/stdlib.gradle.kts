@file:Suppress("UnstableApiUsage")

plugins {
  pklAllProjects
  base
  `maven-publish`
  id("com.diffplug.spotless")
}

val pkldocConfiguration: Configuration = configurations.create("pkldoc")

dependencies {
  pkldocConfiguration(project(":pkl-doc"))
}

// create and publish a self-contained stdlib archive
// purpose is to provide non-jvm tools/projects with a versioned stdlib
val stdlibZip by tasks.registering(Zip::class) {
  destinationDirectory.set(file("$buildDir/libs"))
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
    }
  }
}

spotless {
  format("pkl") {
    target("*.pkl")
    licenseHeaderFile(rootProject.file("buildSrc/src/main/resources/license-header.line-comment.txt"), "/// ")
  }
}

val pkldoc by tasks.registering(JavaExec::class) {
  val stdlibFiles = project.fileTree(projectDir).matching {
    include("*.pkl")
    exclude("doc-package-info.pkl")
  }
  val infoFiles = project.files("doc-package-info.pkl")

  inputs.files(stdlibFiles)
  inputs.files(infoFiles)
  outputs.dir("$buildDir/pkldoc")

  classpath = pkldocConfiguration
  main = "org.pkl.doc.Main"
  args("--output-dir", "$buildDir/pkldoc")
  args(stdlibFiles.map { "pkl:${it.name}".dropLast(4) })
  args(infoFiles)

  doLast {
    println("Generated Standard Library API Docs at: file://$buildDir/pkldoc/index.html")
  }
}
