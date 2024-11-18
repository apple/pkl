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
package org.pkl.core.util.json;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import org.pkl.core.Version;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Nullable;

/**
 * Parser for JSON.
 *
 * <p>JSON types are paresd into the following Java types:
 *
 * <table>
 *   <thead>
 *     <td>JSON type</td>
 *     <td>Java type</td>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>object</td>
 *       <td>{@link JsObject}</td>
 *     </tr>
 *     <tr>
 *       <td>array</td>
 *       <td>{@link JsArray}</td>
 *     </tr>
 *     <tr>
 *       <td>number</td>
 *       <td>{@link Integer} or {@link Double}</td>
 *     </tr>
 *     <tr>
 *       <td>boolean</td>
 *       <td>{@link Boolean}</td>
 *     </tr>
 *     <tr>
 *       <td>null</td>
 *       <td>{@code null}</td>
 *     </tr>
 *     <tr>
 *       <td>string</td>
 *       <td>{@link String}</td>
 *     </tr>
 *   </tbody>
 * </table>
 */
@SuppressWarnings("unused")
public final class Json {
  @FunctionalInterface
  public interface Mapper<R> {
    R apply(Object arg) throws Exception;
  }

  /** Parses {@code input}, expecting it to be a JSON object. */
  public static JsObject parseObject(String input) throws JsonParseException {
    var handler = new Handler();
    var parser = new JsonParser(handler);
    try {
      parser.parse(input);
    } catch (ParseException e) {
      throw new MalformedJsonException(e, input);
    }
    var ret = handler.value;
    if (!(ret instanceof JsObject jsObject)) {
      throw new FormatException("object", ret.getClass());
    }
    return jsObject;
  }

  public abstract static class JsonParseException extends Exception {}

  public static class MalformedJsonException extends JsonParseException {

    private final String message;

    public MalformedJsonException(ParseException e, String inputString) {
      this.message = ErrorMessages.create("malformedJson", e.getMessage(), inputString);
      initCause(e);
    }

    @Override
    public String getMessage() {
      return message;
    }
  }

  public static class FormatException extends JsonParseException {
    private final String message;

    public FormatException(String expected, Class<?> actual) {
      this.message = ErrorMessages.create("badJsonFormat1", expected, actual.getSimpleName());
    }

    public FormatException(String key, String expected, Class<?> actual) {
      this.message = ErrorMessages.create("badJsonFormat2", expected, key, actual);
    }

    @Override
    public String getMessage() {
      return message;
    }
  }

  public static class MappingException extends JsonParseException {

    private final String message;

    public MappingException(String key, Exception e) {
      this.message = ErrorMessages.create("badJsonInvalidMapping", key, e.getMessage());
      this.initCause(e);
    }

    @Override
    public String getMessage() {
      return message;
    }
  }

  public static class MissingFieldException extends JsonParseException {

    private final Object object;
    private final String key;

    public MissingFieldException(Object object, String key) {
      this.object = object;
      this.key = key;
    }

    @Override
    public String getMessage() {
      return ErrorMessages.create("badJsonMissingField", key, object);
    }
  }

  private static class Handler extends JsonHandler<JsArray, JsObject> {
    Object value;

    @Override
    public void endString(String string) {
      value = string;
    }

    @Override
    public void endNull() {
      value = null;
    }

    @Override
    public void endBoolean(boolean value) {
      this.value = value;
    }

    @Override
    public void endNumber(String string) {
      try {
        value = Integer.valueOf(string);
      } catch (NumberFormatException e) {
        value = Double.valueOf(string);
      }
    }

    @Override
    public JsArray startArray() {
      return new JsArray();
    }

    @Override
    public void endArrayValue(JsArray array) {
      array.add(value);
    }

    @Override
    public void endArray(JsArray array) {
      value = array;
    }

    @Override
    public JsObject startObject() {
      return new JsObject();
    }

    @Override
    public void endObjectValue(JsObject object, String name) {
      object.put(name, value);
    }

    @Override
    public void endObject(JsObject object) {
      value = object;
    }
  }

  public static class JsObject implements Map<String, Object> {
    private final Map<String, Object> delegate;

    public JsObject() {
      this.delegate = new HashMap<>();
    }

    public JsObject(int size) {
      this.delegate = new HashMap<>(size);
    }

    public <T> T get(String key, Mapper<T> mapper) throws JsonParseException {
      var ret = get(key);
      if (ret == null) {
        throw new MissingFieldException(this, key);
      }
      try {
        return mapper.apply(ret);
      } catch (Exception e) {
        throw new MappingException(key, e);
      }
    }

    public <T> @Nullable T getNullable(String key, Mapper<T> mapper) throws JsonParseException {
      var ret = get(key);
      if (ret == null) {
        return null;
      }
      return get(key, mapper);
    }

    public boolean getBoolean(String key) throws JsonParseException {
      var ret = get(key);
      if (ret == null) {
        throw new MissingFieldException(this, key);
      }
      if (!(ret instanceof Boolean b)) {
        throw new FormatException(key, "boolean", ret.getClass());
      }
      return b;
    }

    public int getInt(String key) throws JsonParseException {
      var ret = get(key);
      if (ret == null) {
        throw new MissingFieldException(this, key);
      }
      if (!(ret instanceof Integer i)) {
        throw new FormatException(key, "integer", ret.getClass());
      }
      return i;
    }

    public String getString(String key) throws JsonParseException {
      var ret = getStringOrNull(key);
      if (ret == null) {
        throw new MissingFieldException(this, key);
      }
      return ret;
    }

    public @Nullable String getStringOrNull(String key) throws JsonParseException {
      var ret = get(key);
      if (ret == null) {
        return null;
      }
      if (!(ret instanceof String string)) {
        throw new FormatException(key, "string", ret.getClass());
      }
      return string;
    }

    public JsObject getObject(String key) throws JsonParseException {
      var ret = get(key);
      if (ret == null) {
        throw new MissingFieldException(this, key);
      }
      if (!(ret instanceof JsObject jsObject)) {
        throw new FormatException(key, "object", ret.getClass());
      }
      return jsObject;
    }

    public JsArray getArray(String key) throws JsonParseException {
      var ret = get(key);
      if (ret == null) {
        throw new MissingFieldException(this, key);
      }
      if (!(ret instanceof JsArray jsArray)) {
        throw new FormatException(key, "array", ret.getClass());
      }
      return jsArray;
    }

    public Version getVersion(String key) throws JsonParseException {
      var versionStr = getString(key);
      try {
        return Version.parse(versionStr);
      } catch (IllegalArgumentException e) {
        throw new MappingException(key, e);
      }
    }

    public URI getURI(String key) throws JsonParseException {
      var result = getURIOrNull(key);
      if (result == null) {
        throw new MissingFieldException(this, key);
      }
      return result;
    }

    public @Nullable URI getURIOrNull(String key) throws JsonParseException {
      var uriStr = getStringOrNull(key);
      if (uriStr == null) {
        return null;
      }
      try {
        return new URI(uriStr);
      } catch (URISyntaxException e) {
        throw new MappingException(key, e);
      }
    }

    @Override
    public int size() {
      return delegate.size();
    }

    @Override
    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
      return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
      return delegate.containsValue(value);
    }

    @Override
    public @Nullable Object get(Object key) {
      return delegate.get(key);
    }

    @Override
    public Object put(String key, Object value) {
      return delegate.put(key, value);
    }

    @Override
    public Object remove(Object key) {
      return delegate.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
      delegate.putAll(m);
    }

    @Override
    public void clear() {
      delegate.clear();
    }

    @Override
    public Set<String> keySet() {
      return delegate.keySet();
    }

    @Override
    public Collection<Object> values() {
      return delegate.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
      return delegate.entrySet();
    }

    @Override
    public String toString() {
      return delegate.toString();
    }
  }

  public static class JsArray implements List<Object> {
    private final List<Object> delegate;

    public JsArray() {
      this.delegate = new ArrayList<>();
    }

    public JsArray(int size) {
      this.delegate = new ArrayList<>(size);
    }

    @Override
    public int size() {
      return delegate.size();
    }

    @Override
    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
      return delegate.contains(o);
    }

    @Override
    public Iterator<Object> iterator() {
      return delegate.iterator();
    }

    @Override
    public Object[] toArray() {
      return delegate.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
      return delegate.toArray(a);
    }

    @Override
    public boolean add(Object o) {
      return delegate.add(o);
    }

    @Override
    public boolean remove(Object o) {
      return delegate.remove(o);
    }

    @SuppressWarnings("SlowListContainsAll")
    @Override
    public boolean containsAll(Collection<?> c) {
      return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<?> c) {
      return delegate.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<?> c) {
      return delegate.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
      return delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      return delegate.retainAll(c);
    }

    @Override
    public void clear() {
      delegate.clear();
    }

    @Override
    public @Nullable Object get(int index) {
      return delegate.get(index);
    }

    @Override
    public Object set(int index, Object element) {
      return delegate.set(index, element);
    }

    @Override
    public void add(int index, Object element) {
      delegate.add(index, element);
    }

    @Override
    public Object remove(int index) {
      return delegate.remove(index);
    }

    @Override
    public int indexOf(Object o) {
      return delegate.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
      return delegate.lastIndexOf(o);
    }

    @Override
    public ListIterator<Object> listIterator() {
      return delegate.listIterator();
    }

    @Override
    public ListIterator<Object> listIterator(int index) {
      return delegate.listIterator(index);
    }

    @Override
    public List<Object> subList(int fromIndex, int toIndex) {
      return delegate.subList(fromIndex, toIndex);
    }

    @Override
    public String toString() {
      return delegate.toString();
    }
  }
}
