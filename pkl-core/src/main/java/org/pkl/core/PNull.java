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
package org.pkl.core;

import java.io.Serial;

/** Java representation of a {@code pkl.base#Null} value. */
public final class PNull implements Value {
  @Serial private static final long serialVersionUID = 0L;
  private static final PNull INSTANCE = new PNull();

  /** Returns the sole instance of this class. */
  public static PNull getInstance() {
    return INSTANCE;
  }

  @Override
  public void accept(ValueVisitor visitor) {
    visitor.visitNull();
  }

  @Override
  public <T> T accept(ValueConverter<T> converter) {
    return converter.convertNull();
  }

  @Override
  public PClassInfo<?> getClassInfo() {
    return PClassInfo.Null;
  }

  @Override
  public String toString() {
    return "null";
  }
}
