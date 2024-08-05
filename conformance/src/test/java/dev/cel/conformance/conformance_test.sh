#!/bin/bash

(exec "$@")
rc = $?
if [ $rc -eq 1 ]; then
  rc = 0
fi
exit $rc
