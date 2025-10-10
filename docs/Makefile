.PHONY: help install build serve clean

# Default target
help:
	@echo "Available commands:"
	@echo "  install  - Install MkDocs and dependencies"
	@echo "  build    - Build the documentation site"
	@echo "  serve    - Serve the documentation locally (development mode)"
	@echo "  clean    - Clean the build directory"
	@echo "  pr       - Build and test the site locally before PR"

# Install dependencies
install:
	pip install mkdocs-material

# Build the documentation
build:
	mkdocs build

# Serve the documentation locally
serve:
	mkdocs serve

# Clean the build directory
clean:
	rm -rf site/

# Pre-PR check: build and test locally
pr: clean build
	@echo "✅ Site built successfully!"
	@echo "📂 Output in site/ directory"
	@echo "🚀 Run 'make serve' to test locally"