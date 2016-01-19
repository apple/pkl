package org.pkl.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VersionTest {
  @Test
  fun `parse release version`() {
    val version = Version.parse("1.2.3")
    assertThat(version.major).isEqualTo(1)
    assertThat(version.minor).isEqualTo(2)
    assertThat(version.patch).isEqualTo(3)
    assertThat(version.preRelease).isNull()
    assertThat(version.build).isNull()
  }

  @Test
  fun `parse snapshot version`() {
    val version = Version.parse("1.2.3-SNAPSHOT+build-123")
    assertThat(version.major).isEqualTo(1)
    assertThat(version.minor).isEqualTo(2)
    assertThat(version.patch).isEqualTo(3)
    assertThat(version.preRelease).isEqualTo("SNAPSHOT")
    assertThat(version.build).isEqualTo("build-123")
  }

  @Test
  fun `parse beta version`() {
    val version = Version.parse("1.2.3-beta.1+build-123")
    assertThat(version.major).isEqualTo(1)
    assertThat(version.minor).isEqualTo(2)
    assertThat(version.patch).isEqualTo(3)
    assertThat(version.preRelease).isEqualTo("beta.1")
    assertThat(version.build).isEqualTo("build-123")
  }

  @Test
  fun `parse invalid version`() {
    assertThat(Version.parseOrNull("not a version number"))
      .isNull()

    assertThrows<IllegalArgumentException> {
      Version.parse("not a version number")
    }
  }

  @Test
  fun `parse too large version`() {
    assertThrows<IllegalArgumentException> {
      Version.parse("not a version number")
    }

    assertThrows<IllegalArgumentException> {
      Version.parse("999999999999999.0.0")
    }
  }

  @Test
  fun toNormal() {
    val stripped = Version.parse("1.2.3")
    assertThat(Version.parse("1.2.3-beta-1+build-123").toNormal()).isEqualTo(stripped)
    assertThat(Version.parse("1.2.3-beta-1").toNormal()).isEqualTo(stripped)
    assertThat(Version.parse("1.2.3").toNormal()).isEqualTo(stripped)
  }

  @Test
  fun withMethods() {
    val version = Version.parse("0.0.0")
      .withMajor(1)
      .withMinor(2)
      .withPatch(3)
      .withPreRelease("rc.1")
      .withBuild("456.789")

    assertThat(version).isEqualTo(Version.parse("1.2.3-rc.1+456.789"))

    val version2 = Version.parse("0.0.0")
      .withBuild("456.789")
      .withPreRelease("rc.1")
      .withPatch(3)
      .withMinor(2)
      .withMajor(1)

    assertThat(version2).isEqualTo(version)
  }

  @Test
  fun `compareTo()`() {
    assertThat(
      Version(1, 2, 3, null, null).compareTo(
        Version(1, 2, 3, null, null)
      )
    ).isEqualTo(0)
    assertThat(
      Version(1, 2, 3, "SNAPSHOT", null).compareTo(
        Version(1, 2, 3, "SNAPSHOT", null)
      )
    ).isEqualTo(0)
    assertThat(
      Version(1, 2, 3, "alpha", null).compareTo(
        Version(1, 2, 3, "alpha", null)
      )
    ).isEqualTo(0)
    assertThat(
      Version(1, 2, 3, "alpha", null).compareTo(
        Version(1, 2, 3, "alpha", "build123")
      )
    ).isEqualTo(0)

    assertThat(
      Version(1, 2, 3, null, null).compareTo(
        Version(2, 2, 3, null, null)
      )
    ).isLessThan(0)
    assertThat(
      Version(1, 2, 3, null, null).compareTo(
        Version(1, 3, 3, null, null)
      )
    ).isLessThan(0)
    assertThat(
      Version(1, 2, 3, null, null).compareTo(
        Version(1, 2, 4, null, null)
      )
    ).isLessThan(0)

    assertThat(
      Version(2, 2, 3, null, null).compareTo(
        Version(1, 2, 3, null, null)
      )
    ).isGreaterThan(0)
    assertThat(
      Version(1, 3, 3, null, null).compareTo(
        Version(1, 2, 3, null, null)
      )
    ).isGreaterThan(0)
    assertThat(
      Version(1, 2, 4, null, null).compareTo(
        Version(1, 2, 3, null, null)
      )
    ).isGreaterThan(0)

    assertThat(
      Version(1, 2, 3, "SNAPSHOT", null).compareTo(
        Version(1, 2, 3, null, null)
      )
    ).isLessThan(0)
    assertThat(
      Version(1, 2, 3, "alpha", null).compareTo(
        Version(1, 2, 3, "beta", null)
      )
    ).isLessThan(0)
    assertThat(
      Version(1, 2, 3, "alpha", "build123").compareTo(
        Version(1, 2, 3, "beta", null)
      )
    ).isLessThan(0)

    assertThat(
      Version(1, 2, 3, null, null).compareTo(
        Version(1, 2, 3, "SNAPSHOT", null)
      )
    ).isGreaterThan(0)
    assertThat(
      Version(1, 2, 3, "beta", null).compareTo(
        Version(1, 2, 3, "alpha", "build123")
      )
    ).isGreaterThan(0)
  }

  @Test
  fun `compare version with too large numeric pre-release identifier`() {
    // error is deferred until compareTo(), but should be good enough
    assertThrows<IllegalArgumentException> {
      Version(1, 2, 3, "999", null).compareTo(
        Version(1, 2, 3, "9999999999999999999", null)
      )
    }
  }

  @Test
  fun `equals()`() {
    assertThat(Version(1, 2, 3, null, null))
      .isEqualTo(Version(1, 2, 3, null, null))
    assertThat(Version(1, 2, 3, "SNAPSHOT", null))
      .isEqualTo(Version(1, 2, 3, "SNAPSHOT", null))
    assertThat(Version(1, 2, 3, "alpha", null))
      .isEqualTo(Version(1, 2, 3, "alpha", null))
    assertThat(Version(1, 2, 3, "beta", "build123"))
      .isEqualTo(Version(1, 2, 3, "beta", "build456"))

    assertThat(Version(1, 3, 3, null, null))
      .isNotEqualTo(Version(1, 2, 3, null, null))
    assertThat(Version(1, 2, 4, null, null))
      .isNotEqualTo(Version(1, 2, 3, null, null))
    assertThat(Version(1, 2, 3, "SNAPSHOT", null))
      .isNotEqualTo(Version(1, 2, 3, null, null))
    assertThat(Version(1, 2, 3, "beta", null))
      .isNotEqualTo(Version(1, 2, 3, "alpha", null))
  }

  @Test
  fun `hashCode()`() {
    assertThat(Version(1, 2, 3, null, null).hashCode())
      .isEqualTo(Version(1, 2, 3, null, null).hashCode())
    assertThat(Version(1, 2, 3, "SNAPSHOT", null).hashCode())
      .isEqualTo(Version(1, 2, 3, "SNAPSHOT", null).hashCode())
    assertThat(Version(1, 2, 3, "alpha", null).hashCode())
      .isEqualTo(Version(1, 2, 3, "alpha", null).hashCode())
    assertThat(Version(1, 2, 3, "alpha", "build123").hashCode())
      .isEqualTo(Version(1, 2, 3, "alpha", "build456").hashCode())
  }
}
