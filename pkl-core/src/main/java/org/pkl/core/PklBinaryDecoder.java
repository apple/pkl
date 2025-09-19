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
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Formatter;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.graalvm.collections.EconomicMap;
import org.msgpack.core.MessagePackException;
import org.msgpack.core.MessageUnpacker;
import org.pkl.core.ast.member.ObjectMember;
import org.pkl.core.runtime.BaseModule;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmBytes;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmDataSize;
import org.pkl.core.runtime.VmDuration;
import org.pkl.core.runtime.VmDynamic;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.runtime.VmIntSeq;
import org.pkl.core.runtime.VmList;
import org.pkl.core.runtime.VmListing;
import org.pkl.core.runtime.VmMap;
import org.pkl.core.runtime.VmMapping;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmObject;
import org.pkl.core.runtime.VmObjectBuilder;
import org.pkl.core.runtime.VmPair;
import org.pkl.core.runtime.VmRegex;
import org.pkl.core.runtime.VmSet;
import org.pkl.core.runtime.VmTypeAlias;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.LateInit;

/**
 * A decoder/parser for the <a
 * href="https://pkl-lang.org/main/current/bindings-specification/binary-encoding.html"><code>
 * pkl-binary</code></a> encoding.
 */
public class PklBinaryDecoder {
  private final MessageUnpacker unpacker;
  private final Importer importer;
  @LateInit protected Deque<Object> currPath;

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

  public PklBinaryDecoder(MessageUnpacker unpacker, Importer importer) {
    this.unpacker = unpacker;
    this.importer = importer;
  }

  private static class DecodeException extends RuntimeException {
    DecodeException(String msg, Object... args) {
      super(new Formatter().format(msg, args).toString());
    }
  }

  /**
   * Decode a value from the supplied {@link MessageUnpacker}
   *
   * @return the encoded value
   */
  public Object decode() {
    currPath = new ArrayDeque<>();
    try {
      return doDecode();
    } catch (IOException e) {
      throw new VmExceptionBuilder().evalError("ioErrorDecodingFromBinary").withCause(e).build();
    } catch (MessagePackException | DecodeException e) {
      var path = currPath.stream().map(Object::toString).collect(Collectors.toList());
      Collections.reverse(path);
      throw new VmExceptionBuilder()
          .evalError(
              "errorDecodingFromBinary", unpacker.getTotalReadBytes(), String.join(" ", path))
          .withCause(e)
          .build();
    }
  }

  private Object doDecode() throws IOException {
    if (!unpacker.hasNext()) {
      throw new DecodeException("Unexpected EOF");
    }

    return switch (unpacker.getNextFormat()) {
      // primitives
      case NIL -> {
        unpacker.unpackNil();
        yield VmNull.withoutDefault();
      }
      case STR8, STR16, STR32, FIXSTR -> unpacker.unpackString();
      case UINT8, UINT16, UINT32, UINT64, INT8, INT16, INT32, INT64, POSFIXINT, NEGFIXINT ->
          unpacker.unpackLong();
      case BOOLEAN -> unpacker.unpackBoolean();
      case FLOAT32, FLOAT64 -> unpacker.unpackDouble();

      // non-primitive
      case ARRAY16, ARRAY32, FIXARRAY -> decodeNonPrimitive();

      // things we should never see outside a non-primitive
      case BIN8, BIN16, BIN32 -> throw new DecodeException("Unexpected msgpack bin value");
      case MAP16, MAP32, FIXMAP -> throw new DecodeException("Unexpected msgpack map value");
      case EXT8, EXT16, EXT32, FIXEXT1, FIXEXT2, FIXEXT4, FIXEXT8, FIXEXT16 ->
          throw new DecodeException("Unexpected msgpack ext value");
      case NEVER_USED ->
          throw PklBugException
              .unreachableCode(); // getNextFormat throws for this but switch needs to be exhaustive
    };
  }

  private Object decodeNonPrimitive() throws IOException {
    var len = unpacker.unpackArrayHeader();
    if (len < 1) {
      throw new DecodeException("Unexpected empty object array value");
    }

    var code = unpacker.unpackInt();
    return switch (code) {
      case PklBinaryEncoder.CODE_OBJECT -> decodeObject(len);
      case PklBinaryEncoder.CODE_MAP -> decodeMap(len);
      case PklBinaryEncoder.CODE_MAPPING -> decodeMapping(len);
      case PklBinaryEncoder.CODE_LIST -> decodeList(len);
      case PklBinaryEncoder.CODE_LISTING -> decodeListing(len);
      case PklBinaryEncoder.CODE_SET -> decodeSet(len);
      case PklBinaryEncoder.CODE_DURATION -> decodeDuration(len);
      case PklBinaryEncoder.CODE_DATASIZE -> decodeDataSize(len);
      case PklBinaryEncoder.CODE_PAIR -> decodePair(len);
      case PklBinaryEncoder.CODE_INTSEQ -> decodeIntSeq(len);
      case PklBinaryEncoder.CODE_REGEX -> decodeRegex(len);
      case PklBinaryEncoder.CODE_CLASS -> decodeClass(len);
      case PklBinaryEncoder.CODE_TYPEALIAS -> decodeTypeAlias(len);
      case PklBinaryEncoder.CODE_FUNCTION ->
          throw new DecodeException("Cannot decode values of type Function");
      case PklBinaryEncoder.CODE_BYTES -> decodeBytes(len);
      default -> throw new DecodeException("Unrecognized object code %x", code);
    };
  }

  private VmObject decodeObject(int len) throws IOException {
    assert len > 3;
    currPath.push("object");
    var objClassName = unpacker.unpackString();
    var objModuleUri = URI.create(unpacker.unpackString());
    var objectLen = unpacker.unpackArrayHeader();
    EconomicMap<Object, ObjectMember> members = EconomicMap.create();
    for (var i = 0; i < objectLen; i++) {
      currPath.push(i);
      var memberLen = unpacker.unpackArrayHeader();
      if (memberLen != 3) {
        throw new DecodeException("Expected 3 fields in object member, found %d", memberLen);
      }
      var memberCode = unpacker.unpackInt();
      switch (memberCode) {
        case PklBinaryEncoder.CODE_PROPERTY -> {
          var propertyName = Identifier.get(unpacker.unpackString());
          currPath.push(propertyName);
          var propertyValue = doDecode();
          members.put(
              propertyName, VmUtils.createSyntheticObjectProperty(propertyName, "", propertyValue));
        }
        case PklBinaryEncoder.CODE_ENTRY -> {
          var entryKey = doDecode();
          currPath.push(entryKey);
          var entryValue = doDecode();
          members.put(entryKey, VmUtils.createSyntheticObjectEntry("", entryValue));
        }
        case PklBinaryEncoder.CODE_ELEMENT -> {
          var elementIndex = unpacker.unpackLong();
          currPath.push(elementIndex);
          var elementValue = doDecode();
          members.put(elementIndex, VmUtils.createSyntheticObjectElement("", elementValue));
        }
        default -> throw new DecodeException("Unrecognized member code %x", memberCode);
      }
      currPath.pop();
      currPath.pop();
    }

    unpacker.skipValue(len - 4);
    currPath.pop();
    if (objClassName.equals(BaseModule.getDynamicClass().getDisplayName())
        && objModuleUri.equals(BaseModule.getModule().getModuleInfo().getModuleKey().getUri())) {
      return new VmDynamic(
          VmUtils.createEmptyMaterializedFrame(), VmDynamic.empty(), members, objectLen);
    }

    var clazz = importer.importClass(objClassName, objModuleUri);
    return new VmTyped(
        VmUtils.createEmptyMaterializedFrame(), clazz.getPrototype(), clazz, members);
  }

  private VmMap decodeMap(int len) throws IOException {
    assert len > 1;
    currPath.push("map");
    var mapLen = unpacker.unpackMapHeader();
    unpacker.skipValue(len - 2);
    var map = VmMap.builder();
    for (var i = 0; i < mapLen; i++) {
      currPath.push(i);
      var key = doDecode();
      currPath.push(key);
      var val = doDecode();
      currPath.pop();
      currPath.pop();
      map.add(key, val);
    }
    unpacker.skipValue(len - 2);
    currPath.pop();
    return map.build();
  }

  private VmMapping decodeMapping(int len) throws IOException {
    assert len > 1;
    currPath.push("mapping");
    var mappingLen = unpacker.unpackMapHeader();
    var mapping = new VmObjectBuilder(mappingLen);
    for (int i = 0; i < mappingLen; i++) {
      currPath.push(i);
      var key = doDecode();
      currPath.push(key);
      var val = doDecode();
      currPath.pop();
      currPath.pop();
      mapping.addEntry(key, val);
    }
    unpacker.skipValue(len - 2);
    currPath.pop();
    return mapping.toMapping();
  }

  private VmList decodeList(int len) throws IOException {
    assert len > 1;
    currPath.push("list");
    var listLen = unpacker.unpackArrayHeader();
    var list = VmList.EMPTY.builder();
    for (var i = 0; i < listLen; i++) {
      currPath.push(i);
      list.add(doDecode());
      currPath.pop();
    }
    unpacker.skipValue(len - 2);
    currPath.pop();
    return list.build();
  }

  private VmListing decodeListing(int len) throws IOException {
    assert len > 1;
    currPath.push("listing");
    var listingLen = unpacker.unpackArrayHeader();
    var listing = new VmObjectBuilder(listingLen);
    for (int i = 0; i < listingLen; i++) {
      currPath.push(i);
      listing.addElement(doDecode());
      currPath.pop();
    }
    unpacker.skipValue(len - 2);
    currPath.pop();
    return listing.toListing();
  }

  private VmSet decodeSet(int len) throws IOException {
    assert len > 1;
    currPath.push("set");
    var setLen = unpacker.unpackArrayHeader();
    var set = VmSet.EMPTY.builder();
    for (var i = 0; i < setLen; i++) {
      currPath.push(i);
      set.add(doDecode());
      currPath.pop();
    }
    unpacker.skipValue(len - 2);
    currPath.pop();
    return set.build();
  }

  private VmDuration decodeDuration(int len) throws IOException {
    assert len > 2;
    currPath.push("duration");
    var durationValue = unpacker.unpackDouble();
    var rawDurationUnit = unpacker.unpackString();
    var durationUnit = DurationUnit.parse(rawDurationUnit);
    if (durationUnit == null) {
      throw new DecodeException("Invalid Duration unit `%s`", rawDurationUnit);
    }
    unpacker.skipValue(len - 3);
    currPath.pop();
    return new VmDuration(durationValue, durationUnit);
  }

  private VmDataSize decodeDataSize(int len) throws IOException {
    assert len > 2;
    currPath.push("datasize");
    var dataSizeValue = unpacker.unpackDouble();
    var rawDataSizeUnit = unpacker.unpackString();
    var dataSizeUnit = DataSizeUnit.parse(rawDataSizeUnit);
    if (dataSizeUnit == null) {
      throw new DecodeException("Invalid DataSize unit `%s`", rawDataSizeUnit);
    }
    unpacker.skipValue(len - 3);
    currPath.pop();
    return new VmDataSize(dataSizeValue, dataSizeUnit);
  }

  private VmPair decodePair(int len) throws IOException {
    assert len > 2;
    currPath.push("pair");
    currPath.push("first");
    var first = doDecode();
    currPath.pop();
    currPath.push("second");
    var second = doDecode();
    currPath.pop();
    unpacker.skipValue(len - 3);
    currPath.pop();
    return new VmPair(first, second);
  }

  private VmIntSeq decodeIntSeq(int len) throws IOException {
    assert len > 3;
    currPath.push("intseq");
    var start = unpacker.unpackLong();
    var end = unpacker.unpackLong();
    var step = unpacker.unpackLong();
    unpacker.skipValue(len - 4);
    currPath.pop();
    return new VmIntSeq(start, end, step);
  }

  private VmRegex decodeRegex(int len) throws IOException {
    assert len > 1;
    currPath.push("regex");
    var pattern = unpacker.unpackString();
    unpacker.skipValue(len - 2);
    currPath.pop();
    return new VmRegex(Pattern.compile(pattern));
  }

  private VmClass decodeClass(int len) throws IOException {
    assert len > 2;
    currPath.push("class");
    var className = unpacker.unpackString();
    var classModuleUri = URI.create(unpacker.unpackString());
    unpacker.skipValue(len - 3);
    currPath.pop();
    return importer.importClass(className, classModuleUri);
  }

  private VmTypeAlias decodeTypeAlias(int len) throws IOException {
    assert len > 2;
    currPath.push("typealias");
    var typeAliasName = unpacker.unpackString();
    var typeAliasModuleUri = URI.create(unpacker.unpackString());
    unpacker.skipValue(len - 3);
    currPath.pop();
    return importer.importTypeAlias(typeAliasName, typeAliasModuleUri);
  }

  private VmBytes decodeBytes(int len) throws IOException {
    assert len > 1;
    currPath.push("bytes");
    var bytesLen = unpacker.unpackBinaryHeader();
    var bytes = unpacker.readPayload(bytesLen);
    unpacker.skipValue(len - 2);
    currPath.pop();
    return new VmBytes(bytes);
  }
}
