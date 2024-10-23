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
package org.pkl.core.util.yaml.snake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.ScalarResolver;

public abstract class YamlResolver implements ScalarResolver {
  private final Map<Character, List<ResolverTuple>> yamlImplicitResolvers = new HashMap<>();

  protected void addImplicitResolver(Tag tag, Pattern regexp, String initialChars) {
    for (var i = 0; i < initialChars.length(); i++) {
      yamlImplicitResolvers
          .computeIfAbsent(initialChars.charAt(i), ch -> new ArrayList<>())
          .add(new ResolverTuple(tag, regexp));
    }
  }

  @Override
  public Tag resolve(String value, Boolean implicit) {
    if (!implicit) return Tag.STR;

    var resolvers = yamlImplicitResolvers.get(value.isEmpty() ? '\0' : value.charAt(0));
    if (resolvers == null) return Tag.STR;

    for (var resolver : resolvers) {
      if (resolver.regexp.matcher(value).matches()) return resolver.tag;
    }

    return Tag.STR;
  }

  private static final class ResolverTuple {
    private final Tag tag;
    private final Pattern regexp;

    ResolverTuple(Tag tag, Pattern regexp) {
      this.tag = tag;
      this.regexp = regexp;
    }

    @Override
    public String toString() {
      return "Tuple tag=" + tag + " regexp=" + regexp;
    }
  }
}
