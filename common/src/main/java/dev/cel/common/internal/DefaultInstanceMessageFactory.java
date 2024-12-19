// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cel.common.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import com.google.protobuf.MessageLite;
import dev.cel.common.annotations.Internal;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton factory for creating default messages from a protobuf descriptor.
 *
 * <p>CEL Library Internals. Do Not Use.
 */
@Internal
public final class DefaultInstanceMessageFactory {

  private static final DefaultInstanceMessageFactory instance = new DefaultInstanceMessageFactory();

  private final Map<String, LazyGeneratedMessageDefaultInstance> messageByDescriptorName =
      new ConcurrentHashMap<>();

  /** Gets a single instance of this MessageFactory */
  public static DefaultInstanceMessageFactory getInstance() {
    return instance;
  }

  /**
   * Creates a default instance of a protobuf message given a descriptor. This is essentially the
   * same as calling FooMessage.getDefaultInstance(), except reflection is leveraged.
   *
   * @return Default instance of a type. Returns an empty optional if the descriptor used to
   *     construct the type via reflection is different to the provided descriptor or if the
   *     descriptor class isn't loaded in the binary.
   */
  public Optional<MessageLite> getPrototype(String protoFqn, String protoJavaClassFqn) {
    LazyGeneratedMessageDefaultInstance lazyDefaultInstance =
        messageByDescriptorName.computeIfAbsent(
            protoFqn,
            (unused) ->
                new LazyGeneratedMessageDefaultInstance(
                    protoJavaClassFqn));

    MessageLite defaultInstance = lazyDefaultInstance.getDefaultInstance();
    if (defaultInstance == null) {
      return Optional.empty();
    }

    return Optional.of(defaultInstance);
  }

  /**
   * Creates a default instance of a protobuf message given a descriptor. This is essentially the
   * same as calling FooMessage.getDefaultInstance(), except reflection is leveraged.
   *
   * @return Default instance of a type. Returns an empty optional if the descriptor used to
   *     construct the type via reflection is different to the provided descriptor or if the
   *     descriptor class isn't loaded in the binary.
   */
  public Optional<Message> getPrototype(Descriptor descriptor) {
    // Note: This logic will have to be moved outside this package
    MessageLite defaultInstance = getPrototype(descriptor.getFullName(),
        ProtoJavaQualifiedNames.getFullyQualifiedJavaClassName(descriptor)).orElse(null);
    if (defaultInstance == null) {
      return Optional.empty();
    }

    if (!(defaultInstance instanceof Message)) {
      throw new IllegalArgumentException("Expected a full protobuf message, but got: " + defaultInstance);
    }

    Message fullMessage = (Message) defaultInstance;

    // Reference equality is intended. We want to make sure the descriptors are equal
    // to guarantee types to be hermetic if linked types is disabled.
    if (fullMessage.getDescriptorForType() != descriptor) {
      return Optional.empty();
    }
    return Optional.of(fullMessage);
  }

  /** A placeholder to lazily load the generated messages' defaultInstances. */
  private static final class LazyGeneratedMessageDefaultInstance {
    private final String fullClassName;
    private volatile MessageLite defaultInstance = null;
    private volatile boolean loaded = false;

    public LazyGeneratedMessageDefaultInstance(String fullClassName) {
      this.fullClassName = fullClassName;
    }

    public MessageLite getDefaultInstance() {
      if (!loaded) {
        synchronized (this) {
          if (!loaded) {
            loadDefaultInstance();
            loaded = true;
          }
        }
      }
      return defaultInstance;
    }

    private void loadDefaultInstance() {
      try {
        defaultInstance =
            (MessageLite) Class.forName(fullClassName).getMethod("getDefaultInstance").invoke(null);
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new LinkageError(
            String.format("getDefaultInstance for class: %s failed.", fullClassName), e);
      } catch (NoSuchMethodException e) {
        throw new LinkageError(
            String.format("getDefaultInstance method does not exist in class: %s.", fullClassName),
            e);
      } catch (ClassNotFoundException e) {
        // The class may not exist in some instances (Ex: evaluating a checked expression from a
        // cached source).
      }
    }
  }

  /** Clears the descriptor map. This should not be used outside testing. */
  @VisibleForTesting
  void resetDescriptorMapForTesting() {
    messageByDescriptorName.clear();
  }

  private DefaultInstanceMessageFactory() {}
}
