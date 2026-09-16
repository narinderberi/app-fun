#!/usr/bin/env bash

# 1. Root configuration files
touch settings.gradle.kts
touch build.gradle.kts
touch gradle.properties
touch .gitignore

# 2. GitHub Actions directory
mkdir -p .github/workflows
touch .github/workflows/build.yml

# 3. App module configuration & resources
mkdir -p app/src/main/res/xml
mkdir -p app/src/main/res/values
touch app/build.gradle.kts
touch app/src/main/AndroidManifest.xml
touch app/src/main/res/xml/accessibility_service_config.xml
touch app/src/main/res/values/strings.xml

# 4. Kotlin Source Directories (replace package folder names if needed)
PACKAGE_DIR="app/src/main/java/com/yourdomain/throttlingapp"
mkdir -p "$PACKAGE_DIR"

touch "$PACKAGE_DIR/MainActivity.kt"
touch "$PACKAGE_DIR/ThrottlingVpnService.kt"
touch "$PACKAGE_DIR/ProtectionAccessibilityService.kt"
touch "$PACKAGE_DIR/BootReceiver.kt"

echo "Directory structure created successfully!"