plugins {
  id("pklAllProjects")
  id("pklJvmLibrary")
  id("pklPureKotlin")
  id("pklPublishLibrary")
}

description = "Pkl runtime library for Kotlin"

val pklConfigJava: Configuration by configurations.creating

val pklConfigJavaAll: Configuration by configurations.creating

val pklCodegenKotlin: Configuration by configurations.creating

// Ideally, api would extend pklConfigJavaAll,
// instead of extending pklConfigJava and then patching test task and POM.
// However, this wouldn't work for IntelliJ.
configurations.api.get().extendsFrom(pklConfigJava)

dependencies {
  pklConfigJava(projects.pklConfigJava)

  pklConfigJavaAll(project(":pkl-config-java", "fatJar"))

  pklCodegenKotlin(projects.pklCodegenKotlin)

  implementation(libs.kotlinReflect)
  
  testImplementation(libs.geantyref)
}

val generateTestConfigClasses by tasks.registering(JavaExec::class) {
  outputs.dir("build/testConfigClasses")
  inputs.dir("src/test/resources/codegenPkl")

  classpath = pklCodegenKotlin
  mainClass = "org.pkl.codegen.kotlin.Main"
  args("--output-dir", "build/testConfigClasses")
  args(fileTree("src/test/resources/codegenPkl"))
}

sourceSets.getByName("test") {
  java.srcDir("build/testConfigClasses/kotlin")
  resources.srcDir("build/testConfigClasses/resources")
}

tasks.processTestResources {
  dependsOn(generateTestConfigClasses)
}

tasks.compileTestKotlin {
  dependsOn(generateTestConfigClasses)
}

// use pkl-config-java-all for testing (same as for publishing)
tasks.test {
  classpath = classpath - pklConfigJava + pklConfigJavaAll
}

// disable publishing of .module until we find a way to manipulate it like POM (or ideally both together)
tasks.withType<GenerateModuleMetadata> {
  enabled = false
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url = "https://github.com/apple/pkl/tree/main/pkl-config-kotlin"
        description = "Kotlin extensions for pkl-config-java, a Java config library based on the Pkl config language."

        // change dependency pkl-config-java to pkl-config-java-all
        withXml {
          val projectElement = asElement()
          val dependenciesElement = projectElement.getElementsByTagName("dependencies").item(0) as org.w3c.dom.Element
          val dependencyElements = dependenciesElement.getElementsByTagName("dependency")
          for (idx in 0 until dependencyElements.length) {
            val dependencyElement = dependencyElements.item(idx) as org.w3c.dom.Element
            val artifactIdElement = dependencyElement.getElementsByTagName("artifactId").item(0) as org.w3c.dom.Element
            if (artifactIdElement.textContent == "pkl-config-java") {
              artifactIdElement.textContent = "pkl-config-java-all"
              return@withXml
            }
          }
          throw GradleException("Failed to edit POM of module `pkl-config-kotlin`.")
        }
      }
    }
  }
}
