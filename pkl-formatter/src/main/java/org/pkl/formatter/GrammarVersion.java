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
package org.pkl.formatter;

/** Grammar compatibility version. */
public enum GrammarVersion {
  V1(1, "0.25 - 0.29"),
  V2(2, "0.30+");

  private final int version;
  private final String versionSpan;

  GrammarVersion(int version, String versionSpan) {
    this.version = version;
    this.versionSpan = versionSpan;
  }

  public int getVersion() {
    return version;
  }

  public String getVersionSpan() {
    return versionSpan;
  }

  public static GrammarVersion latest() {
    var latest = V1;
    for (var v : values()) {
      if (v.version > latest.version) {
        latest = v;
      }
    }
    return latest;
  }
}
