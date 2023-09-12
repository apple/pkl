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
  api(project(":pkl-commons")) // for convenience
  implementation(libs.assertj)
}


/**
 * Creates test packages from the `src/test/files/packages` directory.
 *
 * These packages are used by PackageServer to serve assets when running
 * LanguageSnippetTests and PackageResolversTest.
 */
val createTestPackages = tasks.create("createTestPackages")

fun toHex(hash: ByteArray): String {
  val hexDigitTable = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
  val builder = StringBuilder(hash.size * 2)
  for (b in hash) {
    builder.append(hexDigitTable[b.toInt() shr 4 and 0xF])
    builder.append(hexDigitTable[b.toInt() and 0xF])
  }
  return builder.toString()
}

fun File.computeChecksum(): String {
  val md = MessageDigest.getInstance("SHA-256")
  val hash = md.digest(readBytes())
  return toHex(hash)
}

tasks.processResources {
  dependsOn(createTestPackages)
  dependsOn(generateCerts)
}

val mainSourceSet by sourceSets.named("main") {
  resources {
    srcDir(buildDir.resolve("test-packages/"))
    srcDir(buildDir.resolve("keystore/"))
  }
}

val sourcesJar = tasks.named("sourcesJar").get()

for (packageDir in file("src/main/files/packages").listFiles()!!) {
  if (!packageDir.isDirectory) continue
  val destinationDir = buildDir.resolve("test-packages/org/pkl/commons/test/packages/${packageDir.name}")
  val metadataJson = packageDir.resolve("${packageDir.name}.json")
  val packageContents = packageDir.resolve("package")
  val zipFileName = "${packageDir.name}.zip"
  val archiveFile = destinationDir.resolve(zipFileName)

  tasks.create("zip-${packageDir.name}", Zip::class) {
    archiveFileName.set(zipFileName)
    from(packageContents)
    destinationDirectory.set(destinationDir)
    // required so that checksums are reproducible
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
  }

  val copyTask = tasks.create("copy-${packageDir.name}", Copy::class) {
    dependsOn("zip-${packageDir.name}")
    from(metadataJson)
    into(destinationDir)
    val shasumFile = file("$destinationDir/${packageDir.name}.json.sha256")
    outputs.file(shasumFile)
    doFirst {
      expand(mapOf("computedChecksum" to archiveFile.computeChecksum()))
    }
    doLast {
      val outputFile = file("$destinationDir").resolve("${packageDir.name}.json")
      shasumFile.writeText(outputFile.computeChecksum())
    }
    createTestPackages.dependsOn(this)
  }

  sourcesJar.dependsOn.add(copyTask)
}

val generateKeys by tasks.registering(JavaExec::class) {
  val outputFile = file("$buildDir/keystore/localhost.p12")
  outputs.file(outputFile)
  mainClass.set("sun.security.tools.keytool.Main")
  args = listOf(
    "-genkeypair",
    "-keyalg", "RSA",
    "-alias", "integ_tests",
    "-keystore", "localhost.p12",
    "-storepass", "password",
    "-dname", "CN=localhost"
  )
  workingDir = file("$buildDir/keystore/")
  onlyIf { !outputFile.exists() }
  doFirst {
    workingDir.mkdirs()
  }
}

val generateCerts by tasks.registering(Exec::class) {
  dependsOn("generateKeys")
  val outputFile = file("$buildDir/keystore/localhost.pem")
  outputs.file(outputFile)
  commandLine = listOf(
    "keytool",
    "-exportcert",
    "-alias", "integ_tests",
    "-storepass", "password",
    "-keystore", "localhost.p12",
    "-rfc",
    "-file", "localhost.pem"
  )
  workingDir = file("$buildDir/keystore/")
  onlyIf { !outputFile.exists() }
  doFirst {
    workingDir.mkdirs()
  }
}
