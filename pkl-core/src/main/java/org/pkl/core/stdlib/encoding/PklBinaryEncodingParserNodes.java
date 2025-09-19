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
package org.pkl.core.stdlib.encoding;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.net.URI;
import org.msgpack.core.MessagePack;
import org.pkl.core.PklBinaryDecoder;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.http.HttpClientInitException;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.runtime.Identifier;
import org.pkl.core.runtime.VmBytes;
import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.runtime.VmLanguage;
import org.pkl.core.runtime.VmTypeAlias;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.stdlib.ExternalMethod2Node;
import org.pkl.core.util.Nullable;

public final class PklBinaryEncodingParserNodes {

  public abstract static class parse extends ExternalMethod2Node {
    @Specialization
    protected Object eval(VmTyped self, VmBytes bytes, VmTyped context) {
      return doParse(bytes.getBytes(), context);
    }

    @Specialization
    protected Object eval(
        VmTyped self,
        VmTyped resource,
        VmTyped context,
        @Cached("create()") IndirectCallNode callNode) {
      var bytes = (VmBytes) VmUtils.readMember(resource, Identifier.BYTES, callNode);
      return doParse(bytes.getBytes(), context);
    }

    @TruffleBoundary
    private Object doParse(byte[] bytes, VmTyped context) {
      var unpacker = MessagePack.newDefaultUnpacker(bytes);
      return new PklBinaryDecoder(
              unpacker, new Importer(context.getVmClass().getPClassInfo().getModuleUri()))
          .decode();
    }

    private class Importer implements PklBinaryDecoder.Importer {

      private final URI currentModuleUri;
      private final VmContext context;
      private final VmLanguage language;

      private Importer(URI currentModuleUri) {
        this.currentModuleUri = currentModuleUri;
        this.language = VmLanguage.get(parse.this);
        this.context = VmContext.get(parse.this);
      }

      @Override
      public VmClass importClass(String name, URI moduleUri) {
        var module = importModule(moduleUri);
        var identifier = getIdentifier(name);
        if (identifier == null) { // if module class
          return module.getVmClass();
        }

        var clazz = module.getClass(identifier);
        if (clazz == null) {
          throw parse
              .this
              .exceptionBuilder()
              .cannotFindProperty(module, identifier, true, false)
              .build();
        }
        return clazz;
      }

      @Override
      public VmTypeAlias importTypeAlias(String name, URI moduleUri) {
        var module = importModule(moduleUri);
        var identifier = getIdentifier(name);
        assert identifier != null;
        var alias = module.getTypeAlias(identifier);
        if (alias == null) {
          throw parse
              .this
              .exceptionBuilder()
              .cannotFindProperty(module, identifier, true, false)
              .build();
        }
        return alias;
      }

      private @Nullable Identifier getIdentifier(String name) {
        // if name is in the format module#identifier, strip to just identifier
        // if no hash, this is a reference to a module class; return null
        var hashIndex = name.lastIndexOf("#");
        if (hashIndex < 0) {
          return null;
        }
        return Identifier.get(name.substring(hashIndex + 1));
      }

      private VmTyped importModule(URI importUri) {
        try {
          context.getSecurityManager().checkImportModule(currentModuleUri, importUri);
          var moduleToImport = context.getModuleResolver().resolve(importUri, parse.this);
          return language.loadModule(moduleToImport, parse.this);
        } catch (SecurityManagerException | PackageLoadError | HttpClientInitException e) {
          throw parse.this.exceptionBuilder().withCause(e).build();
        }
      }
    }
  }
}
