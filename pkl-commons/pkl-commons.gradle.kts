plugins {
  pklAllProjects
  pklKotlinLibrary
  pklPublishLibrary
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/pkl-lang/pkl/tree/dev/pkl-commons")
        description.set("Internal utilities. NOT A PUBLIC API.")
      }
    }
  }
}
