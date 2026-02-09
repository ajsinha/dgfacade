#!/bin/bash
# =============================================================================
# DGFacade Build Script
# Copyright © 2025-2030 Ashutosh Sinha. All Rights Reserved. Patent Pending.
# =============================================================================

set -e

echo "========================================="
echo "  DGFacade Build"
echo "  Version: 1.4.0"
echo "========================================="

# Clean and build
echo "[1/3] Building all modules..."
mvn clean package -DskipTests

# Create deployment directory
echo "[2/3] Preparing deployment..."
DEPLOY_DIR="target/dgfacade-deploy"
rm -rf "$DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR"/{config/handlers,libs,logs}

# Copy artifacts
cp web/target/dgfacade-web-1.4.0.jar "$DEPLOY_DIR/dgfacade.jar"
cp config/handlers/*.json "$DEPLOY_DIR/config/handlers/"
cp config/users.json "$DEPLOY_DIR/config/"
cp config/apikeys.json "$DEPLOY_DIR/config/"
cp run.sh "$DEPLOY_DIR/"
chmod +x "$DEPLOY_DIR/run.sh"

echo "[3/3] Deployment package ready at: $DEPLOY_DIR"
echo ""
echo "To run: cd $DEPLOY_DIR && ./run.sh"
echo "========================================="
