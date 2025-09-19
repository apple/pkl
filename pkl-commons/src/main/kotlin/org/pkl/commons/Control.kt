/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.util.WeakHashMap
import kotlin.reflect.KProperty

// Adapted from https://stackoverflow.com/a/38084930
fun <This, Return> lazyWithReceiver(
  initializer: This.() -> Return
): LazyWithReceiver<This, Return> = LazyWithReceiver(initializer)

class LazyWithReceiver<This, out Return>(val initializer: This.() -> Return) {
  private val values = WeakHashMap<This, Return>()

  private val lock = Object()

  operator fun getValue(thisValue: This, property: KProperty<*>): Return =
    synchronized(lock) { values.getOrPut(thisValue) { thisValue.initializer() } }
}
