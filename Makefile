.PHONY: docs-help docs-install docs-build docs-serve docs-clean docs-pr

# Default target shows help for documentation commands
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
