.PHONY: help docs-help docs-install docs-build docs-serve docs-clean docs-pr
.PHONY: release release-version release-dry-run setup-github-secrets
.PHONY: android-build-release android-build-bundle android-deploy-testing
.PHONY: ios-build-release ios-deploy-testflight

# Default target shows all available commands
help:
	@echo "Deadly Monorepo - Available Make Targets"
	@echo "========================================"
	@echo ""
	@echo "RELEASE MANAGEMENT:"
	@echo "  release             - Create release with automatic versioning"
	@echo "  release-version     - Create release with specific version (make release-version VERSION=1.2.3)"
	@echo "  release-dry-run     - Preview release without making changes"
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
	mkdocs serve -f docs/mkdocs.yml

# Remove generated site directory.
docs-clean:
	rm -rf site/

# Pre‑PR check: clean, build and confirm successful generation.
docs-pr: docs-clean docs-build
	@echo "✅ Site built successfully!"
	@echo "📂 Output in site/ directory"
	@echo "🚀 Run 'make docs-serve' to test locally"

# =============================================================================
# RELEASE MANAGEMENT
# =============================================================================

release:
	@echo "🚀 Creating release with automatic versioning..."
	@if [ ! -f scripts/release.sh ]; then \
		echo "❌ Error: scripts/release.sh not found"; \
		exit 1; \
	fi
	@./scripts/release.sh
	@echo "✅ Release created successfully!"
	@echo "💡 GitHub Actions will now build and publish the release"

release-version:
	@echo "🚀 Creating release with version $(VERSION)..."
	@if [ -z "$(VERSION)" ]; then \
		echo "❌ Error: VERSION not specified"; \
		echo "💡 Usage: make release-version VERSION=1.2.3"; \
		exit 1; \
	fi
	@if [ ! -f scripts/release.sh ]; then \
		echo "❌ Error: scripts/release.sh not found"; \
		exit 1; \
	fi
	@./scripts/release.sh $(VERSION)
	@echo "✅ Release $(VERSION) created successfully!"
	@echo "💡 GitHub Actions will now build and publish the release"

release-dry-run:
	@echo "🧪 Running release dry run..."
	@if [ ! -f scripts/release.sh ]; then \
		echo "❌ Error: scripts/release.sh not found"; \
		exit 1; \
	fi
	@./scripts/release.sh --dry-run

setup-github-secrets:
	@echo "🔐 Uploading secrets to GitHub..."
	@if [ ! -f scripts/setup-github-secrets.sh ]; then \
		echo "❌ Error: scripts/setup-github-secrets.sh not found"; \
		exit 1; \
	fi
	@./scripts/setup-github-secrets.sh

# =============================================================================
# ANDROID FASTLANE
# =============================================================================

android-build-release:
	@echo "📱 Building Android release APK..."
	@cd androidApp && fastlane build_release

android-build-bundle:
	@echo "📱 Building Android App Bundle..."
	@cd androidApp && fastlane build_bundle

android-deploy-testing:
	@echo "📱 Deploying to Play Store Internal Testing..."
	@cd androidApp && fastlane deploy_testing

# =============================================================================
# IOS FASTLANE
# =============================================================================

ios-build-release:
	@echo "🍎 Building iOS release IPA..."
	@cd iosApp && fastlane build_release

ios-deploy-testflight:
	@echo "🍎 Deploying to TestFlight..."
	@cd iosApp && fastlane deploy_testflight
