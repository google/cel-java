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

import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import dev.cel.common.annotations.Internal;

/**
 * Wrap Message to support Unknown value.
 *
 * <p>Deprecated. New clients should use {@link CelAttribute} based unknowns.
 */
@Deprecated
@Internal
public interface PartialMessageOrBuilder extends MessageOrBuilder {
  /** Return original message. */
  public Message getMessage();

  /*
   * Return field mask.
   */
  public FieldMask getFieldMask();

  /**
   * This method is similar to {@link MessageOrBuilder#getField(FieldDescriptor)}, with the
   * following differences: This method may throw an InterpreterException wrapped with a
   * RuntimeException, if the field path is set in the Field mask.
   */
  @Override
  public Object getField(FieldDescriptor field);
}
