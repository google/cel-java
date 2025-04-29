#!/bin/bash
# Copyright 2024 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Script ran as part of Github CEL-Java CI to verify that the runtime jar does not contain unwanted dependencies.

UNWANTED_DEPS=(
  '@cel_spec' # Do not include generated CEL protos in the jar
  'protobuf_java_util' # Does not support protolite
)

runtime_deps="$(bazel query 'deps(//publish:cel_runtime)' --nohost_deps --noimplicit_deps --output graph)"

for unwanted_dep in "${UNWANTED_DEPS[@]}"; do
  if echo "$runtime_deps" | grep "$unwanted_dep" > /dev/null; then
    echo -e "Runtime contains unwanted dependency: $unwanted_dep!\n"
    echo "cel_spec dependency graph (including '$unwanted_dep'):"
    echo "$(echo "$runtime_deps" | grep "$unwanted_dep")"
    exit 1
  fi
done

exit 0

