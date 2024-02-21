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
package org.pkl.config.java;

import java.lang.reflect.Type;

/**
 * Thrown by {@link Config#as(Type)} when the determined Java class for a Pkl value cannot be found
 * on the classpath.
 *
 * <p>When this happens, the most likely explanation is that the generated code is not up-to-date.
 */
public class InvalidMappingException extends RuntimeException {
  private final String pklName;

  private final String javaName;

  public InvalidMappingException(String pklName, String javaName, Exception cause) {
    super(cause);
    this.pklName = pklName;
    this.javaName = javaName;
  }

  @Override
  public String getMessage() {
    return "Did not find expected Java class `"
        + javaName
        + "` on the classpath for Pkl class `"
        + pklName
        + "`. Is your generated code up to date?";
  }

  public String getPklName() {
    return pklName;
  }

  public String getJavaName() {
    return javaName;
  }
}
