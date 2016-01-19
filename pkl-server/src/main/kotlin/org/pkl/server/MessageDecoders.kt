/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.io.InputStream
import org.msgpack.core.MessagePack
import org.msgpack.core.MessageUnpacker

/** Factory methods for creating [MessageDecoder]s. */
internal object MessageDecoders {
  fun from(stream: InputStream): MessageDecoder =
    MessagePackDecoder(MessagePack.newDefaultUnpacker(stream))

  fun from(unpacker: MessageUnpacker): MessageDecoder = MessagePackDecoder(unpacker)

  fun from(array: ByteArray): MessageDecoder =
    MessagePackDecoder(MessagePack.newDefaultUnpacker(array))
}
