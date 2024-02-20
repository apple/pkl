package org.pkl.core

import org.pkl.commons.test.PackageServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.pkl.core.http.HttpClient

class StackFrameTransformersTest {
  // TODO figure out how to test this; right now this fails because there is no VM context.
  @Test
  @Disabled
  fun replacePackageUriWithSourceCodeUrl() {
    PackageServer().use { server ->
      val httpClient = HttpClient.builder().setTestPort(server.port).build()
      EvaluatorBuilder.preconfigured()
        .setHttpClient(httpClient)
        .build().use {
          val frame = StackFrame(
            "package://localhost:12110/birds@0.5.0#/Bird.pkl",
            null,
            listOf(),
            1,
            1,
            2,
            2)
          val transformed =
            StackFrameTransformers.replacePackageUriWithSourceCodeUrl.apply(frame)
          assertThat(transformed.moduleUri).isEqualTo("https://example.com/birds/v0.5.0/blob/Bird.pkl#L1-L2")
        }
    }
  }
}
