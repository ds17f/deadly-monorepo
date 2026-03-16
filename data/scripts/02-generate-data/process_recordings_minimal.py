#!/usr/bin/env python3
"""
Minimal Recordings Processor

This script processes raw Archive.org recording metadata and generates lightweight
recording files for mobile consumption. It includes just-in-time processing of
ratings, source type detection, and outputs only essential fields.

Architecture:
- Loads raw cached metadata from Stage 1 collection
- Applies just-in-time processing (ratings, source types)  
- Generates minimal output with only essential fields
- No tracks data (too heavy, unused downstream)
- Fast local processing from cached data

Usage:
    # Default processing
    python scripts/02-generate-data/process_recordings_minimal.py
    
    # Custom input/output
    python scripts/02-generate-data/process_recordings_minimal.py \
        --input-dir stage01-collected-data/archive \
        --output-dir stage02-generated-data/recordings
"""

import json
import os
import sys
import re
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple, Any
import argparse
import logging

# Add shared module to path
sys.path.append(str(Path(__file__).parent.parent))
from shared.models import RecordingMetadata, ProcessedRecordingMetadata, processed_recording_to_dict
from shared.recording_utils import improve_source_type_detection


class MinimalRecordingsProcessor:
    """
    Processor for raw Archive.org data, generating minimal recording files.
    """
    
    def __init__(self, input_dir: str = "stage01-collected-data/archive"):
        """Initialize the processor with input directory."""
        self.input_dir = Path(input_dir)
        
        # Source type weights for rating computation
        self.source_weights = {
            'SBD': 1.0,
            'MATRIX': 0.9,
            'AUD': 0.7,
            'FM': 0.8,
            'REMASTER': 1.0,
        }
        
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
            self.logger.info("✅ Raw data format validation passed")
        except Exception as e:
            self.logger.error(f"❌ Raw data format validation failed: {e}")
            self.logger.error(f"Problem file: {test_file}")
            return False
        
        return True
    
    def load_raw_recordings(self) -> List[RecordingMetadata]:
        """Load all raw recording metadata."""
        recordings = []
        recording_files = list(self.input_dir.glob("*.json"))
        # Exclude progress.json and collection.log
        recording_files = [f for f in recording_files if not f.name.startswith(('progress', 'collection'))]
        
        self.logger.info(f"Loading {len(recording_files)} raw recordings...")
        
        for cache_file in recording_files:
            try:
                with open(cache_file, 'r') as f:
                    data = json.load(f)
                recording_meta = RecordingMetadata(**data)
                recordings.append(recording_meta)
            except Exception as e:
                self.logger.warning(f"Skipping corrupted cache file {cache_file}: {e}")
                continue
        
        self.logger.info(f"Successfully loaded {len(recordings)} raw recordings")
        return recordings
    
    def improve_source_type(self, identifier: str, raw_metadata: Dict[str, Any]) -> str:
        """Improve source type detection using enhanced logic from recording_utils."""
        # Create a minimal RecordingMetadata-like object for the enhanced function
        class MinimalRecording:
            def __init__(self, identifier, raw_metadata):
                self.identifier = identifier
                self.raw_metadata = raw_metadata
            
            @property
            def title(self): return self.raw_metadata.get('title', '')
            @property 
            def description(self): return self.raw_metadata.get('description', '')
            @property
            def source(self): return self.raw_metadata.get('source', '')
        
        recording = MinimalRecording(identifier, raw_metadata)
        return improve_source_type_detection(recording)
    
    def compute_recording_rating(self, raw_reviews: List[Dict[str, Any]], source_type: str) -> Tuple[float, float, float, int, int]:
        """Compute weighted rating, confidence, and rating breakdown from raw reviews."""
        if not raw_reviews:
            return 0.0, 0.0, 0.0, 0, 0
        
        # Filter and process reviews
        valid_reviews = []
        for review in raw_reviews:
            stars = float(review.get('stars', 0))
            if stars >= 1.0:
                valid_reviews.append(stars)
        
        if not valid_reviews:
            return 0.0, 0.0, 0.0, 0, 0
            
        # Compute basic average (raw rating)
        raw_rating = sum(valid_reviews) / len(valid_reviews)
        
        # Apply source type weighting  
        source_weight = self.source_weights.get(source_type, 0.5)
        weighted_rating = raw_rating * source_weight
        
        # Confidence based on review count
        confidence = min(len(valid_reviews) / 5.0, 1.0)
        
        # Count high/low ratings
        high_ratings = sum(1 for stars in valid_reviews if stars >= 4.0)
        low_ratings = sum(1 for stars in valid_reviews if stars <= 2.0)
        
        final_weighted_rating = weighted_rating * (0.5 + 0.5 * confidence)
        
        return final_weighted_rating, confidence, raw_rating, high_ratings, low_ratings
    
    def process_recording(self, raw_recording: RecordingMetadata) -> ProcessedRecordingMetadata:
        """Process a single raw recording into minimal processed format."""
        
        # Improve source type detection
        source_type = self.improve_source_type(raw_recording.identifier, raw_recording.raw_metadata)
        
        # Compute ratings from raw reviews
        rating, confidence, raw_rating, high_ratings, low_ratings = self.compute_recording_rating(
            raw_recording.raw_reviews, source_type
        )
        
        # Create processed recording with only essential fields
        return ProcessedRecordingMetadata(
            identifier=raw_recording.identifier,
            title=raw_recording.title,
            date=raw_recording.normalized_date,  # Use normalized date
            venue=raw_recording.venue,
            location=raw_recording.location,
            source_type=source_type,
            lineage=raw_recording.lineage,
            taper=raw_recording.taper,
            source=raw_recording.source,
            runtime=raw_recording.runtime,
            rating=rating,
            review_count=len(raw_recording.raw_reviews),
            confidence=confidence,
            raw_rating=raw_rating,
            high_ratings=high_ratings,
            low_ratings=low_ratings
        )
    
    def save_minimal_recordings(self, recordings: List[RecordingMetadata], output_dir: str):
        """Save minimal processed recording files."""
        self.logger.info("Generating minimal recording files...")
        
        # Create recordings directory
        recordings_dir = Path(output_dir)
        recordings_dir.mkdir(parents=True, exist_ok=True)
        
        improved_source_types = 0
        
        # Process each recording
        for raw_recording in recordings:
            # Get original source type from raw metadata (if any)
            original_source_type = self.improve_source_type('', raw_recording.raw_metadata)  # Just from metadata
            
            # Process recording with full identifier analysis
            processed_recording = self.process_recording(raw_recording)
            
            if processed_recording.source_type != original_source_type:
                improved_source_types += 1
            
            # Save minimal recording file
            recording_dict = processed_recording_to_dict(processed_recording)

            # Merge AI review from stage00 if available
            ai_review_path = Path("stage00-created-data/ai-reviews/recordings") / f"{processed_recording.identifier}.json"
            if ai_review_path.exists():
                try:
                    with open(ai_review_path) as af:
                        recording_dict["ai_review"] = json.load(af)
                except Exception as e:
                    self.logger.warning(f"Failed to load AI review for {processed_recording.identifier}: {e}")

            recording_file = recordings_dir / f"{processed_recording.identifier}.json"
            with open(recording_file, 'w') as f:
                json.dump(recording_dict, f, indent=2)
        
        # Calculate directory statistics
        dir_size = sum(f.stat().st_size for f in recordings_dir.glob("*.json")) / (1024 * 1024)  # MB
        
        self.logger.info(f"Generated {len(recordings)} minimal recording files: {dir_size:.1f}MB")
        self.logger.info(f"Improved source types for {improved_source_types} recordings using identifier patterns")
        self.logger.info(f"Output directory: {recordings_dir}")
    
    def process_recordings(self, output_dir: str = "stage02-generated-data/recordings"):
        """Process recordings from raw cache data."""
        start_time = datetime.now()
        
        # Load raw recordings
        recordings = self.load_raw_recordings()
        if not recordings:
            self.logger.error("No recordings loaded. Cannot proceed.")
            return False
        
        # Save minimal processed recordings
        self.save_minimal_recordings(recordings, output_dir)
        
        # Report processing time
        processing_time = (datetime.now() - start_time).total_seconds()
        self.logger.info(f"✅ Processing completed in {processing_time:.1f} seconds")
        
        # Summary
        self.logger.info("=== Processing Summary ===")
        self.logger.info(f"Input recordings: {len(recordings)}")
        self.logger.info(f"Output directory: {output_dir}")
        self.logger.info(f"Input directory: {self.input_dir}")
        
        return True


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description='Generate minimal recording files from raw Archive.org data')
    parser.add_argument('--input-dir', default='stage01-collected-data/archive', 
                       help='Input directory with raw recordings')
    parser.add_argument('--output-dir', default='stage02-generated-data/recordings',
                       help='Output directory for minimal recordings')
    parser.add_argument('--verbose', action='store_true',
                       help='Enable verbose logging')
    
    args = parser.parse_args()
    
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    processor = MinimalRecordingsProcessor(
        input_dir=args.input_dir
    )
    
    # Validate input
    if not processor.validate_input_data():
        return 1
    
    # Process recordings
    success = processor.process_recordings(output_dir=args.output_dir)
    
    if success:
        print(f"✅ Processing complete! Output: {args.output_dir}")
        return 0
    else:
        print("❌ Processing failed. Check logs for details.")
        return 1


if __name__ == '__main__':
    exit(main())