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
plugins { base }

val htmlValidator = extensions.create<HtmlValidator>("htmlValidator", project)

val buildInfo = project.extensions.getByType<BuildInfo>()

val validatorConfiguration: Configuration =
  configurations.create("validator") {
    resolutionStrategy.eachDependency {
      if (requested.group == "log4j" && requested.name == "log4j") {
        @Suppress("UnstableApiUsage") useTarget(buildInfo.libs.findLibrary("log4j12Api").get())
        because("mitigate critical security vulnerabilities")
      }
    }
  }

dependencies {
  @Suppress("UnstableApiUsage")
  validatorConfiguration(buildInfo.libs.findLibrary("nuValidator").get()) {
    // we only want jetty-util and jetty-util-ajax (with the right version)
    // couldn't find a more robust way to express this
    exclude(group = "org.eclipse.jetty", module = "jetty-continuation")
    exclude(group = "org.eclipse.jetty", module = "jetty-http")
    exclude(group = "org.eclipse.jetty", module = "jetty-io")
    exclude(group = "org.eclipse.jetty", module = "jetty-security")
    exclude(group = "org.eclipse.jetty", module = "jetty-server")
    exclude(group = "org.eclipse.jetty", module = "jetty-servlets")
    exclude(group = "javax.servlet")
    exclude(group = "commons-fileupload")
  }
}

val validateHtml by
  tasks.registering(JavaExec::class) {
    val resultFile = layout.buildDirectory.file("validateHtml/result.txt")
    inputs.files(htmlValidator.sources)
    outputs.file(resultFile)

    classpath = validatorConfiguration
    mainClass.set("nu.validator.client.SimpleCommandLineValidator")
    args(
      "--skip-non-html"
    ) // --also-check-css doesn't work (still checks css as html), so limit to html files
    args("--filterpattern", "(.*)Consider adding “lang=(.*)")
    args("--filterpattern", "(.*)Consider adding a “lang” attribute(.*)")
    args("--filterpattern", "(.*)unrecognized media “amzn-kf8”(.*)") // kindle
    // for debugging
    // args "--verbose"
    args(htmlValidator.sources)

    // write a basic result file s.t. gradle can consider task up-to-date
    // writing a result file in case validation fails is not easily possible with JavaExec, but also
    // not strictly necessary
    doFirst { project.delete(resultFile) }
    doLast { resultFile.get().asFile.writeText("Success.") }
  }

tasks.check { dependsOn(validateHtml) }
