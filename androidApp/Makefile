# Deadly Android App - Makefile
# Simplifies common development tasks

.PHONY: help build clean test lint install run run-emulator debug release tag-release tag-release-quick update-release-descriptions dry-run-release setup deps check status logs capture-test-data clean-test-data view-test-data theme-install theme-remove theme-status

# Default target
help:
	@echo "Deadly Android App - Available Commands:"
	@echo ""
	@echo "Setup & Dependencies:"
	@echo "  make setup       - Initial project setup and dependency installation"
	@echo "  make deps        - Download and install dependencies"
	@echo "  make download-icons - Download Material icons and update resources"
	@echo "  make collect-metadata-full - Full metadata collection (2-3 hours)"
	@echo "  make collect-metadata-test - Test collection (10 recordings)"
	@echo "  make collect-metadata-1977 - Collect 1977 shows (golden year)"
	@echo "  make collect-metadata-1995 - Collect 1995 shows (final year, good for TIGDH)"
	@echo "  make generate-ratings-from-cache - Generate ratings from cached data"
	@echo "  make generate-ratings - Alias for collect-metadata-test"
	@echo "  make collect-setlists-full - Collect all setlists from CMU (1972-1995)"
	@echo "  make collect-setlists-year YEAR=1977 - Collect setlists for a specific year"
	@echo "  make collect-gdsets-full - Collect all setlists and images from GDSets.com"
	@echo "  make merge-setlists - Merge CMU and GDSets setlist data (full range)"
	@echo "  make merge-setlists-early - Merge CMU with early years GDSets data"
	@echo "  make process-venues - Process and normalize venue data from merged setlists"
	@echo "  make process-songs - Process and normalize song data from merged setlists"
	@echo "  make integrate-setlists - Integrate setlists with venue and song IDs"
	@echo "  make collect-gdsets-early - Collect early years (1965-1971) from GDSets.com"
	@echo "  make collect-gdsets-images - Collect only show images from GDSets.com"
	@echo "  make package-datazip - Package all metadata into data.zip for app deployment"
	@echo ""
	@echo "Build Commands:"
	@echo "  make build       - Build debug APK"
	@echo "  make release     - Build release APK"
	@echo "  make tag-release - Run tests/lint/builds, then create release version and tag"
	@echo "  make tag-release-quick - Skip quality checks and create release version and tag (faster)"
	@echo "  make update-release-descriptions - Update all existing GitHub release descriptions with changelog content"
	@echo "  make dry-run-release - Test full release process including quality checks"
	@echo "  make clean       - Clean build artifacts"
	@echo ""
	@echo "Development:"
	@echo "  make install     - Install debug APK to connected device"
	@echo "  make install-quiet - Install debug APK (quiet mode, output to file)"
	@echo "  make run         - Install and run app on device"  
	@echo "  make run-emulator - Complete workflow: start emulator + build + install + launch"
	@echo "  make debug       - Build and install debug APK"
	@echo ""
	@echo "Quality Assurance:"
	@echo "  make test        - Run all unit tests"
	@echo "  make lint        - Run lint checks"
	@echo "  make check       - Run tests and lint"
	@echo ""
	@echo "Device & Emulator:"
	@echo "  make devices     - Show connected devices and emulators"
	@echo "  make emulator    - Start Android emulator (first available AVD)"
	@echo "  make emu-list    - List all available Android Virtual Devices"
	@echo "  make emu-stop    - Stop all running emulators"
	@echo "  make emu-cold-boot - Perform a cold boot of the emulator with virtual audio"
	@echo ""
	@echo "Theme Management:"
	@echo "  make theme-install - Install Grateful Dead Classic theme"
	@echo "  make theme-remove  - Remove all custom themes (use default)"
	@echo "  make theme-status  - Check current theme status"
	@echo ""
	@echo "Test Data Management:"
	@echo "  make capture-test-data - Pull test data exported by debug screen"
	@echo "  make clean-test-data   - Clear app data and remove test data files"  
	@echo "  make view-test-data    - Show info about captured test data"
	@echo ""
	@echo "Utilities:"
	@echo "  make status      - Show project and device status"
	@echo "  make logs        - Show app logs from connected device"
	@echo "  make modules     - List all project modules"
	@echo "  make deps-tree   - Show dependency tree"
	@echo ""
	@echo "Documentation:"
	@echo "  make view-db-schema - Generate and view database schema diagram"
	@echo ""
	@echo "Cleanup:"
	@echo "  make deep-clean  - Clean everything including Gradle cache"
	@echo "  make reset       - Reset project to clean state"

# Setup and Dependencies
setup:
	@echo "🚀 Setting up Deadly project..."
	gradle --version
	@echo "✅ Project setup complete!"

deps:
	@echo "📦 Installing dependencies..."
	gradle build --refresh-dependencies
	@echo "✅ Dependencies installed!"

# Build Commands
build:
	@echo "🔨 Building debug APK..."
	@if gradle assembleDebug --console=plain > build-output.log 2>&1; then \
		echo "✅ Debug APK built successfully!"; \
	else \
		echo "❌ Debug build failed! Check build-output.log for details"; \
		exit 1; \
	fi

release:
	@echo "🔨 Building release APK..."
	@if gradle assembleRelease --console=plain > release-output.log 2>&1; then \
		echo "✅ Release APK built successfully!"; \
	else \
		echo "❌ Release build failed! Check release-output.log for details"; \
		exit 1; \
	fi

tag-release:
	@echo "🚀 Creating new release version..."
	@echo "🔍 Checking git working directory..."
	@if [ -n "$$(git status --porcelain)" ]; then \
		echo "⚠️ Working directory has uncommitted changes - proceeding anyway"; \
		git status --porcelain; \
	else \
		echo "✅ Working directory is clean"; \
	fi
	@echo "1️⃣ Running quality checks and builds before release..."
	@$(MAKE) --no-print-directory lint
	@$(MAKE) --no-print-directory build
	@echo "2️⃣ Attempting release build (may fail due to signing requirements)..."
	@if $(MAKE) --no-print-directory release > /dev/null 2>&1; then \
		echo "✅ Release build succeeded!"; \
	else \
		echo "⚠️ Release build failed (likely due to signing), but debug build passed"; \
		echo "🔍 This is normal in CI/CD environments without signing keys"; \
	fi
	@echo "3️⃣ All quality checks passed! Proceeding with release..."
	@chmod +x ./scripts/release.sh
	@./scripts/release.sh
	@echo "✅ Release tagged and pushed! GitHub Actions will build the artifacts."

tag-release-quick:
	@echo "⚡ Creating new release version (quick mode - skipping quality checks)..."
	@echo "🔍 Checking git working directory..."
	@if [ -n "$$(git status --porcelain)" ]; then \
		echo "⚠️ Working directory has uncommitted changes - proceeding anyway"; \
		git status --porcelain; \
	else \
		echo "✅ Working directory is clean"; \
	fi
	@echo "🚀 Skipping quality checks and builds for faster release..."
	@echo "⚠️ WARNING: This bypasses lint checks and builds - use with caution!"
	@chmod +x ./scripts/release.sh
	@./scripts/release.sh
	@echo "✅ Release tagged and pushed! GitHub Actions will build the artifacts."

update-release-descriptions:
	@echo "🚀 Updating all GitHub release descriptions with changelog content..."
	@chmod +x ./scripts/update_release_descriptions.sh
	@./scripts/update_release_descriptions.sh
	@echo "✅ GitHub release descriptions updated! Changes are immediately visible on GitHub."

dry-run-release:
	@echo "🧪 Testing release process (dry run)..."
	@echo "🔍 Checking git working directory..."
	@if [ -n "$$(git status --porcelain)" ]; then \
		echo "❌ Working directory not clean! Please commit or stash changes first."; \
		git status; \
		exit 1; \
	fi
	@echo "✅ Working directory is clean"
	@echo "1️⃣ Running quality checks and builds to validate release readiness..."
	@$(MAKE) --no-print-directory lint
	@$(MAKE) --no-print-directory build
	@echo "2️⃣ Attempting release build (may fail due to signing requirements)..."
	@if $(MAKE) --no-print-directory release > /dev/null 2>&1; then \
		echo "✅ Release build succeeded!"; \
	else \
		echo "⚠️ Release build failed (likely due to signing), but debug build passed"; \
		echo "🔍 This is normal in CI/CD environments without signing keys"; \
	fi
	@echo "3️⃣ All quality checks passed! Proceeding with dry run..."
	@chmod +x ./scripts/release.sh
	@./scripts/release.sh --dry-run
	@echo "💡 To perform an actual release, use 'make tag-release'"

clean:
	@echo "🧹 Cleaning build artifacts and cache..."
	gradle clean --no-configuration-cache
	rm -rf .gradle/
	rm -rf build/
	rm -rf */build/
	rm -rf */*/build/
	@echo "✅ Clean complete!"

# Development
install:
	@echo "📱 Installing debug APK to device..."
	gradle installDebug --console=plain 2>&1 | tee install-output.log
	@echo "✅ App installed! Output saved to install-output.log"

install-quiet:
	@echo "📱 Installing debug APK to device (quiet mode)..."
	@if gradle installDebug --console=plain > install-output.log 2>&1; then \
		echo "✅ App installed successfully!"; \
	else \
		echo "❌ Install failed! Check install-output.log for details"; \
		exit 1; \
	fi

run: install
	@echo "🚀 Launching Deadly app..."
	adb shell am start -n com.grateful.deadly/.MainActivity
	@echo "✅ App launched!"

run-emulator:
	@echo "🎯 Starting complete emulator workflow..."
	@echo "1️⃣ Starting emulator..."
	@$(MAKE) --no-print-directory emu-start
	@echo ""
	@echo "2️⃣ Waiting for emulator to be ready..."
	@echo "⏳ This may take 30-60 seconds for first boot..."
	@for i in $$(seq 1 60); do \
		if adb devices 2>/dev/null | grep -q "emulator.*device$$"; then \
			echo "✅ Emulator is ready!"; \
			break; \
		fi; \
		if [ $$i -eq 60 ]; then \
			echo "❌ Timeout waiting for emulator"; \
			echo "💡 Try manually: make devices"; \
			exit 1; \
		fi; \
		printf "⏳ Waiting... ($$i/60)\r"; \
		sleep 2; \
	done
	@echo ""
	@echo "3️⃣ Building and installing Deadly app..."
	@$(MAKE) --no-print-directory build
	@$(MAKE) --no-print-directory install
	@echo ""
	@echo "4️⃣ Launching app..."
	@adb shell am start -n com.deadly.app/.MainActivity
	@echo ""
	@echo "🎉 SUCCESS! Deadly is now running on emulator!"
	@echo "📱 Use 'make emu-stop' when done"

debug: build install
	@echo "🐛 Debug build installed and ready!"

# Quality Assurance
test:
	@echo "🧪 Running unit tests..."
	@if gradle test --console=plain > test-output.log 2>&1; then \
		echo "✅ Tests passed!"; \
	else \
		echo "❌ Tests failed! Check test-output.log for details"; \
		exit 1; \
	fi

lint:
	@echo "🔍 Running lint checks..."
	@if gradle lint --console=plain > lint-output.log 2>&1; then \
		echo "✅ Lint checks passed!"; \
	else \
		echo "❌ Lint checks failed! Check lint-output.log for details"; \
		exit 1; \
	fi

check: test lint
	@echo "✅ All quality checks completed!"

# Utilities
status:
	@echo "📊 Deadly Project Status:"
	@echo ""
	@echo "Gradle Version:"
	@gradle --version | head -5
	@echo ""
	@echo "Connected Devices:"
	@adb devices || echo "❌ ADB not available"
	@echo ""
	@echo "Project Structure:"
	@find . -name "build.gradle.kts" | head -5
	@echo ""
	@echo "Last Build Artifacts:"
	@find . -name "*.apk" -newer . 2>/dev/null | head -3 || echo "No recent APK builds found"

logs:
	@echo "📱 Showing Deadly app logs..."
	@echo "Press Ctrl+C to stop"
	adb logcat | grep -i "deadarchive\|grateful\|dead"

modules:
	@echo "📦 Deadly Modules:"
	@echo ""
	@echo "Core Modules:"
	@find core -name "build.gradle.kts" -exec dirname {} \; | sort
	@echo ""
	@echo "Feature Modules:"
	@find feature -name "build.gradle.kts" -exec dirname {} \; | sort
	@echo ""
	@echo "App Module:"
	@echo "app"

deps-tree:
	@echo "🌳 Dependency Tree:"
	gradle app:dependencies --configuration debugRuntimeClasspath

# Advanced Cleanup
deep-clean:
	@echo "🧹 Deep cleaning project..."
	gradle clean
	rm -rf .gradle/
	rm -rf build/
	rm -rf */build/
	rm -rf */*/build/
	@echo "✅ Deep clean complete!"

reset: deep-clean
	@echo "🔄 Resetting project to clean state..."
	gradle build --refresh-dependencies
	@echo "✅ Project reset complete!"


# Build variants
.PHONY: apk bundle
apk: build
	@echo "📱 Debug APK: app/build/outputs/apk/debug/app-debug.apk"

bundle:
	@echo "📦 Building Android App Bundle..."
	gradle bundleRelease
	@echo "✅ Bundle built: app/build/outputs/bundle/release/app-release.aab"

# Device management
.PHONY: devices install-release uninstall emulator emu-list emu-start emu-stop emu-cold-boot
devices:
	@echo "📱 Connected Android devices:"
	adb devices -l

install-release:
	@echo "📱 Installing release APK..."
	gradle installRelease

uninstall:
	@echo "🗑️ Uninstalling Deadly..."
	adb uninstall com.deadly.app || echo "App not installed"

# Emulator management
emu-list:
	@echo "📲 Available Android Virtual Devices:"
	@if command -v emulator >/dev/null 2>&1; then \
		emulator -list-avds; \
	elif [ -f "$$ANDROID_HOME/emulator/emulator" ]; then \
		$$ANDROID_HOME/emulator/emulator -list-avds; \
	elif [ -f "$$HOME/Library/Android/sdk/emulator/emulator" ]; then \
		$$HOME/Library/Android/sdk/emulator/emulator -list-avds; \
	elif [ -f "/usr/local/share/android-commandlinetools/emulator/emulator" ]; then \
		/usr/local/share/android-commandlinetools/emulator/emulator -list-avds; \
	else \
		echo "❌ Emulator not found. Searching common locations..."; \
		echo ""; \
		echo "Checking for Android SDK..."; \
		ls -la "$$HOME/Library/Android/sdk/emulator/" 2>/dev/null || echo "Not found: ~/Library/Android/sdk/emulator/"; \
		ls -la "$$ANDROID_HOME/emulator/" 2>/dev/null || echo "Not found: $$ANDROID_HOME/emulator/"; \
		echo ""; \
		echo "💡 To fix this:"; \
		echo "   1. Find your Android SDK path in Android Studio (Preferences > SDK Manager)"; \
		echo "   2. Add to your shell profile: export PATH=\$$PATH:\$$ANDROID_HOME/emulator"; \
		echo "   3. Or set ANDROID_HOME: export ANDROID_HOME=/path/to/your/android/sdk"; \
	fi

emulator: emu-start

emu-start:
	@echo "🚀 Starting Android emulator..."
	@EMULATOR_CMD=""; \
	if command -v emulator >/dev/null 2>&1; then \
		EMULATOR_CMD="emulator"; \
	elif [ -f "$$ANDROID_HOME/emulator/emulator" ]; then \
		EMULATOR_CMD="$$ANDROID_HOME/emulator/emulator"; \
	elif [ -f "$$HOME/Library/Android/sdk/emulator/emulator" ]; then \
		EMULATOR_CMD="$$HOME/Library/Android/sdk/emulator/emulator"; \
	elif [ -f "/usr/local/share/android-commandlinetools/emulator/emulator" ]; then \
		EMULATOR_CMD="/usr/local/share/android-commandlinetools/emulator/emulator"; \
	else \
		echo "❌ Emulator not found. Run 'make emu-list' for help."; \
		exit 1; \
	fi; \
	AVD_NAME=$$($$EMULATOR_CMD -list-avds 2>/dev/null | head -1); \
	if [ -z "$$AVD_NAME" ]; then \
		echo "❌ No AVDs available. Create one in Android Studio first."; \
		echo "💡 Android Studio > Tools > AVD Manager > Create Virtual Device"; \
		exit 1; \
	else \
		echo "📱 Starting emulator: $$AVD_NAME"; \
		$$EMULATOR_CMD -avd $$AVD_NAME -audio virtual -no-boot-anim & \
		echo "⏳ Emulator starting in background..."; \
		echo "🔍 Use 'make devices' to check when it's ready"; \
	fi

emu-stop:
	@echo "🛑 Stopping Android emulators..."
	@adb devices | grep emulator | cut -f1 | while read line; do adb -s $$line emu kill; done
	@echo "✅ All emulators stopped"

emu-cold-boot:
	@echo "❄️ Performing cold boot of Android emulator..."
	@EMULATOR_CMD=""; \
	if command -v emulator >/dev/null 2>&1; then \
		EMULATOR_CMD="emulator"; \
	elif [ -f "$$ANDROID_HOME/emulator/emulator" ]; then \
		EMULATOR_CMD="$$ANDROID_HOME/emulator/emulator"; \
	elif [ -f "$$HOME/Library/Android/sdk/emulator/emulator" ]; then \
		EMULATOR_CMD="$$HOME/Library/Android/sdk/emulator/emulator"; \
	elif [ -f "/usr/local/share/android-commandlinetools/emulator/emulator" ]; then \
		EMULATOR_CMD="/usr/local/share/android-commandlinetools/emulator/emulator"; \
	else \
		echo "❌ Emulator not found. Run 'make emu-list' for help."; \
		exit 1; \
	fi; \
	AVD_NAME=$$($$EMULATOR_CMD -list-avds 2>/dev/null | head -1); \
	if [ -z "$$AVD_NAME" ]; then \
		echo "❌ No AVDs available. Create one in Android Studio first."; \
		exit 1; \
	else \
		echo "📱 Stopping any running emulators..."; \
		adb devices | grep emulator | cut -f1 | while read line; do adb -s $$line emu kill; done; \
		sleep 2; \
		echo "📱 Cold booting emulator: $$AVD_NAME"; \
		$$EMULATOR_CMD -avd $$AVD_NAME -no-snapshot -audio virtual -no-boot-anim -verbose & \
		echo "⏳ Cold boot emulator starting in background..."; \
		echo "🔍 Use 'make devices' to check when it's ready"; \
	fi

# Performance and analysis
.PHONY: profile analyze size
profile:
	@echo "📊 Building with profiling enabled..."
	gradle assembleDebug -Pandroid.enableProfileJson=true

analyze:
	@echo "🔍 Running dependency analysis..."
	gradle buildHealth

size:
	@echo "📏 Analyzing APK size..."
	gradle analyzeDebugBundle || gradle assembleDebug
	@find . -name "*.apk" -exec ls -lh {} \;

# Documentation
.PHONY: docs
docs:
	@echo "📚 Generating documentation..."
	gradle dokkaHtml || echo "Dokka not configured"
	@echo "📖 Opening README..."
	@cat README.md | head -20

# Material Icons Management
.PHONY: download-icons
download-icons:
	@echo "🔍 Downloading Material Icons..."
	@cd scripts && \
		(test -d .venv || python3 -m venv .venv || virtualenv .venv) && \
		. .venv/bin/activate && \
		pip install --upgrade pip && \
		pip install -r requirements.txt && \
		python download_material_icons.py \
		--from-json "$(PWD)/scripts/material_icons_config.json" \
		--output "$(PWD)/core/design/src/main/res/drawable" \
		--icon-registry-path "$(PWD)/core/design/src/main/java/com/grateful/deadly/core/design/resources/IconResources.kt" \
		--update-registry
	@echo "✅ Icons downloaded and processed!"

# Comprehensive Metadata Collection
.PHONY: collect-metadata-full collect-metadata-test generate-ratings-from-cache collect-metadata-resume collect-setlists-full collect-setlists-year collect-gdsets-full collect-gdsets-early collect-gdsets-images merge-setlists merge-setlists-early process-venues process-songs integrate-setlists package-datazip

# Full metadata collection (2-3 hours, run overnight)
collect-metadata-full:
	@echo "⭐ Collecting complete Grateful Dead metadata from Archive.org..."
	@cd scripts && \
		rm -rf .venv && \
		python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt && \
		python generate_metadata.py \
		--mode full \
		--delay 0.25 \
		--cache "$(PWD)/scripts/metadata" \
		--output "$(PWD)/scripts/metadata/ratings.json" \
		--verbose
	@echo "✅ Complete metadata collection finished!"

# Test collection (small subset)
collect-metadata-test:
	@echo "🧪 Testing metadata collection with small subset..."
	@cd scripts && \
		rm -rf .venv && \
		python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt && \
		python generate_metadata.py \
		--mode test \
		--delay 0.25 \
		--cache "$(PWD)/scripts/metadata-test" \
		--output "$(PWD)/scripts/metadata/ratings.json" \
		--max-recordings 10 \
		--verbose
	@echo "✅ Test collection completed!"

# Generate ratings from existing cache (fast)
generate-ratings-from-cache:
	@echo "⚡ Generating ratings from cached metadata..."
	@cd scripts && \
		. .venv/bin/activate && \
		python generate_metadata.py \
		--mode ratings-only \
		--cache "$(PWD)/scripts/metadata" \
		--output "$(PWD)/scripts/metadata/ratings.json" \
		--verbose
	@echo "✅ Ratings generated from cache!"

# Resume interrupted collection
collect-metadata-resume:
	@echo "🔄 Resuming metadata collection..."
	@cd scripts && \
		. .venv/bin/activate && \
		python generate_metadata.py \
		--mode resume \
		--progress "$(PWD)/scripts/metadata/progress.json" \
		--verbose
	@echo "✅ Collection resumed and completed!"

# Collect 1977 data specifically (good balance of quality and quantity)
collect-metadata-1977:
	@echo "🎸 Collecting 1977 Grateful Dead metadata (the golden year)..."
	@cd scripts && \
		rm -rf .venv && \
		python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt && \
		python generate_metadata.py \
		--mode full \
		--year 1977 \
		--delay 0.5 \
		--cache "$(PWD)/scripts/metadata-1977" \
		--output "$(PWD)/scripts/metadata/ratings.json" \
		--max-recordings 100 \
		--verbose
	@echo "✅ 1977 collection completed!"

# Collect 1995 data specifically (final year, good for TIGDH testing)
collect-metadata-1995:
	@echo "🌹 Collecting 1995 Grateful Dead metadata (the final year)..."
	@cd scripts && \
		rm -rf .venv && \
		python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt && \
		python generate_metadata.py \
		--mode full \
		--year 1995 \
		--delay 0.5 \
		--cache "$(PWD)/scripts/metadata-1995" \
		--output "$(PWD)/scripts/metadata/ratings.json" \
		--verbose
	@echo "✅ 1995 collection completed!"

# Legacy alias for backward compatibility
generate-ratings: collect-metadata-test

# Setlist Collection
collect-setlists-full:
	@echo "⭐ Collecting complete Grateful Dead setlists from CS.CMU.EDU..."
	@cd scripts && \
		. .venv/bin/activate || (python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt) && \
		python scrape_cmu_setlists.py \
		--output "$(PWD)/scripts/metadata/setlists/cmu_setlists.json" \
		--delay 0.5 \
		--verbose
	@echo "✅ Complete setlist collection finished!"

collect-setlists-year:
	@if [ -z "$(YEAR)" ]; then \
		echo "⚠️  Error: YEAR parameter is required."; \
		echo "Usage: make collect-setlists-year YEAR=1977"; \
		exit 1; \
	fi
	@echo "🎸 Collecting setlists for year $(YEAR)..."
	@cd scripts && \
		. .venv/bin/activate || (python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt) && \
		python scrape_cmu_setlists.py \
		--output "$(PWD)/scripts/metadata/setlists/cmu_setlists_$(YEAR).json" \
		--year-range $(YEAR) \
		--delay 0.5 \
		--verbose
	@echo "✅ Setlist collection for $(YEAR) finished!"

# GDSets Collection
collect-gdsets-full:
	@echo "⭐ Extracting Grateful Dead setlists and images from GDSets HTML..."
	@cd scripts && \
		. .venv/bin/activate || (python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt) && \
		python scrape_gdsets.py \
		--html-file "$(PWD)/scripts/metadata/sources/gdsets/index.html" \
		--output-setlists "$(PWD)/scripts/metadata/setlists/gdsets_setlists.json" \
		--output-images "$(PWD)/scripts/metadata/images/gdsets_images.json" \
		--focus-years 1965-1995 \
		--verbose
	@echo "✅ Complete GDSets extraction finished!"

collect-gdsets-early:
	@echo "🎵 Extracting early years (1965-1971) setlists from GDSets HTML..."
	@cd scripts && \
		. .venv/bin/activate || (python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt) && \
		python scrape_gdsets.py \
		--html-file "$(PWD)/scripts/metadata/sources/gdsets/index.html" \
		--output-setlists "$(PWD)/scripts/metadata/setlists/gdsets_early_setlists.json" \
		--output-images "$(PWD)/scripts/metadata/images/gdsets_early_images.json" \
		--focus-years 1965-1971 \
		--verbose
	@echo "✅ Early years GDSets extraction finished!"

collect-gdsets-images:
	@echo "🖼️ Extracting Grateful Dead show images from GDSets HTML..."
	@cd scripts && \
		. .venv/bin/activate || (python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt) && \
		python scrape_gdsets.py \
		--html-file "$(PWD)/scripts/metadata/sources/gdsets/index.html" \
		--output-images "$(PWD)/scripts/metadata/images/gdsets_images.json" \
		--images-only \
		--verbose
	@echo "✅ GDSets image extraction finished!"

# Setlist Merging
merge-setlists:
	@echo "🔄 Merging CMU and GDSets setlist data..."
	@cd scripts && \
		. .venv/bin/activate || (python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt) && \
		python merge_setlists.py \
		--cmu "$(PWD)/scripts/metadata/setlists/cmu_setlists.json" \
		--gdsets "$(PWD)/scripts/metadata/setlists/gdsets_setlists.json" \
		--output "$(PWD)/scripts/metadata/setlists/raw_setlists.json" \
		--verbose
	@echo "✅ Setlist merge completed!"

merge-setlists-early:
	@echo "🔄 Merging CMU setlists with early years GDSets data..."
	@cd scripts && \
		. .venv/bin/activate || (python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt) && \
		python merge_setlists.py \
		--cmu "$(PWD)/scripts/metadata/setlists/cmu_setlists.json" \
		--gdsets "$(PWD)/scripts/metadata/setlists/gdsets_early_setlists.json" \
		--output "$(PWD)/scripts/metadata/setlists/raw_setlists_early.json" \
		--verbose
	@echo "✅ Early years setlist merge completed!"

# Venue Processing
process-venues:
	@echo "🏛️ Processing venue data from merged setlists..."
	@cd scripts && \
		. .venv/bin/activate || (python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt) && \
		python process_venues.py \
		--input "$(PWD)/scripts/metadata/setlists/raw_setlists.json" \
		--output "$(PWD)/scripts/metadata/venues/venues.json" \
		--verbose
	@echo "✅ Venue processing completed!"

# Song Processing
process-songs:
	@echo "🎵 Processing song data from merged setlists..."
	@cd scripts && \
		. .venv/bin/activate || (python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt) && \
		python process_songs.py \
		--input "$(PWD)/scripts/metadata/setlists/raw_setlists.json" \
		--output "$(PWD)/scripts/metadata/songs/songs.json" \
		--verbose
	@echo "✅ Song processing completed!"

# Setlist Integration  
integrate-setlists:
	@echo "🔗 Integrating setlists with venue and song IDs..."
	@cd scripts && \
		. .venv/bin/activate || (python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip && \
		pip install -r requirements.txt) && \
		python integrate_setlists.py \
		--setlists "$(PWD)/scripts/metadata/setlists/raw_setlists.json" \
		--venues "$(PWD)/scripts/metadata/venues/venues.json" \
		--songs "$(PWD)/scripts/metadata/songs/songs.json" \
		--output "$(PWD)/scripts/metadata/setlists/setlists.json" \
		--verbose
	@echo "✅ Setlist integration completed!"

# Data Packaging
package-datazip:
	@echo "📦 Packaging metadata into data.zip for app deployment..."
	@cd scripts && \
		. .venv/bin/activate || (python3 -m venv .venv && \
		. .venv/bin/activate && \
		python -m pip install --upgrade pip) && \
		python package_datazip.py \
		--metadata "$(PWD)/scripts/metadata" \
		--output "$(PWD)/app/src/main/assets/data.zip" \
		--verbose
	@echo "✅ data.zip packaged successfully!"

# Test Data Management
capture-test-data:
	@echo "📥 Capturing test data from device..."
	@echo "1️⃣ Make sure you've exported data using the Debug screen in the app"
	@echo "2️⃣ Pulling files from device..."
	@mkdir -p testdata
	@echo "   Trying app external files directory..."
	@adb pull /storage/emulated/0/Android/data/com.deadly.app.debug/files/testdata/ ./testdata/ 2>/dev/null || \
	 adb pull /storage/emulated/0/Android/data/com.deadly.app/files/testdata/ ./testdata/ 2>/dev/null || \
	 adb pull /sdcard/Android/data/com.deadly.app.debug/files/testdata/ ./testdata/ 2>/dev/null || \
	 adb pull /sdcard/Android/data/com.deadly.app/files/testdata/ ./testdata/ 2>/dev/null || \
	 echo "⚠️  No data found. Make sure to export data first using the Debug screen"
	@if [ -d "testdata/testdata" ]; then mv testdata/testdata/* testdata/ && rmdir testdata/testdata; fi
	@echo "✅ Test data captured to ./testdata/"
	@$(MAKE) --no-print-directory view-test-data

clean-test-data:
	@echo "🧹 Cleaning test data..."
	@adb shell pm clear com.deadly.app.debug || adb shell pm clear com.deadly.app || echo "⚠️  App not installed or ADB not connected"
	@rm -rf testdata/
	@echo "✅ Test data cleaned"

view-test-data:
	@echo "📊 Test Data Summary:"
	@if [ -d "testdata" ]; then \
		echo "  Directory: testdata/"; \
		if [ -f "testdata/complete_catalog_response.json" ]; then \
			echo "  Complete Catalog: $$(wc -l < testdata/complete_catalog_response.json 2>/dev/null || echo 0) lines, $$(du -h testdata/complete_catalog_response.json 2>/dev/null | cut -f1 || echo '0B') size"; \
		else \
			echo "  Complete Catalog: Not found"; \
		fi; \
		if [ -f "testdata/sample_metadata_responses.json" ]; then \
			echo "  Sample Metadata: $$(wc -l < testdata/sample_metadata_responses.json 2>/dev/null || echo 0) lines"; \
		else \
			echo "  Sample Metadata: Not found"; \
		fi; \
		echo "  All files:"; \
		ls -la testdata/ 2>/dev/null || echo "    (empty)"; \
	else \
		echo "  No test data directory found"; \
		echo "  💡 Use 'make capture-test-data' after exporting from Debug screen"; \
	fi

# Documentation
view-db-schema:
	@echo "📊 Generating database schema diagram..."
	@npx -p @mermaid-js/mermaid-cli mmdc -i docs/database/database-schema-diagram.md -o docs/database/schema-diagram.png
	@echo "✅ Schema diagram generated"
	@if [ -f "docs/database/schema-diagram-1.png" ]; then \
		mv docs/database/schema-diagram-1.png docs/database/schema-diagram.png; \
	fi
	@open docs/database/schema-diagram.png

