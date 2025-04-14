# Copyright 2025 Google LLC
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
"""Macro to create android_library rules with CEL specific options applied."""

load("@rules_android//rules:rules.bzl", "android_library", "android_local_test")

DEFAULT_JAVACOPTS = [
    "-XDstringConcat=inline",  # SDK forces usage of StringConcatFactory (java 9+)
]

def cel_android_library(name, **kwargs):
    """
    Generates android_library target with CEL specific javacopts applied

    Args:
      name: name of the android_library target
      **kwargs: rest of the args accepted by android_library
    """

    javacopts = kwargs.get("javacopts", [])
    all_javacopts = DEFAULT_JAVACOPTS + javacopts

    # By default, set visibility to android_allow_list, unless if overridden at the call site.
    provided_visibility_or_default = kwargs.get("visibility", ["//:android_allow_list"])
    filtered_kwargs = {k: v for k, v in kwargs.items() if k != "visibility"}

    android_library(
        name = name,
        visibility = provided_visibility_or_default,
        javacopts = all_javacopts,
        **filtered_kwargs
    )

def cel_android_local_test(name, **kwargs):
    """
    Generates android_local_test target with CEL specific javacopts applied

    Args:
      name: name of the android_local_test target
      **kwargs: rest of the args accepted by android_local_test
    """

    javacopts = kwargs.get("javacopts", [])
    all_javacopts = DEFAULT_JAVACOPTS + javacopts

    android_local_test(
        name = name,
        javacopts = all_javacopts,
        **kwargs
    )
