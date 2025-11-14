/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

plugins {
  pklAllProjects
  pklFatJar
  signing
}

val placeholder: SourceSet by sourceSets.creating

val firstPartySourcesJars by configurations.existing

// Note: pkl-tools cannot (easily) contain pkl-config-kotlin
// because pkl-tools ships with a shaded Kotlin stdlib.
dependencies {
  // Use scope `api` so that other subprojects
  // can declare a normal project dependency on this project,
  // which is desirable for IntelliJ integration.
  // The published fat JAR doesn't declare any dependencies.
  api(projects.pklCli)
  api(projects.pklCodegenJava)
  api(projects.pklCodegenKotlin)
  api(projects.pklConfigJava)
  api(projects.pklCore)
  api(projects.pklDoc)
  api(projects.pklCommons)

  // used by `pklFatJar` plugin (ideally this would be inferred automatically)
  firstPartySourcesJars(project(":pkl-cli", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-codegen-java", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-codegen-kotlin", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-config-java", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-core", "sourcesJar"))
  firstPartySourcesJars(project(":pkl-doc", "sourcesJar"))
}

// TODO: need to figure out how to properly generate javadoc here.
// For now, we'll include a placeholder javadoc jar.
val javadocPlaceholder by tasks.registering(Javadoc::class) { source = placeholder.allJava }

java { withJavadocJar() }

val javadocJar by
  tasks.existing(Jar::class) {
    from(javadocPlaceholder)
    archiveBaseName.set("pkl-tools-all")
    archiveClassifier.set("javadoc")
  }

tasks.shadowJar {
  dependsOn(":pkl-doc:shadowJar")
  archiveBaseName.set("pkl-tools-all")
}

private fun Exec.configureTestStartFatJar(launcher: Provider<JavaLauncher>) {
  dependsOn(tasks.shadowJar)
  group = "verification"

  // placeholder output to satisfy up-to-date check
  val outputFile = layout.buildDirectory.file("testStartFatJar/${name}.txt")
  outputs.file(outputFile)

  inputs.files(tasks.shadowJar)
  executable = launcher.get().executablePath.asFile.absolutePath
  standardOutput = OutputStream.nullOutputStream()

  argumentProviders.add(
    CommandLineArgumentProvider {
      buildList {
        add("-cp")
        add(tasks.shadowJar.get().outputs.files.singleFile.absolutePath)
        add("org.pkl.cli.Main")
        add("eval")
        add("-x")
        add("1 + 1")
        add("pkl:base")
      }
    }
  )

  doLast {
    outputFile.get().asFile.toPath().let { file ->
      file.parent.createDirectories()
      file.writeText("OK")
    }
  }
}

val testStartFatJar by
  tasks.registering(Exec::class) { configureTestStartFatJar(buildInfo.javaTestLauncher) }

tasks.validateFatJar { dependsOn(testStartFatJar) }

for (jdkTarget in buildInfo.jdkTestRange) {
  if (buildInfo.jdkToolchainVersion == jdkTarget) {
    tasks.register("testStartFatJarJdk${jdkTarget.asInt()}") {
      group = "verification"
      description = "alias for testStartFatJar"
      dependsOn(testStartFatJar)
    }
  } else {
    val task =
      tasks.register("testStartFatJarJdk${jdkTarget.asInt()}", Exec::class) {
        enabled = buildInfo.isVersionEnabled(jdkTarget)
        val launcher = project.javaToolchains.launcherFor { languageVersion = jdkTarget }
        configureTestStartFatJar(launcher)
      }
    if (buildInfo.multiJdkTesting) {
      tasks.validateFatJar { dependsOn(task) }
    }
  }
}

publishing {
  publications {
    named<MavenPublication>("fatJar") {
      // don't use `-all` suffix because this is the only JAR we publish
      artifactId = "pkl-tools"
      // add placeholder javadoc jar to publication
      artifact(javadocJar)
      pom {
        url.set("https://github.com/apple/pkl/tree/main/pkl-tools")
        description.set(
          "Fat Jar containing pkl-cli, pkl-codegen-java, " +
            "pkl-codegen-kotlin, pkl-config-java, pkl-core, pkl-doc, " +
            "and their shaded third-party dependencies."
        )
        name.set(artifactId)
        // keep in sync with pklPublishLibrary
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("https://github.com/apple/pkl/blob/main/LICENSE.txt")
          }
        }
        developers {
          developer {
            id.set("pkl-authors")
            name.set("The Pkl Authors")
            email.set("pkl-oss@group.apple.com")
          }
        }
        scm {
          connection.set("scm:git:git://github.com/apple/pkl.git")
          developerConnection.set("scm:git:ssh://github.com/apple/pkl.git")
          val buildInfo = project.extensions.getByType<BuildInfo>()
          url.set("https://github.com/apple/pkl/tree/${buildInfo.commitish}")
        }
        issueManagement {
          system.set("GitHub Issues")
          url.set("https://github.com/apple/pkl/issues")
        }
        ciManagement {
          system.set("GitHub Actions")
          url.set("https://github.com/apple/pkl/actions")
        }
      }
    }
  }
}

signing {
  // provided as env vars `ORG_GRADLE_PROJECT_signingKey` and `ORG_GRADLE_PROJECT_signingPassword`
  // in CI.
  val signingKey =
    (findProperty("signingKey") as String?)?.let {
      Base64.getDecoder().decode(it).toString(StandardCharsets.US_ASCII)
    }
  val signingPassword = findProperty("signingPassword") as String?
  if (signingKey != null && signingPassword != null) {
    useInMemoryPgpKeys(signingKey, signingPassword)
  }
  sign(publishing.publications["fatJar"])
}
