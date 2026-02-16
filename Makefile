.PHONY: help docs-help docs-install docs-build docs-serve docs-clean docs-pr
.PHONY: release release-version release-dry-run ios-release release-all
.PHONY: setup-signing setup-github-secrets
.PHONY: android-build-release android-build-bundle android-deploy-testing
.PHONY: promote-alpha promote-production
.PHONY: ios-build-release ios-deploy-testflight

# Default target shows all available commands
help:
	@echo "Deadly Monorepo - Available Make Targets"
	@echo "========================================"
	@echo ""
	@echo "RELEASE MANAGEMENT:"
	@echo "  release              - Android release with auto-versioning (default)"
	@echo "  release-version      - Android release with specific version (make release-version VERSION=1.2.3)"
	@echo "  release-dry-run      - Preview android release without making changes"
	@echo "  ios-release          - iOS release with auto-versioning"
	@echo "  release-all          - Release both platforms with auto-versioning"
	@echo "  setup-signing        - Generate keystore and .secrets/ setup"
	@echo "  setup-github-secrets - Upload all secrets to GitHub repository"
	@echo ""
	@echo "PROMOTIONS:"
	@echo "  promote-alpha        - Promote internal build to closed alpha (triggers workflow)"
	@echo "  promote-production   - Promote alpha build to production (triggers workflow)"
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

release:
	@./scripts/release.sh --platform android

release-version:
	@if [ -z "$(VERSION)" ]; then \
		echo "Error: VERSION not specified"; \
		echo "Usage: make release-version VERSION=1.2.3"; \
		exit 1; \
	fi
	@./scripts/release.sh --platform android $(VERSION)

release-dry-run:
	@./scripts/release.sh --platform android --dry-run

ios-release:
	@./scripts/release.sh --platform ios

release-all:
	@./scripts/release.sh --platform all

setup-signing:
	@./scripts/setup-signing.sh

setup-github-secrets:
	@./scripts/setup-github-secrets.sh

# =============================================================================
# ANDROID FASTLANE
# =============================================================================

promote-alpha:
	gh workflow run android-promote.yml -f stage=alpha \
		-f version=$(shell grep VERSION_NAME version.properties | cut -d= -f2)

promote-production:
	gh workflow run android-promote.yml -f stage=production \
		-f version=$(shell grep VERSION_NAME version.properties | cut -d= -f2)

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
