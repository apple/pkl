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
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Formatter;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.msgpack.core.MessagePackException;
import org.msgpack.core.MessageUnpacker;
import org.pkl.core.util.LateInit;

/**
 * Base class for implementing a decoder/parser for the <a
 * href="https://pkl-lang.org/main/current/bindings-specification/binary-encoding.html"><code>
 * pkl-binary</code></a> encoding.
 */
public abstract class AbstractPklBinaryDecoder {
  private final MessageUnpacker unpacker;
  @LateInit protected Deque<Object> currPath;

  protected AbstractPklBinaryDecoder(MessageUnpacker unpacker) {
    this.unpacker = unpacker;
  }

  protected static class DecodeException extends RuntimeException {
    public DecodeException(String msg, Object... args) {
      super(new Formatter().format(msg, args).toString());
    }
  }

  protected final Object decode() {
    currPath = new ArrayDeque<>();
    try {
      return doDecode();
    } catch (IOException e) {
      throw doIOFail(e);
    } catch (MessagePackException | DecodeException e) {
      var path = currPath.stream().map(Object::toString).collect(Collectors.toList());
      Collections.reverse(path);
      throw doFail(e, unpacker.getTotalReadBytes(), path);
    }
  }

  protected record DecodedObjectMember(int type, Object key, Object value) {}

  protected abstract RuntimeException doFail(Exception cause, long offset, List<String> path);

  protected abstract RuntimeException doIOFail(Exception cause);

  protected abstract Object doDecodeNull();

  protected abstract Object doDecodeObject(
      String className, URI moduleUri, DecodeIterator<DecodedObjectMember> iter) throws IOException;

  protected abstract Object doDecodeMap(MapDecodeIterator iter) throws IOException;

  protected abstract Object doDecodeMapping(MapDecodeIterator iter) throws IOException;

  protected abstract Object doDecodeList(CollectionDecodeIterator iter) throws IOException;

  protected abstract Object doDecodeListing(CollectionDecodeIterator iter) throws IOException;

  protected abstract Object doDecodeSet(CollectionDecodeIterator iter) throws IOException;

  protected abstract Object doDecodeDuration(double value, DurationUnit unit);

  protected abstract Object doDecodeDataSize(double value, DataSizeUnit unit);

  protected abstract Object doDecodePair(Object first, Object second);

  protected abstract Object doDecodeIntSeq(long start, long end, long step);

  protected abstract Object doDecodeRegex(Pattern pattern);

  protected abstract Object doDecodeClass(String qualifiedName, URI moduleUri);

  protected abstract Object doDecodeTypeAlias(String qualifiedName, URI moduleUri);

  protected Object doDecodeFunction() {
    throw new DecodeException("Cannot decode values of type Function");
  }

  protected abstract Object doDecodeBytes(byte[] bytes);

  private Object doDecode() throws IOException {
    if (!unpacker.hasNext()) {
      throw new DecodeException("Unexpected EOF");
    }

    return switch (unpacker.getNextFormat()) {
      // primitives
      case NIL -> {
        unpacker.unpackNil();
        yield doDecodeNull();
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
      case PklBinaryEncoding.CODE_OBJECT -> decodeObject(len);
      case PklBinaryEncoding.CODE_MAP -> decodeMap(len);
      case PklBinaryEncoding.CODE_MAPPING -> decodeMapping(len);
      case PklBinaryEncoding.CODE_LIST -> decodeList(len);
      case PklBinaryEncoding.CODE_LISTING -> decodeListing(len);
      case PklBinaryEncoding.CODE_SET -> decodeSet(len);
      case PklBinaryEncoding.CODE_DURATION -> decodeDuration(len);
      case PklBinaryEncoding.CODE_DATASIZE -> decodeDataSize(len);
      case PklBinaryEncoding.CODE_PAIR -> decodePair(len);
      case PklBinaryEncoding.CODE_INTSEQ -> decodeIntSeq(len);
      case PklBinaryEncoding.CODE_REGEX -> decodeRegex(len);
      case PklBinaryEncoding.CODE_CLASS -> decodeClass(len);
      case PklBinaryEncoding.CODE_TYPEALIAS -> decodeTypeAlias(len);
      case PklBinaryEncoding.CODE_FUNCTION -> decodeFunction(len);
      case PklBinaryEncoding.CODE_BYTES -> decodeBytes(len);
      default -> throw new DecodeException("Unrecognized object code %x", code);
    };
  }

  private Object decodeObject(int len) throws IOException {
    assert len > 3;
    currPath.push("'object");

    var className = unpacker.unpackString();
    if (className.isBlank()) {
      throw new DecodeException("Unexpected blank object class name");
    }
    var classModuleUriString = unpacker.unpackString();
    if (classModuleUriString.isBlank()) {
      throw new DecodeException("Unexpected blank object module URI");
    }
    var classModuleUri = URI.create(classModuleUriString);

    var result =
        doDecodeObject(
            className, classModuleUri, new ObjectDecodeIterator(unpacker.unpackArrayHeader()));
    unpacker.skipValue(len - 4);
    currPath.pop();
    return result;
  }

  private Object decodeMap(int len) throws IOException {
    assert len > 1;
    currPath.push("'map");
    var result = doDecodeMap(new MapDecodeIterator(unpacker.unpackMapHeader()));
    unpacker.skipValue(len - 2);
    currPath.pop();
    return result;
  }

  private Object decodeMapping(int len) throws IOException {
    assert len > 1;
    currPath.push("'mapping");
    var result = doDecodeMapping(new MapDecodeIterator(unpacker.unpackMapHeader()));
    unpacker.skipValue(len - 2);
    currPath.pop();
    return result;
  }

  private Object decodeList(int len) throws IOException {
    assert len > 1;
    currPath.push("'list");
    var result = doDecodeList(new CollectionDecodeIterator(unpacker.unpackArrayHeader()));
    unpacker.skipValue(len - 2);
    currPath.pop();
    return result;
  }

  private Object decodeListing(int len) throws IOException {
    assert len > 1;
    currPath.push("'listing");
    var result = doDecodeListing(new CollectionDecodeIterator(unpacker.unpackArrayHeader()));
    unpacker.skipValue(len - 2);
    currPath.pop();
    return result;
  }

  private Object decodeSet(int len) throws IOException {
    assert len > 1;
    currPath.push("'set");
    var result = doDecodeSet(new CollectionDecodeIterator(unpacker.unpackArrayHeader()));
    currPath.pop();
    unpacker.skipValue(len - 2);
    return result;
  }

  private Object decodeDuration(int len) throws IOException {
    assert len > 2;
    currPath.push("'duration");
    var durationValue = unpacker.unpackDouble();
    var rawDurationUnit = unpacker.unpackString();
    var durationUnit = DurationUnit.parse(rawDurationUnit);
    if (durationUnit == null) {
      throw new DecodeException("Invalid Duration unit `%s`", rawDurationUnit);
    }
    var result = doDecodeDuration(durationValue, durationUnit);
    unpacker.skipValue(len - 3);
    currPath.pop();
    return result;
  }

  private Object decodeDataSize(int len) throws IOException {
    assert len > 2;
    currPath.push("'datasize");
    var dataSizeValue = unpacker.unpackDouble();
    var rawDataSizeUnit = unpacker.unpackString();
    var dataSizeUnit = DataSizeUnit.parse(rawDataSizeUnit);
    if (dataSizeUnit == null) {
      throw new DecodeException("Invalid DataSize unit `%s`", rawDataSizeUnit);
    }
    var result = doDecodeDataSize(dataSizeValue, dataSizeUnit);
    unpacker.skipValue(len - 3);
    currPath.pop();
    return result;
  }

  private Object decodePair(int len) throws IOException {
    assert len > 2;
    currPath.push("'pair");
    currPath.push("'first");
    var first = doDecode();
    currPath.pop();
    currPath.push("'second");
    var second = doDecode();
    currPath.pop();
    var result = doDecodePair(first, second);
    unpacker.skipValue(len - 3);
    currPath.pop();
    return result;
  }

  private Object decodeIntSeq(int len) throws IOException {
    assert len > 3;
    currPath.push("'intseq");
    var start = unpacker.unpackLong();
    var end = unpacker.unpackLong();
    var step = unpacker.unpackLong();
    var result = doDecodeIntSeq(start, end, step);
    unpacker.skipValue(len - 4);
    currPath.pop();
    return result;
  }

  private Object decodeRegex(int len) throws IOException {
    assert len > 1;
    currPath.push("'regex");
    var result = doDecodeRegex(Pattern.compile(unpacker.unpackString()));
    unpacker.skipValue(len - 2);
    currPath.pop();
    return result;
  }

  private Object decodeClass(int len) throws IOException {
    assert len > 2;
    currPath.push("'class");

    var name = unpacker.unpackString();
    if (name.isBlank()) {
      throw new DecodeException("Unexpected blank class name");
    }
    var moduleUriString = unpacker.unpackString();
    if (moduleUriString.isBlank()) {
      throw new DecodeException("Unexpected blank class module URI");
    }
    var moduleUri = URI.create(unpacker.unpackString());

    var result = doDecodeClass(name, moduleUri);
    unpacker.skipValue(len - 3);
    currPath.pop();
    return result;
  }

  private Object decodeTypeAlias(int len) throws IOException {
    assert len > 2;
    currPath.push("'typealias");

    var name = unpacker.unpackString();
    if (name.isBlank()) {
      throw new DecodeException("Unexpected blank typealias name");
    }
    var moduleUriString = unpacker.unpackString();
    if (moduleUriString.isBlank()) {
      throw new DecodeException("Unexpected blank typealias module URI");
    }
    var moduleUri = URI.create(unpacker.unpackString());

    var result = doDecodeTypeAlias(name, moduleUri);
    unpacker.skipValue(len - 3);
    currPath.pop();
    return result;
  }

  private Object decodeFunction(int len) throws IOException {
    assert len > 0;
    currPath.push("'function");
    var result = doDecodeFunction();
    unpacker.skipValue(len - 1);
    currPath.pop();
    return result;
  }

  private Object decodeBytes(int len) throws IOException {
    assert len > 1;
    currPath.push("'bytes");
    var result = doDecodeBytes(unpacker.readPayload(unpacker.unpackBinaryHeader()));
    unpacker.skipValue(len - 2);
    currPath.pop();
    return result;
  }

  // some silly iterator classes because next() needs to throw IOException

  protected abstract class DecodeIterator<T> {
    private final int size;
    private int idx = 0;

    DecodeIterator(int size) {
      this.size = size;
    }

    public int getSize() {
      return size;
    }

    public boolean hasNext() {
      return idx < size;
    }

    public T next() throws IOException {
      currPath.push(idx);
      var val = getNext();
      currPath.pop();
      idx++;
      return val;
    }

    abstract T getNext() throws IOException;
  }

  protected class ObjectDecodeIterator extends DecodeIterator<DecodedObjectMember> {
    ObjectDecodeIterator(int size) {
      super(size);
    }

    @Override
    DecodedObjectMember getNext() throws IOException {
      var memberLen = unpacker.unpackArrayHeader();
      if (memberLen != 3) {
        throw new DecodeException("Expected 3 fields in object member, found %d", memberLen);
      }
      var memberCode = unpacker.unpackInt();
      DecodedObjectMember member;
      switch (memberCode) {
        case PklBinaryEncoding.CODE_PROPERTY -> {
          var propertyName = unpacker.unpackString();
          currPath.push(propertyName);
          member =
              new DecodedObjectMember(PklBinaryEncoding.CODE_PROPERTY, propertyName, doDecode());
        }
        case PklBinaryEncoding.CODE_ENTRY -> {
          var entryKey = doDecode();
          currPath.push(entryKey);
          member = new DecodedObjectMember(PklBinaryEncoding.CODE_ENTRY, entryKey, doDecode());
        }
        case PklBinaryEncoding.CODE_ELEMENT -> {
          var elementIndex = unpacker.unpackLong();
          currPath.push(elementIndex);
          member =
              new DecodedObjectMember(PklBinaryEncoding.CODE_ELEMENT, elementIndex, doDecode());
        }
        default -> throw new DecodeException("Unrecognized member code %x", memberCode);
      }
      currPath.pop();
      return member;
    }
  }

  protected class CollectionDecodeIterator extends DecodeIterator<Object> {
    CollectionDecodeIterator(int size) {
      super(size);
    }

    @Override
    Object getNext() throws IOException {
      return doDecode();
    }
  }

  protected class MapDecodeIterator extends DecodeIterator<SimpleEntry<Object, Object>> {
    MapDecodeIterator(int size) {
      super(size);
    }

    @Override
    SimpleEntry<Object, Object> getNext() throws IOException {
      var key = doDecode();
      currPath.push(key);
      var val = doDecode();
      currPath.pop();
      return new SimpleEntry<>(key, val);
    }
  }
}
