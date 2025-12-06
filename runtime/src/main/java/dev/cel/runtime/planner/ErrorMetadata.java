// Copyright 2025 Google LLC
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

package dev.cel.runtime.planner;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import dev.cel.runtime.Metadata;

@Immutable
final class ErrorMetadata implements Metadata {

  private final ImmutableMap<Long, Integer> exprIdToPositionMap;
  private final String location;

  @Override
  public String getLocation() {
    return location;
  }

  @Override
  public int getPosition(long exprId) {
    return exprIdToPositionMap.getOrDefault(exprId, 0);
  }

  @Override
  public boolean hasPosition(long exprId) {
    return exprIdToPositionMap.containsKey(exprId);
  }

  static ErrorMetadata create(ImmutableMap<Long, Integer> exprIdToPositionMap, String location) {
    return new ErrorMetadata(exprIdToPositionMap, location);
  }

  private ErrorMetadata(ImmutableMap<Long, Integer> exprIdToPositionMap, String location) {
    this.exprIdToPositionMap = checkNotNull(exprIdToPositionMap);
    this.location = checkNotNull(location);
  }
}
