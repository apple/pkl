import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.net.URI

plugins {
  kotlin("jvm")
}

val buildInfo = project.extensions.getByType<BuildInfo>()

dependencies {
  testImplementation(buildInfo.libs.findLibrary("assertj").get())
  testImplementation(buildInfo.libs.findLibrary("junitApi").get())
  testImplementation(buildInfo.libs.findLibrary("junitParams").get())
  testImplementation(buildInfo.libs.findLibrary("kotlinStdLib").get())

  testRuntimeOnly(buildInfo.libs.findLibrary("junitEngine").get())
}

kotlin.jvmToolchain {
  languageVersion.set(jvmToolchainVersion)
  vendor.set(jvmToolchainVendor)
}

tasks.withType<Test>().configureEach {
  val testTask = this

  useJUnitPlatform()

  // enable checking of stdlib return types
  systemProperty("org.pkl.testMode", "true")

  reports.named("html") {
    enabled = true
  }

  testLogging {
    exceptionFormat = TestExceptionFormat.FULL
  }

  addTestListener(object : TestListener {
    override fun beforeSuite(suite: TestDescriptor) {}
    override fun beforeTest(testDescriptor: TestDescriptor) {}
    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}

    // print report link at end of task, not just at end of build
    override fun afterSuite(descriptor: TestDescriptor, result: TestResult) {
      if (descriptor.parent != null) return // only interested in overall result

      if (result.resultType == TestResult.ResultType.FAILURE) {
        println("\nThere were failing tests. See the report at: ${fixFileUri(testTask.reports.html.entryPoint.toURI())}")
      }
    }

    // makes links clickable on macOS
    private fun fixFileUri(uri: URI): URI {
      if ("file" == uri.scheme && !uri.schemeSpecificPart.startsWith("//")) {
        return URI.create("file://" + uri.schemeSpecificPart)
      }
      return uri
    }
  })
}
