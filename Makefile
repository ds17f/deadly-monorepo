.PHONY: dev dev-up dev-down dev-logs dev-ps api-dev api-install api-build api-typecheck
.PHONY: docker-remote-pull docker-remote-up docker-remote-down docker-remote-destroy docker-remote-logs docker-remote-ps docker-remote-redeploy docker-remote-redeploy-logs api-remote-dev api-remote-health
.PHONY: help docs-help docs-install docs-build docs-serve docs-clean docs-pr
.PHONY: ui-install ui-dev ui-build ui-typecheck ui-data
.PHONY: ui-remote-install ui-remote-dev ui-remote-build ui-remote-dev-build ui-dev-build
.PHONY: android-release android-release-version android-release-dry-run android-install
.PHONY: ios-release ios-release-version ios-release-dry-run
.PHONY: setup-signing setup-github-secrets setup-api-secrets setup-hooks
.PHONY: android-build-release android-build-bundle android-deploy-testing
.PHONY: android-promote-alpha android-promote-beta android-promote-production
.PHONY: ios-build-release ios-deploy-testflight
.PHONY: ios-remote-unlock ios-remote-build ios-remote-install ios-remote-sim ios-remote-test ios-remote-resolve
.PHONY: android-remote-build android-remote-install
.PHONY: android-remote-emulator android-remote-emu-list android-remote-emu-stop android-remote-run-emulator
.PHONY: android-auto-dhu android-remote-auto-dhu
.PHONY: ios-build ios-sim ios-test ios-resolve ios-device ios-log
.PHONY: infra-init infra-plan infra-apply infra-retry infra-destroy infra-output infra-deploy infra-logs infra-ssh
.PHONY: data-download data-generate data-package data-download-stage01 data-upload-stage01 data-collect data-release data-clean
.PHONY: db-backup-list db-restore

# =============================================================================
# API & DOCKER COMPOSE
# =============================================================================

# Start full stack locally (Docker Compose dev mode)
dev-up:
	docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d

# Stop the local stack
dev-down:
	docker compose -f docker-compose.yml -f docker-compose.dev.yml down

# View logs from all services
dev-logs:
	docker compose -f docker-compose.yml -f docker-compose.dev.yml logs -f

# Show running service status
dev-ps:
	docker compose -f docker-compose.yml -f docker-compose.dev.yml ps

# Run API locally without Docker (for quick iteration)
api-dev:
	cd api && npm run dev

# Install API dependencies
api-install:
	cd api && npm install

# Build API TypeScript
api-build:
	cd api && npm run build

# Type-check API
api-typecheck:
	cd api && npm run typecheck

# =============================================================================
# UI (Next.js)
# =============================================================================

DEAD_METADATA  ?= data/stage02-generated-data
DATA_VERSION   ?= $(shell cat data/version)

# Install UI dependencies
ui-install:
	cd ui && npm install

# Run UI dev server
ui-dev:
	cd ui && npm run dev

# Build static UI export
ui-build:
	cd ui && npm run build

# Fast dev build — only 10 pages for quick iteration
ui-dev-build:
	cd ui && DEV_PAGES=10 npm run build

# Type-check UI
ui-typecheck:
	cd ui && npx tsc --noEmit

# Copy dataset from dead-metadata into ui/data/
ui-data:
	@echo "Copying dataset from $(DEAD_METADATA) to ui/data/..."
	@mkdir -p ui/data
	@cp -r $(DEAD_METADATA)/shows ui/data/
	@cp -r $(DEAD_METADATA)/recordings ui/data/
	@if [ -f $(DEAD_METADATA)/collections.json ]; then cp $(DEAD_METADATA)/collections.json ui/data/; fi
	@echo "Done. $$(ls ui/data/shows/ | wc -l) shows, $$(ls ui/data/recordings/ | wc -l) recordings."

# UI Remote targets (Linux → Mac)
ui-remote-install:
	@echo "Installing UI deps on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "$(REMOTE_ENVPATH) && cd $(REMOTE_PATH)/ui && rm -rf node_modules .next && npm install"

ui-remote-dev:
	@echo "Starting UI dev server on $(REMOTE_HOST)... (ctrl-c to stop)"
	@ssh -t $(REMOTE_HOST) "$(REMOTE_ENVPATH) && cd $(REMOTE_PATH)/ui && npm run dev"

ui-remote-build:
	@echo "Building UI on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "$(REMOTE_ENVPATH) && cd $(REMOTE_PATH)/ui && npm run build"

# Fast dev build — only generates 10 pages (middle of the catalog so prev/next works)
ui-remote-dev-build:
	@echo "Building UI (dev, 10 pages) on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "$(REMOTE_ENVPATH) && cd $(REMOTE_PATH)/ui && DEV_PAGES=10 npm run build"

# Default target shows all available commands
help:
	@echo "Deadly Monorepo - Available Make Targets"
	@echo "========================================"
	@echo ""
	@echo "API & LOCAL DEV:"
	@echo "  dev-up           - Start full stack locally (Caddy + API + Redis via Docker Compose)"
	@echo "  dev-down         - Stop the local stack"
	@echo "  dev-logs         - View logs from all services"
	@echo "  dev-ps           - Show running service status"
	@echo "  api-dev          - Run API locally without Docker (fast iteration)"
	@echo "  api-install      - Install API dependencies"
	@echo "  api-build        - Build API TypeScript"
	@echo "  api-typecheck    - Type-check API"
	@echo ""
	@echo "UI (Next.js):"
	@echo "  ui-install       - Install UI dependencies"
	@echo "  ui-dev           - Run UI dev server"
	@echo "  ui-build         - Build static UI export"
	@echo "  ui-typecheck     - Type-check UI"
	@echo "  ui-data          - Copy dataset from dead-metadata into ui/data/"
	@echo ""
	@echo "UI REMOTE (Linux → Mac):"
	@echo "  ui-remote-install  - Install UI deps on Mac"
	@echo "  ui-remote-dev      - Run UI dev server on Mac"
	@echo "  ui-remote-build    - Build static UI on Mac"
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
	@echo "  android-auto-dhu         - Launch Android Auto Desktop Head Unit"
	@echo ""
	@echo "ANDROID PROMOTIONS:"
	@echo "  android-promote-alpha      - Promote internal build to closed alpha (triggers workflow)"
	@echo "  android-promote-beta       - Promote internal build to open testing (triggers workflow)"
	@echo "  android-promote-production - Promote alpha build to production (triggers workflow)"
	@echo ""
	@echo "SIGNING & SECRETS:"
	@echo "  setup-hooks          - Configure git to use .githooks/ (run once after clone)"
	@echo "  setup-signing        - Generate keystore and .secrets/ setup"
	@echo "  setup-github-secrets - Upload all secrets to GitHub repository"
	@echo "  setup-api-secrets    - Upload API/OAuth secrets to GitHub 'alpha' environment"
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
	@echo "  ios-sim              - Build + run on iPhone 17 simulator"
	@echo "  ios-device           - Build + install to connected device"
	@echo "  ios-test             - Run tests on simulator"
	@echo "  ios-resolve          - Resolve SPM package dependencies"
	@echo "  ios-log              - Stream app logs from simulator via os_log"
	@echo ""
	@echo "DOCKER REMOTE (Linux → Mac):"
	@echo "  docker-remote-up   - Start full stack on Mac (Docker Compose)"
	@echo "  docker-remote-down    - Stop stack on Mac"
	@echo "  docker-remote-destroy - Destroy stack on Mac (clean rebuild)"
	@echo "  docker-remote-logs - View logs from remote stack"
	@echo "  docker-remote-ps   - Show remote service status"
	@echo "  docker-remote-redeploy      - Destroy + rebuild + start stack on Mac"
	@echo "  docker-remote-redeploy-logs - Destroy + rebuild + start + tail logs"
	@echo "  docker-remote-pull - Pre-pull base images on Mac"
	@echo ""
	@echo "API REMOTE (Linux → Mac):"
	@echo "  api-remote-dev     - Run API directly on Mac (no Docker)"
	@echo "  api-remote-health  - Health check against remote API"
	@echo ""
	@echo "ANDROID REMOTE BUILD (Linux → Mac):"
	@echo "  android-remote-build       - Build debug APK on Mac"
	@echo "  android-remote-install     - Build + install to connected Android device"
	@echo "  android-remote-emulator    - Start Android emulator on Mac"
	@echo "  android-remote-emu-list    - List available AVDs on Mac"
	@echo "  android-remote-emu-stop    - Stop all running emulators on Mac"
	@echo "  android-remote-run-emulator - Start emulator + build + install + launch on Mac"
	@echo "  android-remote-auto-dhu   - Launch Android Auto DHU on Mac"
	@echo ""
	@echo "IOS REMOTE BUILD (Linux → Mac):"
	@echo "  ios-remote-unlock    - Unlock Mac keychain for SSH code signing (once per reboot)"
	@echo "  ios-remote-build     - Build debug on Mac simulator"
	@echo "  ios-remote-install   - Build + install to connected device"
	@echo "  ios-remote-sim       - Build + run on iPhone 17 simulator"
	@echo "  ios-remote-test      - Run tests on Mac simulator"
	@echo "  ios-remote-resolve   - Resolve SPM package dependencies"
	@echo ""
	@echo "INFRASTRUCTURE (default: digitalocean, override: INFRA_PROVIDER=oci):"
	@echo "  infra-init       - Initialize Terraform providers"
	@echo "  infra-plan       - Preview infrastructure changes"
	@echo "  infra-apply      - Apply infrastructure changes"
	@echo "  infra-retry      - Retry OCI instance creation until capacity available (make infra-retry INTERVAL=5)"
	@echo "  infra-destroy    - Tear down all infrastructure"
	@echo "  infra-output     - Show current Terraform outputs (instance IP, etc.)"
	@echo "  infra-deploy     - Deploy alpha stack via GHA (current branch)"
	@echo "  infra-logs       - View production logs (SERVICE=api, LINES=100)"
	@echo "  infra-ssh        - SSH into production server"
	@echo "  setup-infra-secrets - Upload infra secrets (DO, B2, SSH) to GitHub"
	@echo ""
	@echo "DATA PIPELINE:"
	@echo "  data-download VERSION=X    - Download released data.zip + populate ui/data/"
	@echo "  data-generate              - Run stage02 generation (stage00+stage01 -> stage02)"
	@echo "  data-package               - Build data.zip from stage02"
	@echo "  data-download-stage01      - Fetch stage01 API cache from GitHub Release"
	@echo "  data-upload-stage01        - Publish stage01 API cache as GitHub Release"
	@echo "  data-collect               - Re-collect stage01 from APIs (rare, hours)"
	@echo "  data-release VERSION=X     - Tag + push, CI builds and publishes data.zip"
	@echo "  data-clean                 - Remove generated data artifacts"
	@echo ""
	@echo "DATABASE BACKUPS:"
	@echo "  db-backup-list   - List available database backups in B2"
	@echo "  db-restore       - Download latest backup (or specific: make db-restore BACKUP=users-XXX.db)"
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

setup-hooks:  ## Configure git to use .githooks/
	git config core.hooksPath .githooks
	chmod +x .githooks/commit-msg
	@echo "✅ Git hooks configured — feat and fix commits now require platform scope"

setup-signing:
	@./scripts/setup-signing.sh

setup-github-secrets:
	@./scripts/setup-github-secrets.sh

setup-api-secrets:
	@echo "Uploading API secrets to GitHub 'alpha' environment..."
	@gh secret set AUTH_SECRET --env alpha --body "$$(grep '^AUTH_SECRET=' api/.env | cut -d= -f2-)"
	@gh secret set AUTH_URL --env alpha --body "https://beta.thedeadly.app"
	@gh secret set GOOGLE_CLIENT_ID --env alpha --body "$$(grep '^GOOGLE_CLIENT_ID=' api/.env | cut -d= -f2-)"
	@gh secret set GOOGLE_CLIENT_SECRET --env alpha --body "$$(grep '^GOOGLE_CLIENT_SECRET=' api/.env | cut -d= -f2-)"
	@gh secret set APPLE_CLIENT_ID --env alpha --body "$$(grep '^APPLE_CLIENT_ID=' api/.env | cut -d= -f2-)"
	@gh secret set APPLE_TEAM_ID --env alpha --body "$$(grep '^APPLE_TEAM_ID=' api/.env | cut -d= -f2-)"
	@gh secret set APPLE_KEY_ID --env alpha --body "$$(grep '^APPLE_KEY_ID=' api/.env | cut -d= -f2-)"
	@gh secret set APPLE_PRIVATE_KEY --env alpha --body "$$(grep '^APPLE_PRIVATE_KEY=' api/.env | cut -d= -f2-)"
	@gh secret set ANALYTICS_API_KEY --env alpha --body "$$(grep '^ANALYTICS_API_KEY=' api/.env | cut -d= -f2-)"
	@echo "Done. Secrets set in 'alpha' environment."

# =============================================================================
# ANDROID FASTLANE
# =============================================================================

android-install:
	cd androidApp && ./gradlew installDebug

# Launch Android Auto Desktop Head Unit (requires emulator/device with head unit server running)
android-auto-dhu:
	adb forward tcp:5277 tcp:5277
	$(ANDROID_HOME)/extras/google/auto/desktop-head-unit

android-promote-alpha:
	gh workflow run android-promote.yml -f stage=alpha \
		-f version=$(shell grep VERSION_NAME androidApp/version.properties | cut -d= -f2)

android-promote-beta:
	gh workflow run android-promote.yml -f stage=beta \
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

REMOTE_HOST    ?= dsilbergleithcu@worklaptop.local
REMOTE_PATH    ?= ~/Developer/ai/claude-personal/container-home/workspace/Developer/deadly-monorepo
REMOTE_IOS     ?= $(REMOTE_PATH)/iosApp
REMOTE_ANDROID ?= $(REMOTE_PATH)/androidApp

# Build debug on Mac
ios-remote-build:
	@echo "Building on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "cd $(REMOTE_IOS) && xcodebuild -project deadly.xcodeproj -scheme deadly -configuration Debug -destination 'generic/platform=iOS Simulator' build 2>&1 | tail -20"

# Unlock the Mac's login keychain (required once per reboot for SSH code signing)
ios-remote-unlock:
	@echo "Unlocking keychain on $(REMOTE_HOST)..."
	@ssh -t $(REMOTE_HOST) "security unlock-keychain ~/Library/Keychains/login.keychain-db"

# Build + install to connected device (requires USB-connected iPhone + KEYCHAIN_PASSWORD env var)
ios-remote-install:
	@echo "Building on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "security unlock-keychain -p '$(KEYCHAIN_PASSWORD)' ~/Library/Keychains/login.keychain-db && cd $(REMOTE_IOS) && xcodebuild -project deadly.xcodeproj -scheme deadly -configuration Debug -destination 'generic/platform=iOS' -allowProvisioningUpdates build 2>&1 | tail -20"
	@echo "Installing to device..."
	@ssh $(REMOTE_HOST) 'DEVICE_ID=$$(xcrun devicectl list devices 2>/dev/null | grep -oE "[0-9A-F]{8}-([0-9A-F]{4}-){3}[0-9A-F]{12}" | head -1) && APP_PATH=$$(cd $(REMOTE_IOS) && xcodebuild -project deadly.xcodeproj -scheme deadly -configuration Debug -destination "generic/platform=iOS" -showBuildSettings 2>/dev/null | grep " BUILT_PRODUCTS_DIR" | head -1 | awk "{print \$$3}")/deadly.app && xcrun devicectl device install app --device "$$DEVICE_ID" "$$APP_PATH" 2>&1'

# Build + launch on iPhone 17 simulator
ios-remote-sim:
	@echo "Building for simulator on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "cd $(REMOTE_IOS) && xcodebuild -project deadly.xcodeproj -scheme deadly -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 17' build 2>&1 | tail -20"
	@echo "Launching on simulator..."
	@ssh $(REMOTE_HOST) 'APP_PATH=$$(cd $(REMOTE_IOS) && xcodebuild -project deadly.xcodeproj -scheme deadly -configuration Debug -destination "platform=iOS Simulator,name=iPhone 17" -showBuildSettings 2>/dev/null | grep " BUILT_PRODUCTS_DIR" | head -1 | awk "{print \$$3}")/deadly.app && xcrun simctl boot "iPhone 17" 2>/dev/null; xcrun simctl install booted "$$APP_PATH" && xcrun simctl launch booted com.grateful.deadly && open -a Simulator'

# Run tests on Mac simulator
ios-remote-test:
	@echo "Running tests on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "cd $(REMOTE_IOS) && xcodebuild test -project deadly.xcodeproj -scheme deadly -destination 'platform=iOS Simulator,name=iPhone 17' 2>&1 | tail -40"

# Resolve SPM packages on Mac (needed after adding new dependencies)
ios-remote-resolve:
	@echo "Resolving packages on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "cd $(REMOTE_IOS) && xcodebuild -resolvePackageDependencies -project deadly.xcodeproj 2>&1 | tail -20"

# =============================================================================
# ANDROID REMOTE BUILD (Linux → Mac)
# =============================================================================

android-remote-build:
	@echo "Building on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "export ANDROID_HOME=\$$HOME/Library/Android/sdk && cd $(REMOTE_ANDROID) && ./gradlew assembleDebug --console=plain"

android-remote-install:
	@echo "Building + installing on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "export ANDROID_HOME=\$$HOME/Library/Android/sdk && cd $(REMOTE_ANDROID) && ./gradlew installDebug --console=plain"

android-remote-emulator:
	@echo "Starting emulator on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "cd $(REMOTE_ANDROID) && make emu-start"

android-remote-emu-list:
	@ssh $(REMOTE_HOST) "cd $(REMOTE_ANDROID) && make emu-list"

android-remote-emu-stop:
	@echo "Stopping emulators on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "cd $(REMOTE_ANDROID) && make emu-stop"

android-remote-run-emulator:
	@echo "Starting emulator workflow on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "export ANDROID_HOME=\$$HOME/Library/Android/sdk && export PATH=\$$PATH:\$$ANDROID_HOME/platform-tools:\$$ANDROID_HOME/emulator && cd $(REMOTE_ANDROID) && \
		if adb devices 2>/dev/null | grep -q 'emulator.*device\$$'; then \
			echo 'Reusing running emulator...'; \
			./gradlew installDebug && adb shell am start -n com.grateful.deadly.debug/com.grateful.deadly.MainActivity; \
		else \
			make run-emulator; \
		fi"

# Launch Android Auto DHU on Mac (forwards port from device and starts DHU)
android-remote-auto-dhu:
	@echo "Launching Android Auto DHU on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "export ANDROID_HOME=\$$HOME/Library/Android/sdk && export PATH=\$$PATH:\$$ANDROID_HOME/platform-tools && adb forward tcp:5277 tcp:5277 && \$$ANDROID_HOME/extras/google/auto/desktop-head-unit"

# =============================================================================
# IOS LOCAL BUILD (macOS)
# =============================================================================

IOS_DIR     := iosApp
IOS_PROJECT := $(IOS_DIR)/deadly.xcodeproj
IOS_SCHEME  := deadly
SIM_DEST    := platform=iOS Simulator,name=iPhone 17
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

# Build + run on iPhone 17 simulator
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
	&& xcrun simctl boot "iPhone 17" 2>/dev/null; \
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

# =============================================================================
# API REMOTE (Linux → Mac)
# =============================================================================

REMOTE_BREW    := /usr/local/opt
REMOTE_DOCKER  := /Applications/Docker.app/Contents/Resources/bin
REMOTE_ENVPATH := export PATH=$(REMOTE_DOCKER):$(REMOTE_BREW)/node@22/bin:/usr/local/bin:$$PATH

# Pre-pull base images (avoids keychain auth issues over SSH)
docker-remote-pull:
	@echo "Pulling base images on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "$(REMOTE_ENVPATH) && docker pull node:22-slim && docker pull caddy:2-alpine && docker pull redis:7-alpine"

# Build + start full stack on remote Mac (requires KEYCHAIN_PASSWORD env var)
docker-remote-up:
	@echo "Starting stack on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "security unlock-keychain -p '$(KEYCHAIN_PASSWORD)' ~/Library/Keychains/login.keychain-db && $(REMOTE_ENVPATH) && cd $(REMOTE_PATH) && docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build -d"

# Stop stack on remote Mac
docker-remote-down:
	@ssh $(REMOTE_HOST) "$(REMOTE_ENVPATH) && cd $(REMOTE_PATH) && docker compose -f docker-compose.yml -f docker-compose.dev.yml down"

# Destroy stack on remote Mac (removes containers, images, and volumes for a clean rebuild)
docker-remote-destroy:
	@echo "Destroying stack on $(REMOTE_HOST)..."
	@ssh $(REMOTE_HOST) "$(REMOTE_ENVPATH) && cd $(REMOTE_PATH) && docker compose -f docker-compose.yml -f docker-compose.dev.yml down --rmi local -v"

# View logs from remote stack
docker-remote-logs:
	@ssh $(REMOTE_HOST) "$(REMOTE_ENVPATH) && cd $(REMOTE_PATH) && docker compose -f docker-compose.yml -f docker-compose.dev.yml logs -f"

# Destroy + rebuild + start stack on remote Mac
docker-remote-redeploy: docker-remote-destroy docker-remote-up

# Destroy + rebuild + start + tail logs on remote Mac
docker-remote-redeploy-logs: docker-remote-redeploy docker-remote-logs

# Show remote service status
docker-remote-ps:
	@ssh $(REMOTE_HOST) "$(REMOTE_ENVPATH) && cd $(REMOTE_PATH) && docker compose -f docker-compose.yml -f docker-compose.dev.yml ps"

# Run API directly on remote Mac (no Docker, fast iteration)
api-remote-dev:
	@echo "Starting API on $(REMOTE_HOST)..."
	@ssh -t $(REMOTE_HOST) "$(REMOTE_ENVPATH) && cd $(REMOTE_PATH)/api && npm rebuild better-sqlite3 2>/dev/null; npm install && npm run dev"

# Health check against remote API
api-remote-health:
	@curl -s http://worklaptop.local:3001/api/health | python3 -m json.tool

# =============================================================================
# INFRASTRUCTURE (OCI)
# =============================================================================

TERRAFORM      ?= terraform
INTERVAL       ?= 5
INFRA_PROVIDER ?= digitalocean
INFRA_DIR      := infra/$(INFRA_PROVIDER)

infra-init:
	@cd $(INFRA_DIR) && $(TERRAFORM) init

infra-plan:
	@cd $(INFRA_DIR) && $(TERRAFORM) plan

infra-apply:
	@cd $(INFRA_DIR) && $(TERRAFORM) apply

# Retry instance creation, cycling through ADs every INTERVAL minutes.
# OCI free tier A1.Flex capacity is often exhausted — this will keep trying.
#   make infra-retry              # retry every 5 minutes (default)
#   make infra-retry INTERVAL=10  # retry every 10 minutes
infra-retry:
	@cd infra/oci && TERRAFORM=$(TERRAFORM) ./retry-apply.sh $(INTERVAL)

infra-destroy:
	@cd $(INFRA_DIR) && $(TERRAFORM) destroy

infra-output:
	@cd $(INFRA_DIR) && $(TERRAFORM) output

# Deploy alpha stack via GHA (triggers infra-deploy.yml)
infra-deploy:
	gh workflow run infra-deploy.yml -f ref=$(shell git rev-parse --abbrev-ref HEAD)

# Resolve production server IP from DigitalOcean API (uses token from terraform.tfvars)
PROD_SSH_KEY   ?= ssh-key-2026-03-15.key
PROD_DO_TOKEN  = $(shell grep '^do_token' infra/digitalocean/terraform.tfvars 2>/dev/null | cut -d'"' -f2)
PROD_IP       ?= $(shell curl -sf -H "Authorization: Bearer $(PROD_DO_TOKEN)" \
                  "https://api.digitalocean.com/v2/droplets?tag_name=deadly" \
                  | python3 -c "import sys,json;d=json.load(sys.stdin)['droplets'][0]['networks']['v4'];print(next(n['ip_address'] for n in d if n['type']=='public'))" 2>/dev/null)

# View logs from production server (all services, or specify SERVICE=api)
#   make infra-logs                # all services, last 100 lines + follow
#   make infra-logs SERVICE=api    # only API logs
#   make infra-logs LINES=500      # last 500 lines
infra-logs:
	@ssh -i $(PROD_SSH_KEY) deploy@$(PROD_IP) "cd /opt/deadly && docker compose logs $(SERVICE) --tail $(or $(LINES),100) -f"

# SSH into production server
infra-ssh:
	@ssh -i $(PROD_SSH_KEY) deploy@$(PROD_IP)

# =============================================================================
# DATA PIPELINE (delegates to data/Makefile)
# =============================================================================

# Download released data.zip and populate ui/data/ for local dev
data-download:
	@if [ -z "$(VERSION)" ]; then \
		echo "VERSION not specified. Usage: make data-download VERSION=2.3.0"; \
		exit 1; \
	fi
	@$(MAKE) -C data download-data VERSION=$(VERSION)
	@echo "Copying to ui/data/..."
	@mkdir -p ui/data
	@cp -r data/stage02-generated-data/shows ui/data/
	@cp -r data/stage02-generated-data/recordings ui/data/
	@if [ -f data/stage02-generated-data/collections.json ]; then cp data/stage02-generated-data/collections.json ui/data/; fi
	@echo "Done. $$(ls ui/data/shows/ | wc -l) shows, $$(ls ui/data/recordings/ | wc -l) recordings."

# Run generation scripts to build stage02 from stage00+stage01
data-generate:
	@$(MAKE) -C data stage02-generate-data

# Build data.zip from stage02
data-package:
	@$(MAKE) -C data package-data

# Download stage01 API cache from GitHub Release
data-download-stage01:
	@$(MAKE) -C data download-stage01

# Upload stage01 API cache to GitHub Release
data-upload-stage01:
	@$(MAKE) -C data upload-stage01

# Re-collect stage01 from APIs (rare, takes hours)
data-collect:
	@$(MAKE) -C data stage01-collect-data

# Tag and push a data release — CI builds and publishes data.zip
data-release:
	@if [ -z "$(VERSION)" ]; then \
		echo "VERSION not specified. Usage: make data-release VERSION=2.4.0"; \
		exit 1; \
	fi
	@echo "Updating data/version to $(VERSION)..."
	@echo "$(VERSION)" > data/version
	@echo "Creating tag data-v$(VERSION)..."
	git add data/version
	git commit -m "chore(all/data): bump data version to $(VERSION)"
	git tag "data-v$(VERSION)"
	@echo "Push the tag to trigger CI: git push origin main data-v$(VERSION)"

# Remove generated data artifacts
data-clean:
	@$(MAKE) -C data clean

# =============================================================================
# DATABASE BACKUPS (B2)
# =============================================================================

B2_ENDPOINT    := https://s3.us-west-004.backblazeb2.com
B2_BACKUP_PATH := s3://deadly-backups/db

# List available database backups in B2
db-backup-list:
	@aws s3 ls $(B2_BACKUP_PATH)/ --endpoint-url $(B2_ENDPOINT) --region us-west-004

# Download latest (or specific) backup into local api-data/
#   make db-restore                          # latest backup
#   make db-restore BACKUP=users-20260317T120000Z.db  # specific backup
db-restore:
	@mkdir -p api-data
	@if [ -n "$(BACKUP)" ]; then \
		echo "Downloading $(BACKUP)..."; \
		aws s3 cp $(B2_BACKUP_PATH)/$(BACKUP) api-data/users.db \
			--endpoint-url $(B2_ENDPOINT) --region us-west-004; \
	else \
		echo "Downloading latest backup..."; \
		LATEST=$$(aws s3 ls $(B2_BACKUP_PATH)/ --endpoint-url $(B2_ENDPOINT) --region us-west-004 \
			| sort | tail -1 | awk '{print $$4}'); \
		if [ -z "$$LATEST" ]; then echo "No backups found"; exit 1; fi; \
		echo "Found: $$LATEST"; \
		aws s3 cp $(B2_BACKUP_PATH)/$$LATEST api-data/users.db \
			--endpoint-url $(B2_ENDPOINT) --region us-west-004; \
	fi
	@echo "Restored to api-data/users.db"
