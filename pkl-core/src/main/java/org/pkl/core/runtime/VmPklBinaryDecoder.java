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
package org.pkl.core.runtime;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;
import org.graalvm.collections.EconomicMap;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.pkl.core.AbstractPklBinaryDecoder;
import org.pkl.core.DataSizeUnit;
import org.pkl.core.DurationUnit;
import org.pkl.core.PClassInfo;
import org.pkl.core.PklBinaryEncoding;
import org.pkl.core.ast.member.ObjectMember;

/**
 * A decoder/parser for the <a
 * href="https://pkl-lang.org/main/current/bindings-specification/binary-encoding.html"><code>
 * pkl-binary</code></a> encoding. Returns "Vm" objects that can be used by the Pkl runtime.
 */
public class VmPklBinaryDecoder extends AbstractPklBinaryDecoder {
  private final Importer importer;

  /**
   * This interface provides callbacks for callers to implement to provide the implementation for
   * importing Pkl types.
   */
  public interface Importer {
    /**
     * Called by the decoder when a Pkl class should be imported. This happens when decoding {@link
     * VmClass} or {@link VmTyped} values.
     *
     * @param name is the qualified name of the class or module
     * @param moduleUri is the URI of the module or the class's enclosing module
     * @return The imported class
     */
    VmClass importClass(String name, URI moduleUri);

    /**
     * Called by the decoder when a Pkl class should be imported. This happens when decoding {@link
     * VmTypeAlias} values.
     *
     * @param name is the qualified name of the typealias
     * @param moduleUri is the URI of the typealias's enclosing module
     * @return The import typealias
     */
    VmTypeAlias importTypeAlias(String name, URI moduleUri);
  }

  private VmPklBinaryDecoder(MessageUnpacker unpacker, Importer importer) {
    super(unpacker);
    this.importer = importer;
  }

  /**
   * Decode a value from the supplied byte array.
   *
   * @return the encoded value
   */
  public static Object decode(byte[] bytes, Importer importer) {
    return decode(MessagePack.newDefaultUnpacker(bytes), importer);
  }

  /**
   * Decode a value from the supplied {@link ByteArrayInputStream}.
   *
   * @return the encoded value
   */
  public static Object decode(ByteArrayInputStream inputStream, Importer importer) {
    return decode(MessagePack.newDefaultUnpacker(inputStream), importer);
  }

  /**
   * Decode a value from the supplied {@link MessageUnpacker}.
   *
   * @return the encoded value
   */
  public static Object decode(MessageUnpacker unpacker, Importer importer) {
    return new VmPklBinaryDecoder(unpacker, importer).decode();
  }

  @Override
  protected RuntimeException doFail(Exception cause, long offset, List<String> path) {
    return new VmExceptionBuilder()
        .evalError("errorDecodingFromBinary", offset, String.join(" ", path))
        .withCause(cause)
        .build();
  }

  @Override
  protected RuntimeException doIOFail(Exception cause) {
    return new VmExceptionBuilder().evalError("ioErrorDecodingFromBinary").withCause(cause).build();
  }

  @Override
  protected Object doDecodeNull() {
    return VmNull.withoutDefault();
  }

  @Override
  protected Object doDecodeObject(
      String className, URI moduleUri, DecodeIterator<DecodedObjectMember> iter)
      throws IOException {
    EconomicMap<Object, ObjectMember> members = EconomicMap.create(iter.getSize());
    while (iter.hasNext()) {
      var member = iter.next();
      switch (member.type()) {
        case PklBinaryEncoding.CODE_PROPERTY -> {
          var name = Identifier.get((String) member.key());
          members.put(name, VmUtils.createSyntheticObjectProperty(name, "", member.value()));
        }
        case PklBinaryEncoding.CODE_ENTRY ->
            members.put(member.key(), VmUtils.createSyntheticObjectEntry("", member.value()));
        case PklBinaryEncoding.CODE_ELEMENT ->
            members.put(member.key(), VmUtils.createSyntheticObjectElement("", member.value()));
        default -> throw new DecodeException("Unrecognized member code %x", member.type());
      }
    }

    // dynamic
    if (className.equals(BaseModule.getDynamicClass().getDisplayName())
        && moduleUri.equals(PClassInfo.pklBaseUri)) {
      return new VmDynamic(
          VmUtils.createEmptyMaterializedFrame(), VmDynamic.empty(), members, iter.getSize());
    }

    // typed
    var clazz = importer.importClass(className, moduleUri);
    return new VmTyped(
        VmUtils.createEmptyMaterializedFrame(), clazz.getPrototype(), clazz, members);
  }

  @Override
  protected Object doDecodeMap(MapDecodeIterator iter) throws IOException {
    var map = VmMap.builder();
    while (iter.hasNext()) {
      var entry = iter.next();
      map.add(entry.getKey(), entry.getValue());
    }
    return map.build();
  }

  @Override
  protected Object doDecodeMapping(MapDecodeIterator iter) throws IOException {
    var mapping = new VmObjectBuilder(iter.getSize());
    while (iter.hasNext()) {
      var entry = iter.next();
      mapping.addEntry(entry.getKey(), entry.getValue());
    }
    return mapping.toMapping();
  }

  @Override
  protected Object doDecodeList(CollectionDecodeIterator iter) throws IOException {
    var list = VmList.EMPTY.builder();
    while (iter.hasNext()) {
      list.add(iter.next());
    }
    return list.build();
  }

  @Override
  protected Object doDecodeListing(CollectionDecodeIterator iter) throws IOException {
    var listing = new VmObjectBuilder(iter.getSize());
    while (iter.hasNext()) {
      listing.addElement(iter.next());
    }
    return listing.toListing();
  }

  @Override
  protected Object doDecodeSet(CollectionDecodeIterator iter) throws IOException {
    var set = VmSet.EMPTY.builder();
    while (iter.hasNext()) {
      set.add(iter.next());
    }
    return set.build();
  }

  @Override
  protected Object doDecodeDuration(double value, DurationUnit unit) {
    return new VmDuration(value, unit);
  }

  @Override
  protected Object doDecodeDataSize(double value, DataSizeUnit unit) {
    return new VmDataSize(value, unit);
  }

  @Override
  protected Object doDecodePair(Object first, Object second) {
    return new VmPair(first, second);
  }

  @Override
  protected Object doDecodeIntSeq(long start, long end, long step) {
    return new VmIntSeq(start, end, step);
  }

  @Override
  protected Object doDecodeRegex(Pattern pattern) {
    return new VmRegex(pattern);
  }

  @Override
  protected Object doDecodeClass(String qualifiedName, URI moduleUri) {
    return importer.importClass(qualifiedName, moduleUri);
  }

  @Override
  protected Object doDecodeTypeAlias(String qualifiedName, URI moduleUri) {
    return importer.importTypeAlias(qualifiedName, moduleUri);
  }

  @Override
  protected Object doDecodeBytes(byte[] bytes) {
    return new VmBytes(bytes);
  }
}
