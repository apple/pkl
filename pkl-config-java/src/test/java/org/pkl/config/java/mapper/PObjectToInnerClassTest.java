/*
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
package org.pkl.config.java.mapper;

import static org.assertj.core.api.Assertions.*;
import static org.pkl.core.ModuleSource.text;

import org.junit.jupiter.api.Test;
import org.pkl.config.java.ConfigEvaluator;

public class PObjectToInnerClassTest {
  @SuppressWarnings("InnerClassMayBeStatic")
  public class InnerConfig {
    final String text;

    public InnerConfig(@Named("text") String text) {
      this.text = text;
    }
  }

  // verify that a workaround for https://bugs.openjdk.java.net/browse/JDK-8025806 is in place
  // conversion to inner class is still expected to fail but with the usual `ConversionException`
  @Test
  public void attemptToConvertToInnerClassDoesNotFailWithIndexOutOfBoundsException() {
    try (var evaluator = ConfigEvaluator.preconfigured()) {
      var config =
          evaluator.evaluate(
              text(
                  """
                  class Inner {
                    text: String = "Bar"
                  }
                  inner: Inner
                  """));

      assertThatExceptionOfType(ConversionException.class)
          .isThrownBy(() -> config.get("inner").as(InnerConfig.class));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
