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
package org.pkl.lsp

import java.text.MessageFormat
import java.util.*

object ErrorMessages {
  private val bundle: ResourceBundle by lazy {
    ResourceBundle.getBundle("org.pkl.lsp.errorMessages", Locale.getDefault())
  }

  fun create(messageName: String, vararg args: Any): String {
    if (!bundle.containsKey(messageName)) return messageName

    val errorMessage = bundle.getString(messageName)
    // only format if `errorMessage` is a format string
    if (args.isEmpty()) return errorMessage

    val locale = Locale.getDefault()
    val formatter = MessageFormat(errorMessage, locale)
    return formatter.format(args)
  }
}
