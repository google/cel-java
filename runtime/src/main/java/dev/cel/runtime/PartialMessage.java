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

package dev.cel.runtime;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.OneofDescriptor;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.UnknownFieldSet;
import com.google.protobuf.util.FieldMaskUtil;
import dev.cel.common.annotations.Internal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Wrap a Message to throw an error on access to certain fields or sub-fields, as described by a
 * FieldMask.
 *
 * <p>Deprecated. New clients should use {@link CelAttribute} based unknowns.
 */
@Deprecated
@Internal
public class PartialMessage implements PartialMessageOrBuilder, IncompleteData {

  private final Message message;
  private final FieldMask fieldMask;

  @Override
  public Message getDefaultInstanceForType() {
    return message.getDefaultInstanceForType();
  }

  @Override
  public boolean isInitialized() {
    return message.isInitialized();
  }

  @Override
  public List<String> findInitializationErrors() {
    return message.findInitializationErrors();
  }

  @Override
  public String getInitializationErrorString() {
    return message.getInitializationErrorString();
  }

  @Override
  public Descriptor getDescriptorForType() {
    return message.getDescriptorForType();
  }

  @Override
  public Map<FieldDescriptor, Object> getAllFields() {
    return message.getAllFields();
  }

  @Override
  public boolean hasOneof(OneofDescriptor oneof) {
    return message.hasOneof(oneof);
  }

  @Override
  public FieldDescriptor getOneofFieldDescriptor(OneofDescriptor oneof) {
    return message.getOneofFieldDescriptor(oneof);
  }

  @Override
  public boolean hasField(FieldDescriptor field) {
    return message.hasField(field);
  }

  /** Create relative field masks to the field specified by the name. */
  private FieldMask subpathMask(String name) {
    FieldMask.Builder builder = FieldMask.newBuilder();
    for (String p : fieldMask.getPathsList()) {
      if (!p.startsWith(name)) {
        continue;
      }
      String tmp = p.substring(name.length() + 1);
      if (tmp.length() > 0) {
        builder.addPaths(tmp);
      }
    }
    return builder.build();
  }

  @Override
  public Object getField(Descriptors.FieldDescriptor field) {
    String path = field.getName();

    if (fieldMask.getPathsList().contains(path)) {
      return InterpreterUtil.createUnknownExprValue(new ArrayList<Long>());
    }

    Object obj = message.getField(field);
    FieldMask subFieldMask = subpathMask(path);
    if (obj instanceof Message && !subFieldMask.getPathsList().isEmpty()) {
      // Partial message means at least one of its field has been marked as unknown
      return new PartialMessage((Message) obj, subFieldMask);
    } else {
      return obj;
    }
  }

  @Override
  public int getRepeatedFieldCount(FieldDescriptor field) {
    return message.getRepeatedFieldCount(field);
  }

  @Override
  public Object getRepeatedField(FieldDescriptor field, int index) {
    return message.getRepeatedField(field, index);
  }

  @Override
  public UnknownFieldSet getUnknownFields() {
    return message.getUnknownFields();
  }

  public PartialMessage(Message m) {
    this.message = m;
    this.fieldMask = FieldMask.getDefaultInstance();
  }

  public PartialMessage(Message m, FieldMask mask) {
    this.message = m;
    this.fieldMask = mask;

    if (m == null) {
      throw new NullPointerException("The message in PartialMessage is null.");
    }
    if (!FieldMaskUtil.isValid(m.getDescriptorForType(), fieldMask)) {
      throw new RuntimeException(
          new InterpreterException.Builder("Invalid field mask for message:" + message).build());
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(this.getClass());
    sb.append("{\nmessage: {\n");
    sb.append(this.message);
    sb.append("},\nfieldMask: {\n");
    for (Iterator<String> it = fieldMask.getPathsList().iterator(); it.hasNext(); ) {
      sb.append("  paths: ").append(it.next());
      if (it.hasNext()) {
        sb.append(",\n");
      }
    }
    sb.append("\n}\n");
    return sb.toString();
  }

  @Override
  public Message getMessage() {
    return message;
  }

  @Override
  public FieldMask getFieldMask() {
    return fieldMask;
  }
}
