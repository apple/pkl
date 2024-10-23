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
package org.pkl.core.packages;

import org.pkl.core.util.ErrorMessages;

public final class PackageLoadError extends RuntimeException {

  private final String messageName;
  private final Object[] arguments;

  public PackageLoadError(Throwable cause, String messageName, Object... arguments) {
    super(ErrorMessages.create(messageName, arguments), cause);
    this.messageName = messageName;
    this.arguments = arguments;
  }

  public PackageLoadError(String messageName, Object... arguments) {
    super(ErrorMessages.create(messageName, arguments));
    this.messageName = messageName;
    this.arguments = arguments;
  }

  public String getMessageName() {
    return messageName;
  }

  public Object[] getArguments() {
    return arguments;
  }
}
