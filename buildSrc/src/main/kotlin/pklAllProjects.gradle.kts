import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val buildInfo = extensions.create<BuildInfo>("buildInfo", project)

dependencyLocking {
  lockAllConfigurations()
}

configurations {
  val rejectedVersionSuffix = Regex("-alpha|-beta|-eap|-m|-rc|-snapshot", RegexOption.IGNORE_CASE)
  configureEach {
    resolutionStrategy {
      componentSelection {
        all {
          if (rejectedVersionSuffix.containsMatchIn(candidate.version)) {
            reject("Rejected dependency $candidate " +
                "because it has a prelease version suffix matching `$rejectedVersionSuffix`.")
          }
        }
      }
    }
  }
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    freeCompilerArgs = freeCompilerArgs + listOf("-Xjsr305=strict", "-Xjvm-default=all")
  }
}

plugins.withType(IdeaPlugin::class).configureEach {
  val errorMessage = "Use IntelliJ Gradle import instead of running the `idea` task. See README for more information."

  tasks.named("idea") {
    doFirst {
      throw GradleException(errorMessage)
    }
  }
  tasks.named("ideaModule") {
    doFirst {
      throw GradleException(errorMessage)
    }
  }
  if (project == rootProject) {
    tasks.named("ideaProject") {
      doFirst {
        throw GradleException(errorMessage)
      }
    }
  }
}

plugins.withType(MavenPublishPlugin::class).configureEach {
  configure<PublishingExtension> {
    // CI builds pick up artifacts from this repo.
    // It's important that this repo is only declared once per project.
    repositories {
      maven {
        name = "projectLocal" // affects task names
        url = uri("file:///$rootDir/build/m2")
      }
    }
    // use resolved/locked (e.g., `1.15`)
    // instead of declared (e.g., `1.+`)
    // dependency versions in generated POMs
    publications {
      withType(MavenPublication::class.java) {
        versionMapping {
          allVariants {
            fromResolutionResult()
          }
        }
      }
    }
  }
}

// settings.gradle.kts sets `--write-locks`
// if Gradle command line contains this task name
val updateDependencyLocks by tasks.registering {
  doLast {
    configurations
      .filter { it.isCanBeResolved }
      .forEach { it.resolve() }
  }
}

val allDependencies by tasks.registering(DependencyReportTask::class)
