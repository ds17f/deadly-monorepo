#!/usr/bin/env python3
"""
Archive Recordings Generation Script

This script processes cached Archive.org recording metadata into recordings.json
with comprehensive recording data and track-level metadata. It focuses solely on 
recording data processing without generating any show files.

Architecture:
- Reads cached recording metadata from Stage 1 collection
- Generates recordings.json with ratings and track metadata for app consumption
- Validates input data exists before processing
- Fast local processing of cached data
- NO show file generation (Archive.org provides recording data only)

Usage:
    # Default processing
    python scripts/02-generate-data/generate_archive_recordings.py
    
    # Custom input directory
    python scripts/02-generate-data/generate_archive_recordings.py --input-dir /path/to/cache
    
    # Custom output file
    python scripts/02-generate-data/generate_archive_recordings.py --output /path/to/recordings.json
"""

import json
import os
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional
import argparse
import logging

# Add shared module to path
sys.path.append(str(Path(__file__).parent.parent))
from shared.models import RecordingMetadata


class ArchiveRecordingsProcessor:
    """
    Processor for cached Archive.org data, generating recordings.json only.
    """
    
    def __init__(self, input_dir: str = "stage01-collected-data/archive"):
        """Initialize the processor with input directory."""
        self.input_dir = Path(input_dir)
        
        # Setup logging
        self._setup_logging()
    
    def _setup_logging(self):
        """Setup logging with console output."""
        self.logger = logging.getLogger(__name__)
        self.logger.setLevel(logging.INFO)
        
        # Clear any existing handlers
        self.logger.handlers.clear()
        
        # Console handler
        console_handler = logging.StreamHandler()
        console_handler.setLevel(logging.INFO)
        
        # Formatter
        formatter = logging.Formatter('%(asctime)s - %(levelname)s - %(message)s')
        console_handler.setFormatter(formatter)
        
        # Add handler
        self.logger.addHandler(console_handler)
    
    def validate_input_data(self) -> bool:
        """Validate that input data exists and is usable."""
        if not self.input_dir.exists():
            self.logger.error(f"❌ Input directory does not exist: {self.input_dir}")
            self.logger.error("Please run the collection script first:")
            self.logger.error(f"  python scripts/01-collect-data/collect_archive_metadata.py")
            return False
        
        # Count cached recording files
        recording_files = list(self.input_dir.glob("*.json"))
        # Exclude progress.json and logs
        recording_files = [f for f in recording_files if not f.name.startswith(('progress', 'collection'))]
        
        if len(recording_files) == 0:
            self.logger.error(f"❌ No recording metadata found in: {self.input_dir}")
            self.logger.error("Please run the collection script first to populate the cache.")
            return False
        
        self.logger.info(f"✅ Found {len(recording_files)} cached recording files")
        
        # Test read one file to validate format
        try:
            test_file = recording_files[0]
            with open(test_file, 'r') as f:
                data = json.load(f)
            # Try to create RecordingMetadata to validate structure
            RecordingMetadata(**data)
            self.logger.info("✅ Cached data format validation passed")
        except Exception as e:
            self.logger.error(f"❌ Cached data format validation failed: {e}")
            self.logger.error(f"Problem file: {test_file}")
            return False
        
        return True
    
    def load_cached_recordings(self) -> List[RecordingMetadata]:
        """Load all cached recording metadata."""
        recordings = []
        recording_files = list(self.input_dir.glob("*.json"))
        # Exclude progress.json and collection.log
        recording_files = [f for f in recording_files if not f.name.startswith(('progress', 'collection'))]
        
        self.logger.info(f"Loading {len(recording_files)} cached recordings...")
        
        for cache_file in recording_files:
            try:
                with open(cache_file, 'r') as f:
                    data = json.load(f)
                recording_meta = RecordingMetadata(**data)
                recordings.append(recording_meta)
            except Exception as e:
                self.logger.warning(f"Skipping corrupted cache file {cache_file}: {e}")
                continue
        
        self.logger.info(f"Successfully loaded {len(recordings)} recordings")
        return recordings
    
    def extract_track_metadata(self, recording_meta: RecordingMetadata) -> List[Dict]:
        """Extract track information from Archive.org files array."""
        tracks = []
        
        # Group files by track number
        track_files = {}
        for file_info in recording_meta.files:
            # Only process audio files with track information
            if not file_info.get('title') or not file_info.get('track'):
                continue
            
            # Skip derivative files we don't need (spectrograms, peaks, etc.)
            format_name = file_info.get('format', '').lower()
            if format_name in ['spectrogram', 'columbia peaks', 'png']:
                continue
            
            track_num = file_info.get('track')
            if track_num not in track_files:
                track_files[track_num] = {
                    'title': file_info.get('title', '').replace(f"{track_num} ", ""),
                    'duration': None,
                    'formats': []
                }
            
            # Extract duration (prefer from FLAC/original source)
            if file_info.get('source') == 'original' and file_info.get('length'):
                try:
                    duration = float(file_info['length'])
                    track_files[track_num]['duration'] = duration
                except (ValueError, TypeError):
                    pass
            
            # Add format information
            format_info = {
                'format': file_info.get('format', 'Unknown'),
                'filename': file_info.get('name', '')
            }
            
            # Add bitrate for MP3s
            if 'mp3' in format_name and file_info.get('bitrate'):
                format_info['bitrate'] = file_info.get('bitrate')
            
            track_files[track_num]['formats'].append(format_info)
        
        # Convert to list format, sorted by track number
        for track_num in sorted(track_files.keys(), key=lambda x: int(x) if x.isdigit() else 999):
            track_data = track_files[track_num]
            
            # Skip tracks without any valid formats
            if not track_data['formats']:
                continue
            
            tracks.append({
                'track': track_num,
                'title': track_data['title'],
                'duration': track_data['duration'],
                'formats': track_data['formats']
            })
        
        return tracks
    
    def improve_source_type(self, identifier: str, original_source_type: str) -> str:
        """Improve source type detection using identifier patterns."""
        # If we already have a good source type from Archive.org, keep it
        if original_source_type != 'UNKNOWN':
            return original_source_type
            
        # Check identifier patterns for source type clues
        identifier_upper = identifier.upper()
        
        if '.SBD.' in identifier_upper or '.SOUNDBOARD.' in identifier_upper:
            return 'SBD'
        elif '.MTX.' in identifier_upper or '.MATRIX.' in identifier_upper:
            return 'MATRIX'  
        elif '.AUD.' in identifier_upper or '.AUDIENCE.' in identifier_upper:
            return 'AUD'
        elif '.FM.' in identifier_upper or '.BROADCAST.' in identifier_upper:
            return 'FM'
        elif '.REMASTER.' in identifier_upper:
            return 'REMASTER'
        
        # If no patterns found, return original
        return original_source_type

    def save_individual_recordings(self, recordings: List[RecordingMetadata], output_dir: str):
        """Save individual recording files with ratings and track data."""
        self.logger.info("Generating individual recording files with track metadata...")
        
        # Create recordings directory
        recordings_dir = Path(output_dir) / "recordings"
        recordings_dir.mkdir(parents=True, exist_ok=True)
        
        total_tracks = 0
        improved_source_types = 0
        
        # Process each recording with track data
        for recording_meta in recordings:
            # Extract track metadata
            tracks = self.extract_track_metadata(recording_meta)
            total_tracks += len(tracks)
            
            # Improve source type detection using identifier patterns
            improved_source_type = self.improve_source_type(recording_meta.identifier, recording_meta.source_type)
            if improved_source_type != recording_meta.source_type:
                improved_source_types += 1
            
            # Create individual recording data
            recording_data = {
                "rating": recording_meta.rating,
                "review_count": recording_meta.review_count,
                "source_type": improved_source_type,
                "confidence": recording_meta.confidence,
                "date": recording_meta.date,
                "venue": recording_meta.venue,
                "location": recording_meta.location,
                "lineage": recording_meta.lineage,
                "taper": recording_meta.taper,
                "raw_rating": recording_meta.raw_rating,
                "high_ratings": recording_meta.high_ratings,
                "low_ratings": recording_meta.low_ratings,
                "tracks": tracks
            }
            
            # Save individual recording file
            recording_file = recordings_dir / f"{recording_meta.identifier}.json"
            with open(recording_file, 'w') as f:
                json.dump(recording_data, f, indent=2)
        
        # Calculate directory statistics
        dir_size = sum(f.stat().st_size for f in recordings_dir.glob("*.json")) / (1024 * 1024)  # MB
        
        self.logger.info(f"Generated {len(recordings)} individual recording files: {dir_size:.1f}MB")
        self.logger.info(f"Contains {len(recordings)} recordings with {total_tracks} total tracks")
        self.logger.info(f"Improved source types for {improved_source_types} recordings using identifier patterns")
        self.logger.info(f"Output directory: {recordings_dir}")
    
    def process_recordings(self, output_dir: str = "stage02-generated-data"):
        """Process recordings from cached data."""
        start_time = datetime.now()
        
        # Load cached recordings
        recordings = self.load_cached_recordings()
        if not recordings:
            self.logger.error("No recordings loaded. Cannot proceed.")
            return False
        
        # Save individual recording files
        self.save_individual_recordings(recordings, output_dir)
        
        # Report processing time
        processing_time = (datetime.now() - start_time).total_seconds()
        self.logger.info(f"✅ Processing completed in {processing_time:.1f} seconds")
        
        # Summary
        self.logger.info("=== Generation Summary ===")
        self.logger.info(f"Input recordings: {len(recordings)}")
        self.logger.info(f"Output directory: {output_dir}/recordings/")
        self.logger.info(f"Input directory: {self.input_dir}")
        
        return True


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description='Generate individual recording files from cached Archive.org data')
    parser.add_argument('--input-dir', default='stage01-collected-data/archive', 
                       help='Input directory with cached recordings')
    parser.add_argument('--output-dir', default='stage02-generated-data',
                       help='Output directory (recordings/ will be created inside)')
    parser.add_argument('--verbose', action='store_true',
                       help='Enable verbose logging')
    
    args = parser.parse_args()
    
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    processor = ArchiveRecordingsProcessor(
        input_dir=args.input_dir
    )
    
    # Validate input
    if not processor.validate_input_data():
        return 1
    
    # Process recordings
    success = processor.process_recordings(output_dir=args.output_dir)
    
    if success:
        print(f"✅ Generation complete! Output: {args.output_dir}/recordings/")
        return 0
    else:
        print("❌ Generation failed. Check logs for details.")
        return 1


if __name__ == '__main__':
    exit(main())