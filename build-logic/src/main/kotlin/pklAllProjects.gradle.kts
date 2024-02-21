@file:Suppress("UnstableApiUsage")

import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("unused")
val buildInfo = extensions.create<BuildInfo>("buildInfo", project)

val libs = the<LibrariesForLibs>()

// Default JVM bytecode target, and Java language level.
private val defaultJavaTarget = "11"

// Property where the JVM target and Java language level can be overridden.
private val buildPropertyJavaTarget = "javaTarget"

// Property where the Kotlin target can be overridden.
private val buildPropertyKotlinTarget = "kotlinTarget"

// Property to enable dependency locking.
private val buildPropertyDependencyLocks = "lockDependencies"

// Property to enable strict compiler modes.
private val buildPropertyStrictMode = "strict"

// Arguments to pass to `javac`.
private val javacArgs = listOf(
  "-parameters",
)

// Arguments to pass to `kotlinc`.
private val kotlincArgs = listOf(
  "-Xjsr305=strict",
  "-Xjvm-default=all",
  "-Xextended-compiler-checks",
  "-Xemit-jvm-type-annotations",
  "-Xlambdas=indy",
  "-Xsam-conversions=indy",
  "-Xcontext-receivers",
)

// Configurations which are subject to dependency locking.
private val lockedConfigurations = listOf(
  "annotationProcessor",
  "archives",
  "compileClasspath",
  "graal",
  "kotlinCompilerClasspath",
  "kotlinKlibCommonizerClasspath",
  "runtimeClasspath",
  "testCompileClasspath",
  "testRuntimeClasspath",
  "truffle",
)

// Compiler strict mode.
private val strictMode = findProperty(buildPropertyStrictMode) == "true"

// Dependency pins.
val dependencyPins = listOf(
  "org.jetbrains.kotlin:kotlin-stdlib" to libs.versions.kotlin,  // general pins (critical dependency)
  "org.jetbrains.kotlin:kotlin-stdlib-common" to libs.versions.kotlin,  // general pins (critical dependency)
  "commons-io:commons-io" to libs.versions.commonsIo,  // CVE-2021-29425
  "org.apache.httpcomponents:httpclient" to libs.versions.cvePins.apacheHttpClient,  // CVE-2020-13956
).toMap()

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

  if (findProperty(buildPropertyDependencyLocks) == "true") all {
    if (name in lockedConfigurations) {
      resolutionStrategy.activateDependencyLocking()
    }
  }

  all {
    // don't spend the extra cycles to verify dependencies for linters
    if ("spotless" in name) resolutionStrategy {
      deactivateDependencyLocking()
    }

    // don't verify or lock detached configurations
    if ("detached" in name) resolutionStrategy {
      disableDependencyVerification()
      deactivateDependencyLocking()
    }

    resolutionStrategy {
      eachDependency {
        when (val pin = dependencyPins["${requested.group}:${requested.name}"]) {
          null -> {}
          else -> pin.get().let {
            useVersion(it)
            because("pinned dependencies")
          }
        }
      }
    }
  }
}

plugins.withType(JavaPlugin::class).configureEach {
  val java = project.extensions.getByType<JavaPluginExtension>()
  val target = JavaVersion.toVersion(project.findProperty("javaTarget") as? String ?: defaultJavaTarget)
  java.sourceCompatibility = target
  java.targetCompatibility = target
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    jvmTarget = findProperty(buildPropertyJavaTarget) as? String ?: defaultJavaTarget
    freeCompilerArgs = freeCompilerArgs + kotlincArgs
    allWarningsAsErrors = strictMode
    javaParameters = true

    (findProperty(buildPropertyKotlinTarget) as? String)?.ifBlank { null }?.let { kotlinTarget ->
      apiVersion = kotlinTarget
      languageVersion = kotlinTarget
    }
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.compilerArgumentProviders.add(CommandLineArgumentProvider {
    javacArgs
  })
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
// from: https://docs.gradle.org/current/userguide/dependency_locking.html#generating_and_updating_dependency_locks
val updateDependencyLocks by tasks.registering {
  notCompatibleWithConfigurationCache("Filters configurations at execution time")

  doLast {
    configurations
      .filter { it.isCanBeResolved }
      .forEach { it.resolve() }
  }
}

val allDependencies by tasks.registering(DependencyReportTask::class)

// generate archives in a reproducible manner
listOf(
  Jar::class,
  Zip::class,
  Tar::class,
).forEach {
  tasks.withType(it).configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false

    when (this) {
      is Zip -> isZip64 = true
      is Jar -> isZip64 = true
      else -> {}
    }
  }
}

// tasks which should not be eligible for build caching, either because they are too big, or because
// it does not save time
listOf(
  "spotlessCheck",
  "spotlessApply",
).forEach {
  tasks.findByName(it)?.configure<Task> {
    outputs.cacheIf { false }
    doNotTrackState("not eligible for caching")
  }
}
