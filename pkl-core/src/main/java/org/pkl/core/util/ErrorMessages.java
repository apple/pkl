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
package org.pkl.core.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.*;

public final class ErrorMessages {
  private ErrorMessages() {}

  public static String create(String messageName, Object... args) {
    var locale = Locale.getDefault();
    String errorMessage =
        ResourceBundle.getBundle("org.pkl.core.errorMessages", locale).getString(messageName);

    // only format if `errorMessage` is a format string
    if (args.length == 0) return errorMessage;

    var formatter = new MessageFormat(errorMessage, locale);
    return formatter.format(args);
  }

  public static String createIndented(String messageName, String indent, Object... args) {
    return create(messageName, args)
        .lines()
        .map((msg) -> indent + msg)
        .collect(Collectors.joining("\n"));
  }
}
