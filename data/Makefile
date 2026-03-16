# Grateful Dead Archive Data Pipeline
# Part of the deadly-monorepo — see root Makefile for convenience targets (make data-*)

.PHONY: help stage01-collect-data stage02-generate-data
.PHONY: collect-archive-data collect-jerrygarcia-shows collect-show-image-urls collect-show-image-files
.PHONY: generate-recordings integrate-shows process-collections
.PHONY: package-data package-data-versioned package-release package-dev
.PHONY: download-stage01 upload-stage01 download-data
.PHONY: process-reviews setup-review-processor extract-ai-reviews clean-ai-reviews
.PHONY: all clean

VERSION   ?= $(shell cat version)
REPO      ?= ds17f/deadly-monorepo
STAGE01_RELEASE ?= data-stage01-v1

# Default help
help:
	@echo "Grateful Dead Archive Data Pipeline"
	@echo ""
	@echo "Available targets:"
	@echo "  stage01-collect-data      - Run complete Stage 1: Data Collection (5-7 hours)"
	@echo "  stage02-generate-data     - Run complete Stage 2: Data Generation (fast)"
	@echo ""
	@echo "  collect-archive-data      - Collect metadata from Archive.org (2-3 hours)"
	@echo "  collect-jerrygarcia-shows - Collect complete show database from jerrygarcia.com (3-4 hours)"
	@echo "  collect-show-image-urls   - Extract ticket/photo URLs into show JSON (fast, no downloads)"
	@echo "  collect-show-image-files  - Download ticket images and photos for all shows"
	@echo "  generate-recordings       - Generate minimal recording data with ratings and enhanced metadata from cache"
	@echo "  integrate-shows           - Integrate JG shows with recording data"
	@echo "  process-collections       - Process collections and add to shows"
	@echo "  package-data              - Package all processed data into data.zip for distribution"
	@echo "  package-data-versioned    - Create versioned package with auto-detected version"
	@echo "  package-release VERSION=X - Create release package with specific version"
	@echo "  package-dev               - Create development build with commit hash"
	@echo ""
	@echo "  download-stage01          - Download stage01 cache from GitHub Release"
	@echo "  upload-stage01            - Zip and upload stage01 cache as GitHub Release"
	@echo "  download-data VERSION=X   - Download a released data.zip by version"
	@echo ""
	@echo "  setup-review-processor    - Set up review processor Python environment (one-time)"
	@echo "  process-reviews PATTERN=X - Process shows with LLM analysis"
	@echo "  extract-ai-reviews        - Extract AI reviews from stage02 to stage00 (one-time migration)"
	@echo "  clean-ai-reviews          - Delete stage00 AI review data (requires confirmation)"
	@echo ""
	@echo "  all                       - Run complete pipeline"
	@echo "  clean                     - Clean generated data (preserves stage00 AI reviews)"

# =============================================================================
# Stage 01: Data Collection
# =============================================================================

collect-archive-data:
	@echo "Collecting Grateful Dead metadata from Archive.org..."
	@echo "This may take 2-3 hours for full collection"
	python scripts/01-collect-data/collect_archive_metadata.py --mode full --verbose
	@echo "Archive.org collection complete!"

collect-jerrygarcia-shows:
	@echo "Collecting complete Grateful Dead show database from jerrygarcia.com..."
	@echo "This will collect ~2,331 shows from 111 pages (3-4 hours with 2s delay)"
	python scripts/01-collect-data/collect_jerrygarcia_com_shows.py --start-page 1 --end-page 111 --delay 2.0 --verbose
	@echo "Jerry Garcia show database collection complete!"

collect-show-image-urls:
	@echo "Extracting ticket and photo URLs for all shows (no downloads)..."
	python scripts/01-collect-data/collect_jerrygarcia_com_shows.py --collect-images --images-no-download --delay 2.0 --verbose
	@echo "Show image URL extraction complete!"

collect-show-image-files:
	@echo "Collecting ticket images and photos for all shows..."
	python scripts/01-collect-data/collect_jerrygarcia_com_shows.py --collect-images --delay 1.0 --verbose
	@echo "Show image collection complete!"

stage01-collect-data: collect-archive-data collect-jerrygarcia-shows
	@echo "Stage 1: Data Collection complete!"

# =============================================================================
# Stage 02: Data Generation
# =============================================================================

generate-recordings:
	@echo "Generating minimal recording data from raw Archive cache..."
	python scripts/02-generate-data/process_recordings_minimal.py --verbose
	@echo "Minimal recording data generation complete!"

integrate-shows:
	@echo "Integrating JG shows with recording data..."
	python scripts/02-generate-data/integrate_jerry_garcia_shows.py --verbose
	@echo "Show integration complete!"

process-collections:
	@echo "Processing collections and adding to shows..."
	python scripts/02-generate-data/process_collections.py --verbose
	@echo "Collections processing complete!"

stage02-generate-data: generate-recordings integrate-shows process-collections
	@echo "Stage 2: Data Generation complete!"

# =============================================================================
# Data Packaging
# =============================================================================

package-data:
	@echo "Packaging all processed data for distribution..."
	python scripts/package_datazip.py --output data.zip --verbose
	@echo "Data packaging complete! Final package: data.zip"

package-data-versioned:
	@echo "Creating versioned data package..."
	python scripts/package_datazip.py --version $(VERSION) --verbose
	@echo "Versioned package created!"

package-release:
	@echo "Creating release package..."
	@if [ -z "$(VERSION)" ]; then \
		echo "VERSION not specified. Usage: make package-release VERSION=2.1.0"; \
		exit 1; \
	fi
	python scripts/package_datazip.py --version $(VERSION) --verbose
	@echo "Release package v$(VERSION) created!"

package-dev:
	@echo "Creating development build package..."
	python scripts/package_datazip.py --dev-build --verbose
	@echo "Development package created!"

# =============================================================================
# Download / Upload Targets (GitHub Releases)
# =============================================================================

# Download stage01 cache from a GitHub Release (avoids re-collecting from APIs)
download-stage01:
	@echo "Downloading stage01 cache from $(REPO) release $(STAGE01_RELEASE)..."
	gh release download $(STAGE01_RELEASE) \
		--repo $(REPO) \
		--pattern 'stage01-collected-data.zip' \
		--output /tmp/stage01-collected-data.zip \
		--clobber
	@echo "Extracting stage01..."
	unzip -qo /tmp/stage01-collected-data.zip -d .
	rm /tmp/stage01-collected-data.zip
	@echo "stage01-collected-data/ restored."

# Upload current stage01 cache as a GitHub Release asset
upload-stage01:
	@echo "Zipping stage01-collected-data/..."
	cd stage01-collected-data && zip -r ../stage01-collected-data.zip .
	@echo "Uploading to $(REPO) release $(STAGE01_RELEASE)..."
	gh release upload $(STAGE01_RELEASE) stage01-collected-data.zip \
		--repo $(REPO) --clobber
	rm stage01-collected-data.zip
	@echo "stage01 uploaded to release $(STAGE01_RELEASE)."

# Download a specific versioned data.zip from GitHub Releases
download-data:
	@if [ -z "$(VERSION)" ]; then \
		echo "VERSION not specified. Usage: make download-data VERSION=2.3.0"; \
		exit 1; \
	fi
	@echo "Downloading data.zip from $(REPO) release data-v$(VERSION)..."
	gh release download "data-v$(VERSION)" \
		--repo $(REPO) \
		--pattern 'data.zip' \
		--output /tmp/data.zip \
		--clobber
	@echo "Extracting to stage02-generated-data/..."
	mkdir -p stage02-generated-data
	unzip -qo /tmp/data.zip -d stage02-generated-data
	rm /tmp/data.zip
	@echo "data-v$(VERSION) extracted to stage02-generated-data/."

# =============================================================================
# Full Pipeline
# =============================================================================

all: stage01-collect-data stage02-generate-data package-data
	@echo "Complete pipeline finished!"

# =============================================================================
# Review Processor
# =============================================================================

setup-review-processor:
	@echo "Setting up Review Processor environment..."
	cd scripts/review-processor && python -m venv .venv
	cd scripts/review-processor && source .venv/bin/activate && pip install -r requirements.txt
	@echo "Review Processor setup complete!"
	@echo ""
	@echo "Next steps:"
	@echo "  1. Edit scripts/review-processor/config.json with your LLM provider settings"
	@echo "  2. Test: make process-reviews PATTERN='stage02-generated-data/shows/gd1977-05-08*' FLAGS='--dry-run'"
	@echo "  3. Process: make process-reviews PATTERN='stage02-generated-data/shows/gd1977-*'"

process-reviews:
	@echo "Processing Grateful Dead show reviews with LLM analysis..."
	@if [ -z "$(PATTERN)" ]; then \
		echo "PATTERN is required. Example: make process-reviews PATTERN='stage02-generated-data/shows/gd1977-*'"; \
		exit 1; \
	fi
	@echo "Pattern: $(PATTERN)"
	@echo "Flags: $(FLAGS)"
	@if [ ! -d "scripts/review-processor/.venv" ]; then \
		echo "Review Processor not set up. Run 'make setup-review-processor' first."; \
		exit 1; \
	fi
	cd scripts/review-processor && source .venv/bin/activate && cd ../.. && python scripts/review-processor/review_processor.py "$(PATTERN)" $(FLAGS)

# AI Review Management
extract-ai-reviews:
	@echo "Extracting AI reviews from stage02 to stage00-created-data/ai-reviews/..."
	python scripts/extract_ai_reviews.py --verbose
	@echo "AI review extraction complete!"

clean-ai-reviews:
	@echo "WARNING: This will DELETE all AI review data from stage00-created-data/ai-reviews/"
	@echo "These reviews took days of LLM processing and are NOT cheaply regenerable."
	@read -p "Are you sure? Type 'yes' to confirm: " confirm && [ "$$confirm" = "yes" ] || { echo "Aborted."; exit 1; }
	rm -rf stage00-created-data/ai-reviews/
	@echo "AI review data deleted."

# Cleanup
clean:
	@echo "Cleaning generated data (stage00 AI reviews preserved)..."
	rm -rf stage02-generated-data/
	rm -rf stage02-processed-data/
	rm -rf stage03-search-data/
	rm -f data.zip data-v*.zip
	@echo "Cleanup complete!"
