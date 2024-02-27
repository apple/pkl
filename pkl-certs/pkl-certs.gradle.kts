plugins {
  pklAllProjects
  pklJavaLibrary
  pklPublishLibrary
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-certs")
        description.set("""
          Pkl's built-in CA certificates. 
          Used by pkl-cli and optionally supported by pkl-core.")
        """.trimIndent())
      }
    }
  }
}
