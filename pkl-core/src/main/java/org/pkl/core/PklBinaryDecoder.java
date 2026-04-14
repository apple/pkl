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
package org.pkl.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.util.CollectionUtils;
import org.pkl.core.util.pklbinary.AbstractPklBinaryDecoder;

/**
 * A decoder/parser for <code>pkl-binary</code>.
 *
 * <p>For how pkl-binary turns Java, see {@link Value}.
 */
public class PklBinaryDecoder extends AbstractPklBinaryDecoder {

  private PklBinaryDecoder(MessageUnpacker unpacker) {
    super(unpacker);
  }

  /** Decode a value from the supplied byte array. */
  public static Object decode(byte[] bytes) {
    return new PklBinaryDecoder(MessagePack.newDefaultUnpacker(bytes)).decode();
  }

  /** Decode a value from the supplied {@link InputStream}. */
  public static Object decode(InputStream inputStream) {
    return new PklBinaryDecoder(MessagePack.newDefaultUnpacker(inputStream)).decode();
  }

  @Override
  protected RuntimeException doFail(Exception cause, long offset, List<String> path) {
    return new RuntimeException(
        String.format(
            "Exception while decoding binary data at offset %d, path [%s]",
            offset, String.join(", ", path)),
        cause);
  }

  @Override
  protected RuntimeException doIOFail(IOException cause) {
    return new UncheckedIOException("IO exception during decoding", cause);
  }

  @Override
  protected Object doDecodeNull() {
    return PNull.getInstance();
  }

  @Override
  protected Object doDecodeDuration(double value, DurationUnit unit) {
    return new Duration(value, unit);
  }

  @Override
  protected Object doDecodeObject(
      String className, URI moduleUri, DecodeIterator<DecodedObjectMember> iter) {
    var properties = CollectionUtils.<String, Object>newLinkedHashMap(iter.getSize());
    while (iter.hasNext()) {
      var member = iter.next();
      properties.put(member.key().toString(), member.value());
    }

    if (moduleUri.equals(PClassInfo.pklBaseUri)) {
      // dynamic
      if (className.equals(BaseModule.getDynamicClass().getDisplayName())) {
        return new PObject(PClassInfo.Dynamic, properties);
      }

      // pkl:base typed
      if (!className.equals(BaseModule.getModule().getVmClass().getDisplayName())) {
        return new PObject(PClassInfo.get("pkl.base", className, moduleUri), properties);
      }
      // fall through to module case
    }

    // module
    var hashIndex = className.lastIndexOf("#");
    if (hashIndex < 0) {
      return new PModule(
          moduleUri, className, PClassInfo.get(className, "ModuleClass", moduleUri), properties);
    }

    // non-pkl:base class
    return new PObject(
        PClassInfo.get(
            className.substring(0, hashIndex), className.substring(hashIndex + 1), moduleUri),
        properties);
  }

  @Override
  protected Object doDecodeMap(MapDecodeIterator iter) {
    var map = CollectionUtils.newLinkedHashMap(iter.getSize());
    while (iter.hasNext()) {
      var entry = iter.next();
      map.put(entry.getFirst(), entry.getSecond());
    }
    return map;
  }

  @Override
  protected Object doDecodeMapping(MapDecodeIterator iter) {
    return doDecodeMap(iter); // same exported result!
  }

  @Override
  protected Object doDecodeList(CollectionDecodeIterator iter) {
    var listing = new ArrayList<>(iter.getSize());
    while (iter.hasNext()) {
      listing.add(iter.next());
    }
    return listing;
  }

  @Override
  protected Object doDecodeListing(CollectionDecodeIterator iter) {
    return doDecodeList(iter); // same exported result!
  }

  @Override
  protected Object doDecodeSet(CollectionDecodeIterator iter) {
    var set = CollectionUtils.newLinkedHashSet(iter.getSize());
    while (iter.hasNext()) {
      set.add(iter.next());
    }
    return set;
  }

  @Override
  protected Object doDecodeDataSize(double value, DataSizeUnit unit) {
    return new DataSize(value, unit);
  }

  @Override
  protected Object doDecodePair(Object first, Object second) {
    return new Pair<>(first, second);
  }

  @Override
  protected Object doDecodeIntSeq(long start, long end, long step) {
    throw new DecodeException("Cannot decode IntSeq value");
  }

  @Override
  protected Object doDecodeRegex(Pattern pattern) {
    return pattern;
  }

  @Override
  protected Object doDecodeClass(String qualifiedName, URI moduleUri) {
    throw new DecodeException("Cannot decode Class value");
  }

  @Override
  protected Object doDecodeTypeAlias(String qualifiedName, URI moduleUri) {
    throw new DecodeException("Cannot decode TypeAlias value");
  }

  @Override
  protected Object doDecodeBytes(byte[] bytes) {
    return bytes;
  }
}
