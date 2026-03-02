.PHONY: help docs-help docs-install docs-build docs-serve docs-clean docs-pr
.PHONY: android-release android-release-version android-release-dry-run android-install
.PHONY: ios-release ios-release-version ios-release-dry-run
.PHONY: setup-signing setup-github-secrets
.PHONY: android-build-release android-build-bundle android-deploy-testing
.PHONY: android-promote-alpha android-promote-production
.PHONY: ios-build-release ios-deploy-testflight
.PHONY: ios-remote-unlock ios-remote-sync ios-remote-build ios-remote-install ios-remote-sim ios-remote-test ios-remote-resolve
.PHONY: ios-build ios-sim ios-test ios-resolve ios-device ios-log

# Default target shows all available commands
help:
	@echo "Deadly Monorepo - Available Make Targets"
	@echo "========================================"
	@echo ""
	@echo "ANDROID RELEASE:"
	@echo "  android-release          - Android release with auto-versioning"
	@echo "  android-release-version  - Android release with specific version (make android-release-version VERSION=1.2.3)"
	@echo "  android-release-dry-run  - Preview android release without making changes"
	@echo ""
	@echo "IOS RELEASE:"
	@echo "  ios-release              - iOS release with auto-versioning"
	@echo "  ios-release-version      - iOS release with specific version (make ios-release-version VERSION=1.2.3)"
	@echo "  ios-release-dry-run      - Preview iOS release without making changes"
	@echo ""
	@echo "  android-install          - Build debug + install to connected Android device (USB)"
	@echo ""
	@echo "ANDROID PROMOTIONS:"
	@echo "  android-promote-alpha      - Promote internal build to closed alpha (triggers workflow)"
	@echo "  android-promote-production - Promote alpha build to production (triggers workflow)"
	@echo ""
	@echo "SIGNING & SECRETS:"
	@echo "  setup-signing        - Generate keystore and .secrets/ setup"
	@echo "  setup-github-secrets - Upload all secrets to GitHub repository"
	@echo ""
	@echo "ANDROID FASTLANE:"
	@echo "  android-build-release - Build signed Android release APK"
	@echo "  android-build-bundle  - Build signed Android App Bundle (AAB)"
	@echo "  android-deploy-testing - Deploy to Play Store Internal Testing"
	@echo ""
	@echo "IOS FASTLANE:"
	@echo "  ios-build-release    - Build signed iOS release IPA"
	@echo "  ios-deploy-testflight - Deploy to TestFlight"
	@echo ""
	@echo "IOS LOCAL BUILD (macOS):"
	@echo "  ios-build            - Build debug for simulator"
	@echo "  ios-sim              - Build + run on iPhone 16 simulator"
	@echo "  ios-device           - Build + install to connected device"
	@echo "  ios-test             - Run tests on simulator"
	@echo "  ios-resolve          - Resolve SPM package dependencies"
	@echo "  ios-log              - Stream app logs from simulator via os_log"
	@echo ""
	@echo "IOS REMOTE BUILD (Linux → Mac):"
	@echo "  ios-remote-unlock    - Unlock Mac keychain for SSH code signing (once per reboot)"
	@echo "  ios-remote-sync      - Rsync working tree to Mac"
	@echo "  ios-remote-build     - Sync + build debug on Mac simulator"
	@echo "  ios-remote-install   - Sync + build + install to connected device"
	@echo "  ios-remote-sim       - Sync + build + run on iPhone 16 simulator"
	@echo "  ios-remote-test      - Sync + run tests on Mac simulator"
	@echo "  ios-remote-resolve   - Sync + resolve SPM package dependencies"
	@echo ""
	@echo "DOCUMENTATION:"
	@echo "  docs-help       - Show documentation-specific help"
	@echo "  docs-install    - Install MkDocs dependencies"
	@echo "  docs-build      - Build the documentation site"
	@echo "  docs-serve      - Serve docs locally for development"
	@echo "  docs-clean      - Remove generated site directory"
	@echo "  docs-pr         - Clean, build and confirm site generation before PR"

# Documentation help (more detailed)
docs-help:
	@echo "Documentation Make targets:"
	@echo "  docs-install   - Install MkDocs dependencies (from docs/requirements.txt)"
	@echo "  docs-build     - Build the documentation site into ./site"
	@echo "  docs-serve     - Serve docs locally for development"
	@echo "  docs-clean     - Remove generated site directory"
	@echo "  docs-pr        - Clean, build and confirm site generation before PR"

# Install MkDocs dependencies from the docs-specific requirements file.
docs-install:
	pip install -r docs/requirements.txt

# Build the documentation using the custom config.
docs-build:
	mkdocs build -f docs/mkdocs.yml

# Serve the documentation locally for development.
docs-serve:
	mkdocs serve -f docs/mkdocs.yml -a localhost:8099

# Remove generated site directory.
docs-clean:
	rm -rf site/

# Pre-PR check: clean, build and confirm successful generation.
docs-pr: docs-clean docs-build
	@echo "Site built successfully!"
	@echo "Output in site/ directory"
	@echo "Run 'make docs-serve' to test locally"

# =============================================================================
# RELEASE MANAGEMENT
# =============================================================================

android-release:
	@./scripts/release.sh --platform android

android-release-version:
	@if [ -z "$(VERSION)" ]; then \
		echo "Error: VERSION not specified"; \
		echo "Usage: make android-release-version VERSION=1.2.3"; \
		exit 1; \
	fi
	@./scripts/release.sh --platform android $(VERSION)

android-release-dry-run:
	@./scripts/release.sh --platform android --dry-run

ios-release:
	@./scripts/release.sh --platform ios

ios-release-version:
	@if [ -z "$(VERSION)" ]; then \
		echo "Error: VERSION not specified"; \
		echo "Usage: make ios-release-version VERSION=1.2.3"; \
		exit 1; \
	fi
	@./scripts/release.sh --platform ios $(VERSION)

ios-release-dry-run:
	@./scripts/release.sh --platform ios --dry-run

setup-signing:
	@./scripts/setup-signing.sh

setup-github-secrets:
	@./scripts/setup-github-secrets.sh

# =============================================================================
# ANDROID FASTLANE
# =============================================================================

android-install:
	cd androidApp && ./gradlew installDebug

android-promote-alpha:
	gh workflow run android-promote.yml -f stage=alpha \
		-f version=$(shell grep VERSION_NAME androidApp/version.properties | cut -d= -f2)

android-promote-production:
	gh workflow run android-promote.yml -f stage=production \
		-f version=$(shell grep VERSION_NAME androidApp/version.properties | cut -d= -f2)

android-build-release:
	@echo "Building Android release APK..."
	@cd androidApp && fastlane build_release

android-build-bundle:
	@echo "Building Android App Bundle..."
	@cd androidApp && fastlane build_bundle

android-deploy-testing:
	@echo "Deploying to Play Store Internal Testing..."
	@cd androidApp && fastlane deploy_testing

# =============================================================================
# IOS FASTLANE
# =============================================================================

ios-build-release:
	@echo "Building iOS release IPA..."
	@cd iosApp && fastlane build_release

ios-deploy-testflight:
	@echo "Deploying to TestFlight..."
	@cd iosApp && fastlane deploy_testflight

# =============================================================================
# IOS REMOTE BUILD (Linux → Mac)
# =============================================================================

REMOTE_HOST ?= dsilbergleithcu@worklaptop.local
REMOTE_PATH ?= ~/Developer/ai/deadly-monorepo
REMOTE_IOS  ?= $(REMOTE_PATH)/iosApp

# Sync working tree to Mac (rsync, excludes build artifacts)
ios-remote-sync:
	@echo "Syncing to $(REMOTE_HOST):$(REMOTE_PATH)..."
	@rsync -avz --delete \
		--exclude='.git' \
		--exclude='.claude' \
		--exclude='PLANS' \
		--exclude='androidApp/app/build' \
		--exclude='androidApp/**/build' \
		--exclude='androidApp/.gradle' \
		--exclude='iosApp/build' \
		--exclude='iosApp/**/build' \
		--exclude='iosApp/**/.build' \
		--exclude='node_modules' \
		--exclude='.secrets' \
		./ $(REMOTE_HOST):$(REMOTE_PATH)/

# Sync + build debug on Mac
ios-remote-build:
	@$(MAKE) ios-remote-sync
	@echo "Building on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "cd $(REMOTE_IOS) && xcodebuild -project deadly.xcodeproj -scheme deadly -configuration Debug -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -20"

# Unlock the Mac's login keychain (required once per reboot for SSH code signing)
ios-remote-unlock:
	@echo "Unlocking keychain on $(REMOTE_HOST)..."
	@ssh -t $(REMOTE_HOST) "security unlock-keychain ~/Library/Keychains/login.keychain-db"

# Sync + build + install to connected device (requires USB-connected iPhone + KEYCHAIN_PASSWORD env var)
ios-remote-install:
	@$(MAKE) ios-remote-sync
	@echo "Building on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "security unlock-keychain -p '$(KEYCHAIN_PASSWORD)' ~/Library/Keychains/login.keychain-db && cd $(REMOTE_IOS) && xcodebuild -project deadly.xcodeproj -scheme deadly -configuration Debug -destination 'generic/platform=iOS' -allowProvisioningUpdates build 2>&1 | tail -20"
	@echo "Installing to device..."
	@ssh $(REMOTE_HOST) 'DEVICE_ID=$$(xcrun devicectl list devices 2>/dev/null | grep -oE "[0-9A-F]{8}-([0-9A-F]{4}-){3}[0-9A-F]{12}" | head -1) && APP_PATH=$$(cd $(REMOTE_IOS) && xcodebuild -project deadly.xcodeproj -scheme deadly -configuration Debug -destination "generic/platform=iOS" -showBuildSettings 2>/dev/null | grep " BUILT_PRODUCTS_DIR" | head -1 | awk "{print \$$3}")/deadly.app && xcrun devicectl device install app --device "$$DEVICE_ID" "$$APP_PATH" 2>&1'

# Sync + build + launch on iPhone 16 simulator
ios-remote-sim:
	@$(MAKE) ios-remote-sync
	@echo "Building for simulator on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "cd $(REMOTE_IOS) && xcodebuild -project deadly.xcodeproj -scheme deadly -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -20"
	@echo "Launching on simulator..."
	@ssh $(REMOTE_HOST) 'APP_PATH=$$(cd $(REMOTE_IOS) && xcodebuild -project deadly.xcodeproj -scheme deadly -configuration Debug -destination "platform=iOS Simulator,name=iPhone 16" -showBuildSettings 2>/dev/null | grep " BUILT_PRODUCTS_DIR" | head -1 | awk "{print \$$3}")/deadly.app && xcrun simctl boot "iPhone 16" 2>/dev/null; xcrun simctl install booted "$$APP_PATH" && xcrun simctl launch booted com.grateful.deadly && open -a Simulator'

# Sync + run tests on Mac simulator
ios-remote-test:
	@$(MAKE) ios-remote-sync
	@echo "Running tests on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "cd $(REMOTE_IOS) && xcodebuild test -project deadly.xcodeproj -scheme deadly -destination 'platform=iOS Simulator,name=iPhone 16' 2>&1 | tail -40"

# Resolve SPM packages on Mac (needed after adding new dependencies)
ios-remote-resolve:
	@$(MAKE) ios-remote-sync
	@echo "Resolving packages on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "cd $(REMOTE_IOS) && xcodebuild -resolvePackageDependencies -project deadly.xcodeproj 2>&1 | tail -20"

# =============================================================================
# IOS LOCAL BUILD (macOS)
# =============================================================================

IOS_DIR     := iosApp
IOS_PROJECT := $(IOS_DIR)/deadly.xcodeproj
IOS_SCHEME  := deadly
SIM_DEST    := platform=iOS Simulator,name=iPhone 16
BUNDLE_ID   := com.grateful.deadly

# Lightweight xcodebuild filter: prints Compiling/Linking/Signing lines as
# single-line progress, and passes through warnings, errors, and the final
# BUILD SUCCEEDED / BUILD FAILED summary.
define XC_FILTER
awk '/^Compile|^CompileC|^SwiftCompile|^Ld |^Link|^CodeSign|^ProcessInfoPlist|^PhaseScriptExecution/ \
	{ sub(/.*\//, ""); printf "  %-60s\r", $$0; next } \
	/warning:|error:|BUILD SUCCEEDED|BUILD FAILED|CLEAN SUCCEEDED|TEST SUCCEEDED|TEST FAILED|\*\*/ \
	{ print }'
endef

# Build debug for simulator
ios-build:
	@echo "Building debug for simulator..."
	@cd $(IOS_DIR) && xcodebuild \
		-project deadly.xcodeproj \
		-scheme $(IOS_SCHEME) \
		-configuration Debug \
		-destination 'generic/platform=iOS Simulator' \
		build 2>&1 | $(XC_FILTER)

# Build + run on iPhone 16 simulator
ios-sim:
	@echo "Building for simulator..."
	@cd $(IOS_DIR) && xcodebuild \
		-project deadly.xcodeproj \
		-scheme $(IOS_SCHEME) \
		-configuration Debug \
		-destination '$(SIM_DEST)' \
		build 2>&1 | $(XC_FILTER)
	@echo ""
	@echo "Launching on simulator..."
	@APP_PATH=$$(cd $(IOS_DIR) && xcodebuild \
		-project deadly.xcodeproj \
		-scheme $(IOS_SCHEME) \
		-configuration Debug \
		-destination '$(SIM_DEST)' \
		-showBuildSettings 2>/dev/null \
		| grep " BUILT_PRODUCTS_DIR" | head -1 | awk '{print $$3}')/deadly.app \
	&& xcrun simctl boot "iPhone 16" 2>/dev/null; \
	xcrun simctl install booted "$$APP_PATH" \
	&& xcrun simctl launch booted $(BUNDLE_ID) \
	&& open -a Simulator

# Build + install to connected device (may prompt for keychain password)
ios-device:
	@echo "Building for device..."
	@cd $(IOS_DIR) && xcodebuild \
		-project deadly.xcodeproj \
		-scheme $(IOS_SCHEME) \
		-configuration Debug \
		-destination 'generic/platform=iOS' \
		-allowProvisioningUpdates \
		build 2>&1 | $(XC_FILTER)
	@echo ""
	@echo "Installing to device..."
	@DEVICE_ID=$$(xcrun devicectl list devices 2>/dev/null \
		| grep -oE "[0-9A-F]{8}-([0-9A-F]{4}-){3}[0-9A-F]{12}" | head -1) \
	&& APP_PATH=$$(cd $(IOS_DIR) && xcodebuild \
		-project deadly.xcodeproj \
		-scheme $(IOS_SCHEME) \
		-configuration Debug \
		-destination 'generic/platform=iOS' \
		-showBuildSettings 2>/dev/null \
		| grep " BUILT_PRODUCTS_DIR" | head -1 | awk '{print $$3}')/deadly.app \
	&& xcrun devicectl device install app --device "$$DEVICE_ID" "$$APP_PATH"

# Run tests on simulator
ios-test:
	@echo "Running tests on simulator..."
	@cd $(IOS_DIR) && xcodebuild test \
		-project deadly.xcodeproj \
		-scheme $(IOS_SCHEME) \
		-destination '$(SIM_DEST)' \
		2>&1 | $(XC_FILTER)

# Resolve SPM package dependencies
ios-resolve:
	@echo "Resolving SPM packages..."
	@cd $(IOS_DIR) && xcodebuild \
		-resolvePackageDependencies \
		-project deadly.xcodeproj \
		2>&1 | $(XC_FILTER)

# Stream app logs from simulator (subsystems: deadly + SwiftAudioStreamEx)
ios-log:
	@echo "Streaming logs for $(BUNDLE_ID) and SwiftAudioStreamEx..."
	@echo "(Press Ctrl-C to stop)"
	@xcrun simctl spawn booted log stream \
		--predicate 'subsystem == "$(BUNDLE_ID)" OR subsystem == "SwiftAudioStreamEx"' \
		--level debug
