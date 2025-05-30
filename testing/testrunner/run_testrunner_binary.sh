#!/bin/bash
# Path: //third_party/java/cel/testing/testrunner/run_testrunner_binary.sh

die() {
  echo "ERROR: $*" >&2
  exit 1
}

# Get the name passed to the sh_test rule from the arguments.
# This is the name passed to the sh_test macro,
# and it's also the name of the java_binary
NAME=$1

# Find the test_runner_binary executable (wrapper script), using the NAME
TEST_RUNNER_BINARY="$(find -L "${TEST_SRCDIR}" -name "${NAME}_test_runner_binary" -type f -executable)"

# This would have also worked but the above is more strict in finding
# a file that's a symlink to the executable.
# TEST_RUNNER_BINARY="$(find "${TEST_SRCDIR}" -name "${NAME}_test_runner_binary")"

if [ -z "$TEST_RUNNER_BINARY" ]; then
  die "Test runner binary (wrapper script) $TEST_RUNNER_BINARY not found in runfiles."
fi

#Execute the symlink to the executable.
"$TEST_RUNNER_BINARY" || die "Some or all the tests failed."

echo "PASS"
