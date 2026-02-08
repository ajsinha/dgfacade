#!/bin/bash
# =============================================================================
# DGFacade Run Script
# Copyright © 2025-2030 Ashutosh Sinha. All Rights Reserved. Patent Pending.
# =============================================================================

JAR="dgfacade.jar"
if [ ! -f "$JAR" ]; then
    JAR="web/target/dgfacade-web-1.1.0.jar"
fi

if [ ! -f "$JAR" ]; then
    echo "ERROR: Cannot find dgfacade JAR. Run build.sh first."
    exit 1
fi

echo "Starting DGFacade v1.1.0..."
echo "Config dir: config/"
echo "Libs dir:   libs/"
echo "Logs dir:   logs/"
echo ""

java -jar "$JAR" \
    --dgfacade.config.handlers-dir=config/handlers \
    --dgfacade.config.users-file=config/users.json \
    --dgfacade.config.apikeys-file=config/apikeys.json \
    --dgfacade.config.external-libs-dir=./libs \
    "$@"
