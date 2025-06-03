/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.stdlib;

import java.util.*;
import org.pkl.core.runtime.*;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.Pair;

public final class PklConverter implements VmValueConverter<Object> {
  private final Map<VmClass, VmFunction> typeConverters;
  private final Pair<Object[], VmFunction>[] pathConverters;

  private final @Nullable VmFunction stringConverter;
  private final @Nullable VmFunction booleanConverter;
  private final @Nullable VmFunction intConverter;
  private final @Nullable VmFunction floatConverter;
  private final @Nullable VmFunction durationConverter;
  private final @Nullable VmFunction dataSizeConverter;
  private final @Nullable VmFunction bytesConverter;
  private final @Nullable VmFunction intSeqConverter;
  private final @Nullable VmFunction listConverter;
  private final @Nullable VmFunction setConverter;
  private final @Nullable VmFunction mapConverter;
  private final @Nullable VmFunction listingConverter;
  private final @Nullable VmFunction mappingConverter;
  private final @Nullable VmFunction dynamicConverter;
  private final @Nullable VmFunction pairConverter;
  private final @Nullable VmFunction regexConverter;
  private final @Nullable VmFunction nullConverter;
  private final @Nullable VmFunction classConverter;
  private final @Nullable VmFunction typeAliasConverter;

  public PklConverter(VmMapping converters) {
    // As of 0.18, `converters` is forced by the mapping type check,
    // but let's not rely on this implementation detail.
    converters.force(false, false);
    typeConverters = createTypeConverters(converters);
    pathConverters = createPathConverters(converters);

    stringConverter = typeConverters.get(BaseModule.getStringClass());
    booleanConverter = typeConverters.get(BaseModule.getBooleanClass());
    intConverter = typeConverters.get(BaseModule.getIntClass());
    floatConverter = typeConverters.get(BaseModule.getFloatClass());
    durationConverter = typeConverters.get(BaseModule.getDurationClass());
    dataSizeConverter = typeConverters.get(BaseModule.getDataSizeClass());
    bytesConverter = typeConverters.get(BaseModule.getBytesClass());
    intSeqConverter = typeConverters.get(BaseModule.getIntSeqClass());
    listConverter = typeConverters.get(BaseModule.getListClass());
    setConverter = typeConverters.get(BaseModule.getSetClass());
    mapConverter = typeConverters.get(BaseModule.getMapClass());
    listingConverter = typeConverters.get(BaseModule.getListingClass());
    mappingConverter = typeConverters.get(BaseModule.getMappingClass());
    dynamicConverter = typeConverters.get(BaseModule.getDynamicClass());
    pairConverter = typeConverters.get(BaseModule.getPairClass());
    regexConverter = typeConverters.get(BaseModule.getRegexClass());
    nullConverter = typeConverters.get(BaseModule.getNullClass());
    classConverter = typeConverters.get(BaseModule.getClassClass());
    typeAliasConverter = typeConverters.get(BaseModule.getTypeAliasClass());
  }

  @Override
  public Object convertString(String value, Iterable<Object> path) {
    return doConvert(value, path, stringConverter);
  }

  @Override
  public Object convertBoolean(Boolean value, Iterable<Object> path) {
    return doConvert(value, path, booleanConverter);
  }

  @Override
  public Object convertInt(Long value, Iterable<Object> path) {
    return doConvert(value, path, intConverter);
  }

  @Override
  public Object convertFloat(Double value, Iterable<Object> path) {
    return doConvert(value, path, floatConverter);
  }

  @Override
  public Object convertDuration(VmDuration value, Iterable<Object> path) {
    return doConvert(value, path, durationConverter);
  }

  @Override
  public Object convertDataSize(VmDataSize value, Iterable<Object> path) {
    return doConvert(value, path, dataSizeConverter);
  }

  @Override
  public Object convertBytes(VmBytes value, Iterable<Object> path) {
    return doConvert(value, path, bytesConverter);
  }

  @Override
  public Object convertIntSeq(VmIntSeq value, Iterable<Object> path) {
    return doConvert(value, path, intSeqConverter);
  }

  @Override
  public Object convertList(VmList value, Iterable<Object> path) {
    return doConvert(value, path, listConverter);
  }

  @Override
  public Object convertSet(VmSet value, Iterable<Object> path) {
    return doConvert(value, path, setConverter);
  }

  @Override
  public Object convertMap(VmMap value, Iterable<Object> path) {
    return doConvert(value, path, mapConverter);
  }

  @Override
  public Object convertListing(VmListing value, Iterable<Object> path) {
    return doConvert(value, path, listingConverter);
  }

  @Override
  public Object convertMapping(VmMapping value, Iterable<Object> path) {
    return doConvert(value, path, mappingConverter);
  }

  @Override
  public Object convertDynamic(VmDynamic value, Iterable<Object> path) {
    return doConvert(value, path, dynamicConverter);
  }

  @Override
  public Object convertTyped(VmTyped value, Iterable<Object> path) {
    return doConvert(value, path, findTypeConverter(value.getVmClass()));
  }

  @Override
  public Object convertPair(VmPair value, Iterable<Object> path) {
    return doConvert(value, path, pairConverter);
  }

  @Override
  public Object convertRegex(VmRegex value, Iterable<Object> path) {
    return doConvert(value, path, regexConverter);
  }

  @Override
  public Object convertFunction(VmFunction value, Iterable<Object> path) {
    return doConvert(value, path, typeConverters.get(value.getVmClass()));
  }

  @Override
  public Object convertClass(VmClass value, Iterable<Object> path) {
    return doConvert(value, path, classConverter);
  }

  @Override
  public Object convertTypeAlias(VmTypeAlias value, Iterable<Object> path) {
    return doConvert(value, path, typeAliasConverter);
  }

  @Override
  public Object convertNull(VmNull value, Iterable<Object> path) {
    return doConvert(value, path, nullConverter);
  }

  private Map<VmClass, VmFunction> createTypeConverters(VmMapping converters) {
    var result = new HashMap<VmClass, VmFunction>();
    converters.iterateMemberValues(
        (key, member, value) -> {
          assert value != null; // forced in ctor
          if (key instanceof VmClass vmClass) {
            result.put(vmClass, (VmFunction) value);
          }
          return true;
        });
    return result;
  }

  @SuppressWarnings("unchecked")
  private Pair<Object[], VmFunction>[] createPathConverters(VmMapping converters) {
    var result = new ArrayList<Pair<Object[], VmFunction>>();
    var parser = new PathSpecParser();
    converters.iterateMemberValues(
        (key, member, value) -> {
          assert value != null; // forced in ctor
          if (key instanceof String string) {
            result.add(Pair.of(parser.parse(string), (VmFunction) value));
          }
          return true;
        });
    return result.toArray(new Pair[0]);
  }

  private @Nullable VmFunction getPathConverter(Iterable<Object> path) {
    for (var converter : pathConverters) {
      if (PathConverterSupport.pathMatches(Arrays.asList(converter.first), path)) {
        return converter.second;
      }
    }
    return null;
  }

  /**
   * Finds a type converter for the given class (if one exists).
   *
   * <p>Type converters are covariant, so an Animal converter will accept a Dog or a Cat. This
   * method will return the most specific converter for a type.
   */
  private @Nullable VmFunction findTypeConverter(VmClass clazz) {
    for (var current = clazz; current != null; current = current.getSuperclass()) {
      var found = typeConverters.get(current);
      if (found != null) return found;
    }
    return null;
  }

  private Object doConvert(
      Object value, Iterable<Object> path, @Nullable VmFunction typeConverter) {
    var pathConverter = getPathConverter(path);
    if (pathConverter != null) {
      // path converter wins over type converter
      return pathConverter.apply(value);
    }

    if (typeConverter != null) {
      return typeConverter.apply(value);
    }

    return value;
  }
}
