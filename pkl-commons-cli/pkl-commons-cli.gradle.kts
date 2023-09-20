plugins {
  pklAllProjects
  pklKotlinLibrary
  pklPublishLibrary
}

dependencies {
  api(project(":pkl-core"))
  api(libs.clikt) {
    // force clikt to use our version of the kotlin stdlib
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
  }

  implementation(project(":pkl-commons"))
  testImplementation(project(":pkl-commons-test"))
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/pkl-lang/pkl/tree/main/pkl-commons-cli")
        description.set("Internal CLI utilities. NOT A PUBLIC API.")
      }
    }
  }
}
