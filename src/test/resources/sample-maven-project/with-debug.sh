#!/bin/bash

source "$(dirname $(dirname "$0"))/build.sh"

SCRIPT_DIR=$(dirname "$0")

# Setup build directory with compilation information
(cd "$SCRIPT_DIR" && build true with-debug)
