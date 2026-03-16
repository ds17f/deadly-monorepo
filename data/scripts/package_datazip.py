#!/usr/bin/env python3
"""
Data Packaging Script

Packages the complete processed data into a compressed file for distribution.
Combines Stage 2 generated data into a mobile-ready package.

Usage:
    python scripts/package_datazip.py --output data.zip
    python scripts/package_datazip.py --verbose --analyze
"""

import json
import os
import sys
import zipfile
from datetime import datetime
from pathlib import Path
import argparse
import logging
import subprocess
import re


class DataPackager:
    """
    Packages processed Dead Archive data into distribution formats.
    
    Combines all Stage 2 outputs into optimized packages for:
    - Mobile app consumption
    - Web application deployment
    - Data analysis and research
    """
    
    def __init__(self, 
                 stage2_dir: str = "stage02-generated-data",
                 output_file: str = "data.zip",
                 version: str = None,
                 auto_version: bool = False,
                 dev_build: bool = False):
        """Initialize the data packager."""
        self.stage2_dir = Path(stage2_dir)
        
        # Version detection and output file naming
        self.version = self._detect_version(version, auto_version, dev_build)
        self.output_file = self._determine_output_file(output_file, auto_version or dev_build)
        
        # Package contents tracking
        self.included_files = []
        self.package_stats = {}
        
        # Setup logging
        self._setup_logging()
    
    def _detect_version(self, version_override: str = None, auto_version: bool = False, dev_build: bool = False) -> str:
        """
        Detect package version from various sources.
        
        Priority: manual override > git tag > git commit (dev) > default
        """
        # Manual override has highest priority
        if version_override:
            if self._validate_semver(version_override):
                return version_override
            else:
                self.logger.warning(f"Invalid version format: {version_override}, using auto-detection")
        
        # Development build using git commit
        if dev_build or (auto_version and not self._get_git_tag()):
            git_hash = self._get_git_commit_hash()
            if git_hash:
                timestamp = datetime.now().strftime('%Y%m%d')
                return f"dev-{git_hash}-{timestamp}"
        
        # Try git tag for release builds
        if auto_version:
            git_tag = self._get_git_tag()
            if git_tag:
                # Remove 'v' prefix if present
                return git_tag[1:] if git_tag.startswith('v') else git_tag
        
        # Default fallback
        return "2.0.0"
    
    def _validate_semver(self, version: str) -> bool:
        """Validate semantic version format (MAJOR.MINOR.PATCH)."""
        semver_pattern = r'^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$'
        return bool(re.match(semver_pattern, version))
    
    def _get_git_tag(self) -> str:
        """Get current git tag if available."""
        try:
            result = subprocess.run(
                ['git', 'describe', '--tags', '--exact-match', 'HEAD'], 
                capture_output=True, text=True, check=True
            )
            return result.stdout.strip()
        except (subprocess.CalledProcessError, FileNotFoundError):
            return None
    
    def _get_git_commit_hash(self) -> str:
        """Get short git commit hash."""
        try:
            result = subprocess.run(
                ['git', 'rev-parse', '--short', 'HEAD'], 
                capture_output=True, text=True, check=True
            )
            return result.stdout.strip()
        except (subprocess.CalledProcessError, FileNotFoundError):
            return None
    
    def _get_git_metadata(self) -> dict:
        """Get additional git metadata for manifest."""
        metadata = {}
        
        # Full commit hash
        try:
            result = subprocess.run(
                ['git', 'rev-parse', 'HEAD'], 
                capture_output=True, text=True, check=True
            )
            metadata['commit'] = result.stdout.strip()
        except:
            metadata['commit'] = None
        
        # Branch name
        try:
            result = subprocess.run(
                ['git', 'branch', '--show-current'], 
                capture_output=True, text=True, check=True
            )
            metadata['branch'] = result.stdout.strip()
        except:
            metadata['branch'] = None
        
        # Tag (if any)
        metadata['tag'] = self._get_git_tag()
        
        # Check if working directory is clean
        try:
            result = subprocess.run(
                ['git', 'status', '--porcelain'], 
                capture_output=True, text=True, check=True
            )
            metadata['clean'] = len(result.stdout.strip()) == 0
        except:
            metadata['clean'] = None
        
        return metadata
    
    def _determine_output_file(self, output_file: str, use_versioned_name: bool) -> Path:
        """Determine the output filename based on versioning preferences."""
        output_path = Path(output_file)
        
        # If user explicitly specified a filename, respect it
        if output_file != "data.zip":
            return output_path
        
        # For versioned builds, include version in filename
        if use_versioned_name:
            return Path(f"data-v{self.version}.zip")
        
        # Default behavior
        return output_path
    
    def _setup_logging(self):
        """Setup logging with console output."""
        self.logger = logging.getLogger(__name__)
        self.logger.setLevel(logging.INFO)
        
        # Clear existing handlers
        self.logger.handlers.clear()
        
        # Console handler
        console_handler = logging.StreamHandler()
        console_handler.setLevel(logging.INFO)
        
        # Formatter
        formatter = logging.Formatter('%(asctime)s - %(levelname)s - %(message)s')
        console_handler.setFormatter(formatter)
        
        # Add handler
        self.logger.addHandler(console_handler)
    
    def analyze_data_structure(self) -> dict:
        """
        Analyze the available data structure and calculate statistics.
        
        Returns comprehensive analysis of the dataset for validation.
        """
        analysis = {
            'timestamp': datetime.now().isoformat(),
            'stage2_data': {},
            'totals': {},
            'missing_files': []
        }
        
        # Analyze Stage 2 data
        self.logger.info("🔍 Analyzing Stage 2 generated data...")
        
        # Collections data
        collections_file = self.stage2_dir / "collections.json"
        if collections_file.exists():
            with open(collections_file, 'r', encoding='utf-8') as f:
                collections_data = json.load(f)
                analysis['stage2_data']['collections'] = {
                    'total_collections': len(collections_data.get('collections', [])),
                    'file_size': collections_file.stat().st_size
                }
        else:
            analysis['missing_files'].append(str(collections_file))
        
        # Recording data with track metadata (individual files)
        recordings_dir = self.stage2_dir / "recordings"
        if recordings_dir.exists():
            recording_files = list(recordings_dir.glob("*.json"))
            total_tracks = 0
            total_file_size = 0
            
            # Sample a few files to count tracks (for performance)
            sample_files = recording_files[:10] if len(recording_files) > 10 else recording_files
            for sample_file in sample_files:
                try:
                    with open(sample_file, 'r', encoding='utf-8') as f:
                        recording_data = json.load(f)
                        total_tracks += len(recording_data.get('tracks', []))
                        total_file_size += sample_file.stat().st_size
                except Exception:
                    continue
            
            # Estimate total tracks and size
            if sample_files:
                avg_tracks_per_recording = total_tracks / len(sample_files)
                avg_size_per_file = total_file_size / len(sample_files)
                estimated_total_tracks = int(avg_tracks_per_recording * len(recording_files))
                estimated_total_size = int(avg_size_per_file * len(recording_files))
            else:
                estimated_total_tracks = 0
                estimated_total_size = 0
            
            analysis['stage2_data']['recordings'] = {
                'total_recordings': len(recording_files),
                'total_tracks': estimated_total_tracks,
                'total_files': len(recording_files),
                'estimated_size': estimated_total_size
            }
        else:
            analysis['missing_files'].append(str(recordings_dir))
        
        # Show files
        shows_dir = self.stage2_dir / "shows"
        if shows_dir.exists():
            show_files = list(shows_dir.glob("*.json"))
            analysis['stage2_data']['shows'] = {
                'total_shows': len(show_files),
                'directory_size': sum(f.stat().st_size for f in show_files)
            }
        else:
            analysis['missing_files'].append(str(shows_dir))
        
        
        # Calculate totals
        total_size = 0
        total_files = 0
        
        if 'shows' in analysis['stage2_data']:
            total_size += analysis['stage2_data']['shows']['directory_size']
            total_files += analysis['stage2_data']['shows']['total_shows']
        
        for section in [analysis['stage2_data']]:
            for item_data in section.values():
                if isinstance(item_data, dict) and 'file_size' in item_data:
                    total_size += item_data['file_size']
                    total_files += 1
        
        analysis['totals'] = {
            'total_files': total_files,
            'total_size_bytes': total_size,
            'total_size_mb': round(total_size / (1024 * 1024), 2)
        }
        
        return analysis
    
    def create_package_manifest(self, analysis: dict) -> dict:
        """
        Create package manifest with metadata and file descriptions.
        
        Returns manifest data to be included in the package.
        """
        # Get git metadata for enhanced versioning info
        git_metadata = self._get_git_metadata()
        
        # Determine version type
        version_type = "development" if "dev-" in self.version else "release"
        
        manifest = {
            'package': {
                'name': 'Dead Archive Metadata',
                'version': self.version,
                'version_type': version_type,
                'description': 'Complete Grateful Dead concert metadata with track-level data and search optimization',
                'created': datetime.now().isoformat(),
                'generator': 'dead-metadata pipeline v3.0'
            },
            'build_info': {
                'git_commit': git_metadata.get('commit'),
                'git_branch': git_metadata.get('branch'),
                'git_tag': git_metadata.get('tag'),
                'git_clean': git_metadata.get('clean'),
                'build_timestamp': datetime.now().isoformat(),
                'build_host': os.uname().nodename if hasattr(os, 'uname') else 'unknown'
            },
            'contents': {
                'stage2_data': {
                    'description': 'Generated data products from Jerry Garcia shows and Archive.org integration',
                    'files': {
                        'collections.json': 'Processed collections with resolved show memberships',
                        'recordings/': 'Individual recording files with track-level data and ratings from Archive.org',
                        'shows/': 'Individual show files with complete metadata (2,313+ shows)'
                    }
                },
            },
            'statistics': analysis['totals'],
            'data_sources': [
                'jerrygarcia.com - Complete show database (1965-1995)',
                'Archive.org - 17,790+ recording metadata files with ratings',
                'Curated collections - 25+ pre-defined show groupings'
            ],
            'usage': {
                'mobile_apps': 'Import data.zip contents into mobile app assets',
                'web_apps': 'Serve search indexes via REST API',
                'research': 'Comprehensive dataset for Dead analysis'
            }
        }
        
        return manifest
    
    def package_data(self, compression_level: int = zipfile.ZIP_DEFLATED) -> bool:
        """
        Create the final data package with all processed data.
        
        Returns True if successful, False otherwise.
        """
        try:
            # Analyze data first
            analysis = self.analyze_data_structure()
            
            # Check for missing critical files
            if analysis['missing_files']:
                self.logger.error(f"❌ Missing critical files: {analysis['missing_files']}")
                return False
            
            # Create package manifest
            manifest = self.create_package_manifest(analysis)
            
            self.logger.info(f"📦 Creating package: {self.output_file}")
            self.logger.info(f"🏷️ Package version: {self.version}")
            self.logger.info(f"📊 Total data: {analysis['totals']['total_size_mb']} MB across {analysis['totals']['total_files']} files")
            
            with zipfile.ZipFile(self.output_file, 'w', compression_level) as zipf:
                
                # Add manifest
                manifest_json = json.dumps(manifest, indent=2, ensure_ascii=False)
                zipf.writestr('manifest.json', manifest_json)
                self.included_files.append('manifest.json')
                
                # Add Stage 2 generated data
                self.logger.info("📁 Adding Stage 2 generated data...")
                
                # Collections
                collections_file = self.stage2_dir / "collections.json"
                if collections_file.exists():
                    zipf.write(collections_file, 'collections.json')
                    self.included_files.append('collections.json')
                
                # Individual recording files with tracks
                recordings_dir = self.stage2_dir / "recordings"
                if recordings_dir.exists():
                    recording_files = list(recordings_dir.glob("*.json"))
                    recording_count = 0
                    
                    self.logger.info(f"   📄 Adding {len(recording_files)} individual recording files...")
                    
                    for recording_file in recording_files:
                        archive_path = f"recordings/{recording_file.name}"
                        zipf.write(recording_file, archive_path)
                        self.included_files.append(archive_path)
                        recording_count += 1
                        
                        if recording_count % 2000 == 0:
                            self.logger.info(f"   📄 Added {recording_count}/{len(recording_files)} recording files...")
                    
                    self.logger.info(f"   ✅ Added {recording_count} recording files")
                
                # Individual show files
                shows_dir = self.stage2_dir / "shows"
                if shows_dir.exists():
                    show_files = list(shows_dir.glob("*.json"))
                    show_count = 0
                    
                    for show_file in show_files:
                        archive_path = f"shows/{show_file.name}"
                        zipf.write(show_file, archive_path)
                        self.included_files.append(archive_path)
                        show_count += 1
                        
                        if show_count % 500 == 0:
                            self.logger.info(f"   📄 Added {show_count}/{len(show_files)} show files...")
                    
                    self.logger.info(f"   ✅ Added {show_count} show files")
                
                
                # Calculate final package stats
                self.package_stats = {
                    'total_files': len(self.included_files),
                    'compressed_size': self.output_file.stat().st_size,
                    'compression_ratio': round(
                        (1 - self.output_file.stat().st_size / analysis['totals']['total_size_bytes']) * 100, 1
                    )
                }
            
            self.logger.info(f"✅ Package created successfully!")
            self.logger.info(f"📊 Final package: {round(self.package_stats['compressed_size'] / (1024 * 1024), 2)} MB")
            self.logger.info(f"🗜️ Compression: {self.package_stats['compression_ratio']}% reduction")
            self.logger.info(f"📄 Total files: {self.package_stats['total_files']}")
            
            return True
            
        except Exception as e:
            self.logger.error(f"❌ Packaging failed: {e}")
            return False
    
    def validate_package(self) -> bool:
        """
        Validate the created package by reading and checking contents.
        
        Returns True if package is valid, False otherwise.
        """
        if not self.output_file.exists():
            self.logger.error(f"❌ Package file not found: {self.output_file}")
            return False
        
        try:
            self.logger.info(f"🔍 Validating package: {self.output_file}")
            
            with zipfile.ZipFile(self.output_file, 'r') as zipf:
                # Check manifest
                try:
                    manifest_data = zipf.read('manifest.json')
                    manifest = json.loads(manifest_data.decode('utf-8'))
                    self.logger.info(f"✅ Manifest valid: {manifest['package']['name']} v{manifest['package']['version']}")
                except Exception as e:
                    self.logger.error(f"❌ Invalid manifest: {e}")
                    return False
                
                # Check file list
                files_in_package = zipf.namelist()
                expected_critical_files = [
                    'manifest.json',
                    'collections.json',
                    # TODO: These are no longer critical files
                    # 'search/shows_index.json',
                    # 'search/collections.json'
                ]
                
                for critical_file in expected_critical_files:
                    if critical_file not in files_in_package:
                        self.logger.error(f"❌ Missing critical file: {critical_file}")
                        return False
                
                # Count show files
                show_files = [f for f in files_in_package if f.startswith('shows/') and f.endswith('.json')]
                self.logger.info(f"📄 Package contains {len(show_files)} show files")
                
                # Count recording files
                recording_files = [f for f in files_in_package if f.startswith('recordings/') and f.endswith('.json')]
                self.logger.info(f"📄 Package contains {len(recording_files)} recording files")
                
                # Test read a sample show file
                if show_files:
                    sample_show = zipf.read(show_files[0])
                    show_data = json.loads(sample_show.decode('utf-8'))
                    if 'show_id' in show_data and 'date' in show_data:
                        self.logger.info(f"✅ Show file format valid: {show_data['show_id']}")
                    else:
                        self.logger.error(f"❌ Invalid show file format")
                        return False
                
                # TODO: Format has changed, update this
                ## Test read a sample recording file
                #if recording_files:
                #    sample_recording = zipf.read(recording_files[0])
                #    recording_data = json.loads(sample_recording.decode('utf-8'))
                #    if 'rating' in recording_data and 'tracks' in recording_data:
                #        self.logger.info(f"✅ Recording file format valid with {len(recording_data.get('tracks', []))} tracks")
                #    else:
                #        self.logger.error(f"❌ Invalid recording file format")
                #        return False
                
                self.logger.info(f"✅ Package validation successful!")
                return True
                
        except Exception as e:
            self.logger.error(f"❌ Package validation failed: {e}")
            return False


def main():
    """Main execution function."""
    parser = argparse.ArgumentParser(description='Package Dead Archive processed data')
    parser.add_argument('--output', default='data.zip', help='Output package filename')
    parser.add_argument('--stage2-dir', default='stage02-generated-data',
                        help='Stage 2 data directory')
    parser.add_argument('--verbose', action='store_true', help='Enable verbose output')
    parser.add_argument('--analyze', action='store_true', help='Analyze data structure only')
    parser.add_argument('--validate', action='store_true', help='Validate existing package')
    
    # Versioning options
    parser.add_argument('--version', help='Specify package version (e.g., 2.1.0)')
    parser.add_argument('--auto-version', action='store_true', 
                        help='Auto-detect version from git tags/commits and use versioned filename')
    parser.add_argument('--dev-build', action='store_true',
                        help='Create development build with commit hash and timestamp')
    
    args = parser.parse_args()
    
    # Initialize packager
    packager = DataPackager(
        stage2_dir=args.stage2_dir,
        output_file=args.output,
        version=args.version,
        auto_version=args.auto_version,
        dev_build=args.dev_build
    )
    
    # Set logging level
    if args.verbose:
        packager.logger.setLevel(logging.DEBUG)
    
    try:
        packager.logger.info("🚀 Starting data packaging process...")
        
        # Analyze only mode
        if args.analyze:
            analysis = packager.analyze_data_structure()
            print("\n📊 Data Structure Analysis:")
            print(json.dumps(analysis, indent=2))
            return 0
        
        # Validate only mode
        if args.validate:
            if packager.validate_package():
                packager.logger.info("✅ Package validation successful!")
                return 0
            else:
                packager.logger.error("❌ Package validation failed!")
                return 1
        
        # Full packaging process
        if packager.package_data():
            # Validate the created package
            if packager.validate_package():
                packager.logger.info("🎉 Data packaging completed successfully!")
                packager.logger.info(f"📦 Final package: {args.output}")
                return 0
            else:
                packager.logger.error("❌ Package created but validation failed!")
                return 1
        else:
            packager.logger.error("❌ Data packaging failed!")
            return 1
            
    except Exception as e:
        packager.logger.error(f"❌ Packaging process failed: {e}")
        return 1


if __name__ == "__main__":
    exit(main())
