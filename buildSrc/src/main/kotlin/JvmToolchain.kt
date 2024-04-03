import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

private const val JDK_VERSION = 11

val jvmToolchainVersion: JavaLanguageVersion
    get() {
        val jvmVersion = System.getProperty("PKL_JVM_VERSION")?.toInt() ?: JDK_VERSION
        return JavaLanguageVersion.of(jvmVersion)
    }

val jvmToolchainVendor: JvmVendorSpec 
    get() = JvmVendorSpec.ADOPTIUM
