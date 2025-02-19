// Copyright 2024 Google LLC
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

package dev.cel.bundle;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Interface to be implemented on a builder that can be used to verify all required fields being
 * set.
 */
interface RequiredFieldsChecker {

  ImmutableList<RequiredField> requiredFields();

  default ImmutableList<String> getMissingRequiredFieldNames() {
    return requiredFields().stream()
        .filter(entry -> !entry.fieldValue().get().isPresent())
        .map(RequiredField::displayName)
        .collect(toImmutableList());
  }

  @AutoValue
  abstract class RequiredField {
    abstract String displayName();

    abstract Supplier<Optional<?>> fieldValue();

    static RequiredField of(String displayName, Supplier<Optional<?>> fieldValue) {
      return new AutoValue_RequiredFieldsChecker_RequiredField(displayName, fieldValue);
    }
  }
}
