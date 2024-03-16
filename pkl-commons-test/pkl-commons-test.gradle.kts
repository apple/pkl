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
  api(projects.pklCommons) // for convenience
  implementation(libs.assertj)
  runtimeOnly(projects.pklCerts)
}

/**
 * Creates test packages from the `src/test/files/packages` directory.
 *
 * These packages are used by PackageServer to serve assets when running
 * LanguageSnippetTests and PackageResolversTest.
 */
val createTestPackages by tasks.registering

// make sure that declaring a dependency on this project suffices to have test fixtures generated
tasks.processResources {
  dependsOn(createTestPackages)
  dependsOn(exportCerts)
}

for (packageDir in file("src/main/files/packages").listFiles()!!) {
  if (!packageDir.isDirectory) continue
  val destinationDir = file("build/test-packages/${packageDir.name}")
  val metadataJson = file("$packageDir/${packageDir.name}.json")
  val packageContents = packageDir.resolve("package")
  val zipFileName = "${packageDir.name}.zip"
  val archiveFile = destinationDir.resolve(zipFileName)

  val zipTask = tasks.register("zip-${packageDir.name}", Zip::class) {
    destinationDirectory.set(destinationDir)
    archiveFileName.set(zipFileName)
    from(packageContents)
    // required so that checksums are reproducible
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
  }

  val copyTask = tasks.register("copy-${packageDir.name}", Copy::class) {
    dependsOn(zipTask)
    from(metadataJson)
    into(destinationDir)
    val shasumFile = file("$destinationDir/${packageDir.name}.json.sha256")
    outputs.file(shasumFile)
    filter { line ->
      line.replaceFirst("\$computedChecksum", archiveFile.computeChecksum())
    }
    doLast {
      val outputFile = destinationDir.resolve("${packageDir.name}.json")
      shasumFile.writeText(outputFile.computeChecksum())
    }
  }

  createTestPackages.configure { 
    dependsOn(copyTask) 
  }
}

val keystoreDir = file("build/keystore")
val keystoreName = "localhost.p12"
val certsFileName = "localhost.pem"

val generateKeys by tasks.registering(JavaExec::class) {
  val outputFile = file("$keystoreDir/$keystoreName")
  outputs.file(outputFile)
  mainClass.set("sun.security.tools.keytool.Main")
  args = listOf(
    "-genkeypair",
    "-keyalg", "RSA",
    "-alias", "integ_tests",
    "-keystore", keystoreName,
    "-storepass", "password",
    "-dname", "CN=localhost"
  )
  workingDir = keystoreDir
  doFirst {
    workingDir.mkdirs()
    outputFile.delete()
  }
}

val exportCerts by tasks.registering(JavaExec::class) {
  val outputFile = file("$keystoreDir/$certsFileName")
  dependsOn(generateKeys)
  inputs.file("$keystoreDir/$keystoreName")
  outputs.file(outputFile)
  mainClass.set("sun.security.tools.keytool.Main")
  args = listOf(
    "-exportcert",
    "-alias", "integ_tests",
    "-storepass", "password",
    "-keystore", keystoreName,
    "-rfc",
    "-file", certsFileName
  )
  workingDir = keystoreDir
  doFirst {
    workingDir.mkdirs()
    outputFile.delete()
  }
}

fun toHex(hash: ByteArray): String {
  val hexDigitTable = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
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
