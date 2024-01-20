plugins {
  pklAllProjects
  pklJavaLibrary
  pklFatJar
  pklPublishLibrary
}

val pklCodegenJava: Configuration by configurations.creating
val firstPartySourcesJars by configurations.existing

val generateTestConfigClasses by tasks.registering(JavaExec::class) {
  outputs.dir("build/testConfigClasses")
  inputs.dir("src/test/resources/codegenPkl")

  classpath = pklCodegenJava
  mainClass.set("org.pkl.codegen.java.Main")
  args("--output-dir", "build/testConfigClasses")
  args("--generate-javadoc")
  args(fileTree("src/test/resources/codegenPkl"))
}

tasks.processTestResources {
  dependsOn(generateTestConfigClasses)
}

tasks.compileTestKotlin {
  dependsOn(generateTestConfigClasses)
}

val bundleTests by tasks.registering(Jar::class) {
  from(sourceSets.test.get().output)
}

// Runs unit tests using jar'd class files as a source.
// This is to test loading the ClassRegistry from within a jar, as opposed to directly from the file system.
val testFromJar by tasks.registering(Test::class) {
  dependsOn(bundleTests)

  testClassesDirs = files(tasks.test.get().testClassesDirs)

  classpath =
    // compiled test classes
    bundleTests.get().outputs.files +
      // fat Jar
      tasks.shadowJar.get().outputs.files +
      // test-only dependencies
      // (test dependencies that are also main dependencies must already be contained in fat Jar;
      // to verify that, we don't want to include them here)
      (configurations.testRuntimeClasspath.get() - configurations.runtimeClasspath.get())
}

// TODO: the below snippet causes `./gradlew check` to fail specifically on `pkl-codegen-java:check`. Why?
//tasks.test {
//  dependsOn(testFromJar)
//}

sourceSets.getByName("test") {
  java.srcDir("build/testConfigClasses/java")
  resources.srcDir("build/testConfigClasses/resources")
}

dependencies {
  // "api" because ConfigEvaluator extends Evaluator
  api(project(":pkl-core"))

  implementation(libs.geantyref)

  testImplementation(libs.javaxInject)

  firstPartySourcesJars(project(":pkl-core", "sourcesJar"))

  pklCodegenJava(project(":pkl-codegen-java"))
}

tasks.shadowJar {
  archiveBaseName.set("pkl-config-java-all")
}

publishing {
  publications {
    named<MavenPublication>("library") {
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-config-java")
        description.set("Java config library based on the Pkl config language.")
      }
    }

    named<MavenPublication>("fatJar") {
      artifactId = "pkl-config-java-all"
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-config-java")
        description.set("Shaded fat Jar for pkl-config-java, a Java config library based on the Pkl config language.")
      }
    }
  }
}
