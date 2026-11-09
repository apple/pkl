/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.commons

import org.junit.jupiter.api.Test

class StringsTest {
  @Test
  fun isValidConfigurationPropertiesPrefix() {
    val passes = listOf("app.datasource", "my-app.service-config", "app2.v1-api")
    val negatives =
      listOf(
        "myApp.service",
        "my_app.service",
        "1app.service",
        "app..service",
        ".app.service / app.",
      )
    for (value in passes) {
      assert(value.isValidConfigurationPropertiesPrefix) {
        "$value should have been valid but was not"
      }
    }
    for (value in negatives) {
      assert(!value.isValidConfigurationPropertiesPrefix) {
        "$value should not have been valid but was"
      }
    }
  }
}
