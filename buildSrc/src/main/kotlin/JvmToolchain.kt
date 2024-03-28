import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.api.Project

private const val JDK_VERSION = 11

val jvmToolchainVersion: JavaLanguageVersion
    get() {
        val jvmVersion = System.getProperty("PKL_JVM_VERSION", JDK_VERSION.toString()).toInt()
        return JavaLanguageVersion.of(jvmVersion)
    }

val jvmToolchainVendor: JvmVendorSpec 
    get() = JvmVendorSpec.ADOPTIUM