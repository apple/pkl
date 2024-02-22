plugins {
  id("pklAllProjects")
  id("pklJvmLibrary")
  id("pklPureKotlin")
  id("pklPublishLibrary")
}

description = "Pkl commons (internal)"

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url = "https://github.com/apple/pkl/tree/main/pkl-commons"
        description = "Internal utilities. NOT A PUBLIC API."
      }
    }
  }
}
