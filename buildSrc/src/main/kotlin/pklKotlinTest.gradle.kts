/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.net.URI
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins { kotlin("jvm") }

val buildInfo = project.extensions.getByType<BuildInfo>()

dependencies {
  testImplementation(buildInfo.libs.findLibrary("assertj").get())
  testImplementation(buildInfo.libs.findLibrary("junitApi").get())
  testImplementation(buildInfo.libs.findLibrary("junitParams").get())
  testImplementation(buildInfo.libs.findLibrary("kotlinStdLib").get())

  testRuntimeOnly(buildInfo.libs.findLibrary("junitEngine").get())
}

tasks.withType<Test>().configureEach {
  val testTask = this

  useJUnitPlatform()

  // enable checking of stdlib return types
  systemProperty("org.pkl.testMode", "true")

  // Disable colour output in tests
  systemProperty("org.fusesource.jansi.Ansi.disable", "true")

  reports.named("html") { enabled = true }

  testLogging { exceptionFormat = TestExceptionFormat.FULL }

  addTestListener(
    object : TestListener {
      override fun beforeSuite(suite: TestDescriptor) {}

      override fun beforeTest(testDescriptor: TestDescriptor) {}

      override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {}

      // print report link at end of task, not just at end of build
      override fun afterSuite(descriptor: TestDescriptor, result: TestResult) {
        if (descriptor.parent != null) return // only interested in overall result

        if (result.resultType == TestResult.ResultType.FAILURE) {
          println(
            "\nThere were failing tests. See the report at: ${fixFileUri(testTask.reports.html.entryPoint.toURI())}"
          )
        }
      }

      // makes links clickable on macOS
      private fun fixFileUri(uri: URI): URI {
        if ("file" == uri.scheme && !uri.schemeSpecificPart.startsWith("//")) {
          return URI.create("file://" + uri.schemeSpecificPart)
        }
        return uri
      }
    }
  )
}
