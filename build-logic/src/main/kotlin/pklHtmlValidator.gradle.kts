import org.gradle.accessors.dm.LibrariesForLibs

plugins {
  base
}

val htmlValidator = extensions.create<HtmlValidator>("htmlValidator", project)

val libs = the<LibrariesForLibs>()

val validatorConfiguration: Configuration = configurations.create("validator") {
  resolutionStrategy.eachDependency {
    if (requested.group == "log4j" && requested.name == "log4j") {
      @Suppress("UnstableApiUsage")
      useTarget(libs.log4j12Api)
      because("mitigate critical security vulnerabilities")
    }
  }
}

dependencies {
  @Suppress("UnstableApiUsage")
  validatorConfiguration(libs.nuValidator) {
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

val validateHtml by tasks.registering(JavaExec::class) {
  val resultFile = layout.buildDirectory.file("validateHtml/result.txt")
  inputs.files(htmlValidator.sources)
  outputs.file(resultFile)

  classpath = validatorConfiguration
  mainClass.set("nu.validator.client.SimpleCommandLineValidator")
  args("--skip-non-html") // --also-check-css doesn't work (still checks css as html), so limit to html files
  args("--filterpattern", "(.*)Consider adding “lang=(.*)")
  args("--filterpattern", "(.*)Consider adding a “lang” attribute(.*)")
  args("--filterpattern", "(.*)unrecognized media “amzn-kf8”(.*)") // kindle
  // for debugging
  // args "--verbose"
  args(htmlValidator.sources)

  // write a basic result file s.t. gradle can consider task up-to-date
  // writing a result file in case validation fails is not easily possible with JavaExec, but also not strictly necessary
  doFirst { project.delete(resultFile) }
  doLast { resultFile.get().asFile.writeText("Success.") }
}

tasks.check {
  dependsOn(validateHtml)
}
