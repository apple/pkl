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
import java.security.MessageDigest

plugins {
  pklAllProjects
  pklKotlinLibrary
}

// note: no need to publish this library

dependencies {
  api(libs.junitApi)
  api(libs.junitEngine)
  api(libs.junitParams)
  api(libs.jansi)
  api(projects.pklCommons) // for convenience
  implementation(libs.assertj)
}

/**
 * Creates test packages from the `src/test/files/packages` directory.
 *
 * These packages are used by PackageServer to serve assets when running LanguageSnippetTests and
 * PackageResolversTest.
 */
val createTestPackages by tasks.registering

// make sure that declaring a dependency on this project suffices to have test fixtures generated
tasks.processResources {
  dependsOn(createTestPackages)
  dependsOn(exportCerts)
}

for (packageDir in file("src/main/files/packages").listFiles()!!) {
  if (!packageDir.isDirectory) continue
  val destinationDir = layout.buildDirectory.dir("test-packages/${packageDir.name}")
  val metadataJson = file("$packageDir/${packageDir.name}.json")
  val packageContents = packageDir.resolve("package")
  val zipFileName = "${packageDir.name}.zip"
  val archiveFile = destinationDir.map { it.file(zipFileName) }

  val zipTask =
    tasks.register("zip-${packageDir.name}", Zip::class) {
      destinationDirectory.set(destinationDir)
      archiveFileName.set(zipFileName)
      from(packageContents)
      // required so that checksums are reproducible
      isPreserveFileTimestamps = false
      isReproducibleFileOrder = true
    }

  val copyTask =
    tasks.register("copy-${packageDir.name}", Copy::class) {
      dependsOn(zipTask)
      from(metadataJson)
      into(destinationDir)
      val shasumFile = destinationDir.map { it.file("${packageDir.name}.json.sha256") }
      outputs.file(shasumFile)
      filter { line ->
        line.replaceFirst("\$computedChecksum", archiveFile.get().asFile.computeChecksum())
      }
      doLast {
        val outputFile = destinationDir.get().asFile.resolve("${packageDir.name}.json")
        if (buildInfo.os.isWindows) {
          val contents = outputFile.readText()
          // workaround for https://github.com/gradle/gradle/issues/1151
          outputFile.writeText(contents.replace("\r\n", "\n"))
        }
        shasumFile.get().asFile.writeText(outputFile.computeChecksum())
      }
    }

  createTestPackages.configure { dependsOn(copyTask) }
}

val keystoreDir = layout.buildDirectory.dir("keystore")
val keystoreName = "localhost.p12"
val keystoreFile = keystoreDir.map { it.file(keystoreName) }
val certsFileName = "localhost.pem"

val generateKeys by
  tasks.registering(JavaExec::class) {
    outputs.file(keystoreFile)
    mainClass.set("sun.security.tools.keytool.Main")
    args =
      listOf(
        "-genkeypair",
        "-keyalg",
        "RSA",
        "-alias",
        "integ_tests",
        "-keystore",
        keystoreName,
        "-storepass",
        "password",
        "-dname",
        "CN=localhost"
      )
    workingDir(keystoreDir)
    doFirst {
      workingDir.mkdirs()
      keystoreFile.get().asFile.delete()
    }
  }

val exportCerts by
  tasks.registering(JavaExec::class) {
    val outputFile = keystoreDir.map { it.file(certsFileName) }
    dependsOn(generateKeys)
    inputs.file(keystoreFile)
    outputs.file(outputFile)
    mainClass.set("sun.security.tools.keytool.Main")
    args =
      listOf(
        "-exportcert",
        "-alias",
        "integ_tests",
        "-storepass",
        "password",
        "-keystore",
        keystoreName,
        "-rfc",
        "-file",
        certsFileName
      )
    workingDir(keystoreDir)
    doFirst {
      workingDir.mkdirs()
      outputFile.get().asFile.delete()
    }
  }

fun toHex(hash: ByteArray): String {
  val hexDigitTable =
    charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
  return buildString(hash.size * 2) {
    for (b in hash) {
      append(hexDigitTable[b.toInt() shr 4 and 0xF])
      append(hexDigitTable[b.toInt() and 0xF])
    }
  }
}

fun File.computeChecksum(): String {
  val md = MessageDigest.getInstance("SHA-256")
  val hash = md.digest(readBytes())
  return toHex(hash)
}
