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
package org.pkl.server

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.msgpack.core.MessagePack
import org.msgpack.value.ImmutableValue

fun ByteArray.unpack(): ImmutableValue = MessagePack.newDefaultUnpacker(this).unpackValue()

fun ByteArray.asInt(): Int = unpack().asIntegerValue().asInt()

fun ByteArray.asString(): String = unpack().asStringValue().asString()

fun createDirectExecutor(): ExecutorService =
  object : AbstractExecutorService() {
    override fun execute(command: Runnable) {
      command.run()
    }

    override fun shutdown() {}

    override fun shutdownNow(): MutableList<Runnable> {
      throw UnsupportedOperationException("shutdownNow")
    }

    override fun isShutdown(): Boolean {
      throw UnsupportedOperationException("isShutdown")
    }

    override fun isTerminated(): Boolean {
      throw UnsupportedOperationException("isTerminated")
    }

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
      throw UnsupportedOperationException("awaitTermination")
    }
  }
