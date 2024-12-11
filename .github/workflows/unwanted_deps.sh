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

set -e

runtime_deps=$(bazel query 'deps(//publish:cel_runtime)' --nohost_deps --noimplicit_deps --output graph | grep '@cel_spec')

if [[ ! -z $runtime_deps ]]; then
  echo "cel_spec deps: " $runtime_deps
  echo "Runtime contains unwanted @cel_spec dependency" && exit 1
fi

exit 0

