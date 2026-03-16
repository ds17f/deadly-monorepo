#!/usr/bin/env python3
"""
Recording Ratings Generation Script

This script processes cached Archive.org recording metadata to generate comprehensive
rating statistics including raw ratings, distributions, and high/low rating counts.
These ratings are then used by the show integration script.

Architecture:
- Reads cached recording metadata from Stage 1 collection
- Calculates recording-level rating statistics (raw_rating, distribution, etc.)
- Generates show-level rating aggregations
- Creates recording_ratings.json for use by integration script

Usage:
    # Default processing
    python scripts/02-generate-data/generate_recording_ratings.py
    
    # Custom input/output directories
    python scripts/02-generate-data/generate_recording_ratings.py \
        --input-dir /path/to/archive/cache \
        --output-file /path/to/recording_ratings.json
"""

import json
import os
import sys
from datetime import datetime
from pathlib import Path
from collections import defaultdict
from typing import Dict, List, Optional, Any
import argparse
import logging

# Add shared module to path
sys.path.append(str(Path(__file__).parent.parent))
from shared.models import RecordingMetadata
from shared.recording_utils import improve_source_type_detection


class RecordingRatingsGenerator:
    """
    Generator for comprehensive recording rating statistics from Archive.org cache.
    """
    
    def __init__(self, input_dir: str = "stage01-collected-data/archive",
                 output_file: str = "stage02-generated-data/recording_ratings.json"):
        """Initialize the generator with input and output paths."""
        self.input_dir = Path(input_dir)
        self.output_file = Path(output_file)
        
        # Source weighting for best recording selection
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
            self.logger.info("✅ Cached data format validation passed")
        except Exception as e:
            self.logger.error(f"❌ Cached data format validation failed: {e}")
            self.logger.error(f"Problem file: {test_file}")
            return False
        
        return True
    
    def create_output_directory(self):
        """Create output directory if it doesn't exist."""
        self.output_file.parent.mkdir(parents=True, exist_ok=True)
        self.logger.info(f"Output directory ready: {self.output_file.parent}")
    
    def calculate_recording_rating(self, reviews: List[Dict[str, Any]]) -> Dict[str, Any]:
        """
        Calculate comprehensive rating statistics from reviews.
        Returns dictionary with raw_rating, distribution, high_ratings, low_ratings.
        """
        if not reviews:
            return {
                "raw_rating": 0.0,
                "distribution": {},
                "high_ratings": 0,
                "low_ratings": 0
            }
        
        # Calculate raw rating (simple average)
        total_stars = sum(review['stars'] for review in reviews)
        raw_rating = total_stars / len(reviews)
        
        # Build distribution
        distribution = {}
        for review in reviews:
            star_level = str(int(review['stars']))  # Convert to string for JSON
            distribution[star_level] = distribution.get(star_level, 0) + 1
        
        # Count high and low ratings
        high_ratings = sum(1 for review in reviews if review['stars'] >= 4)
        low_ratings = sum(1 for review in reviews if review['stars'] <= 2)
        
        return {
            "raw_rating": raw_rating,
            "distribution": distribution,
            "high_ratings": high_ratings,
            "low_ratings": low_ratings
        }
    
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
    
    def generate_recording_ratings(self, recordings: List[RecordingMetadata]) -> Dict[str, Any]:
        """Generate recording-level rating statistics."""
        self.logger.info("Generating recording rating statistics...")
        
        recording_ratings = {}
        
        # Process each recording
        for recording_meta in recordings:
            # Handle reviews (they might already be dictionaries or ReviewData objects)
            reviews = []
            for review in recording_meta.reviews:
                if hasattr(review, 'stars'):
                    # ReviewData object
                    reviews.append({"stars": review.stars, "review_text": review.review_text, "date": review.date})
                else:
                    # Already a dictionary
                    reviews.append(review)
            
            # Improve source type detection using identifier/title/description
            improved_source_type = improve_source_type_detection(recording_meta)
            
            # Calculate comprehensive rating statistics
            rating_stats = self.calculate_recording_rating(reviews)
            
            # Build recording rating entry
            recording_ratings[recording_meta.identifier] = {
                "rating": recording_meta.rating,  # Existing weighted rating
                "raw_rating": rating_stats["raw_rating"],
                "review_count": recording_meta.review_count,
                "source_type": improved_source_type,  # Use improved source type
                "confidence": recording_meta.confidence,
                "distribution": rating_stats["distribution"],
                "high_ratings": rating_stats["high_ratings"],
                "low_ratings": rating_stats["low_ratings"],
                "date": recording_meta.date,
                "venue": recording_meta.venue
            }
        
        self.logger.info(f"Generated ratings for {len(recording_ratings)} recordings")
        return recording_ratings
    
    def generate_show_ratings(self, recording_ratings: Dict[str, Any]) -> Dict[str, Any]:
        """Generate show-level rating aggregations."""
        self.logger.info("Generating show-level rating aggregations...")
        
        shows_data = defaultdict(list)
        
        # Group recordings by show (date + venue)
        for identifier, rating_data in recording_ratings.items():
            show_key = f"{rating_data['date']}_{rating_data['venue'].replace(' ', '_')}"
            shows_data[show_key].append(rating_data)
        
        show_ratings = {}
        
        # Generate show-level statistics
        for show_key, show_recordings in shows_data.items():
            if len(show_recordings) == 0:
                continue
            
            # Sort recordings by preference (SBD > others, then by rating)
            show_recordings.sort(key=lambda r: (
                r['source_type'] == 'SBD' and r['review_count'] >= 3,
                r['review_count'] >= 5,
                r['rating'],
                r['review_count']
            ), reverse=True)
            
            best_recording = show_recordings[0]
            
            # Compute show-level weighted rating
            total_weight = 0
            weighted_sum = 0
            
            for recording in show_recordings:
                weight = recording['review_count'] * self.source_weights.get(recording['source_type'], 0.5)
                weighted_sum += recording['rating'] * weight
                total_weight += weight
            
            show_rating = weighted_sum / total_weight if total_weight > 0 else 0
            
            # Compute show-level raw rating (simple average)
            total_raw_rating = sum(r['raw_rating'] * r['review_count'] for r in show_recordings if r['review_count'] > 0)
            total_review_count = sum(r['review_count'] for r in show_recordings)
            show_raw_rating = total_raw_rating / total_review_count if total_review_count > 0 else 0
            
            # Sum high and low ratings across all recordings
            total_high_ratings = sum(r['high_ratings'] for r in show_recordings)
            total_low_ratings = sum(r['low_ratings'] for r in show_recordings)
            
            # Confidence calculation
            confidence = min(total_review_count / 10.0, 1.0)
            
            show_ratings[show_key] = {
                "date": best_recording['date'],
                "venue": best_recording['venue'],
                "rating": show_rating,
                "raw_rating": show_raw_rating,
                "confidence": confidence,
                "best_recording": next(r for r in recording_ratings if recording_ratings[r]['date'] == best_recording['date'] and 
                                    recording_ratings[r]['venue'] == best_recording['venue'] and
                                    recording_ratings[r]['rating'] == best_recording['rating']),
                "recording_count": len(show_recordings),
                "total_high_ratings": total_high_ratings,
                "total_low_ratings": total_low_ratings
            }
        
        self.logger.info(f"Generated ratings for {len(show_ratings)} shows")
        return show_ratings
    
    def generate_ratings_json(self, recording_ratings: Dict[str, Any], show_ratings: Dict[str, Any]):
        """Generate final recording_ratings.json file."""
        self.logger.info("Generating recording ratings JSON file...")
        
        # Create complete structure
        ratings_data = {
            "metadata": {
                "generated_at": datetime.now().isoformat(),
                "version": "2.0.0",
                "total_recordings": len(recording_ratings),
                "total_shows": len(show_ratings),
                "description": "Comprehensive recording and show rating statistics from Archive.org data"
            },
            "recording_ratings": recording_ratings,
            "show_ratings": show_ratings
        }
        
        # Write JSON file
        with open(self.output_file, 'w') as f:
            json.dump(ratings_data, f, indent=2, sort_keys=True)
        
        # Get file size
        json_size = os.path.getsize(self.output_file) / (1024 * 1024)  # MB
        
        self.logger.info(f"Generated {self.output_file}: {json_size:.1f}MB")
        self.logger.info(f"Contains {len(recording_ratings)} recordings and {len(show_ratings)} shows")
    
    def process_all_ratings(self) -> bool:
        """Process all rating statistics from cached data."""
        start_time = datetime.now()
        
        # Load cached recordings
        recordings = self.load_cached_recordings()
        if not recordings:
            self.logger.error("No recordings loaded. Cannot proceed.")
            return False
        
        # Generate recording-level ratings
        recording_ratings = self.generate_recording_ratings(recordings)
        
        # Generate show-level ratings
        show_ratings = self.generate_show_ratings(recording_ratings)
        
        # Generate final JSON output
        self.generate_ratings_json(recording_ratings, show_ratings)
        
        # Report processing time
        processing_time = (datetime.now() - start_time).total_seconds()
        self.logger.info(f"✅ Processing completed in {processing_time:.1f} seconds")
        
        # Summary
        self.logger.info("=== Generation Summary ===")
        self.logger.info(f"Input recordings: {len(recordings)}")
        self.logger.info(f"Recording ratings generated: {len(recording_ratings)}")
        self.logger.info(f"Show ratings generated: {len(show_ratings)}")
        self.logger.info(f"Output file: {self.output_file}")
        
        return True


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description='Generate recording ratings from cached Archive.org data')
    parser.add_argument('--input-dir', default='stage01-collected-data/archive', 
                       help='Input directory with cached recordings')
    parser.add_argument('--output-file', default='stage02-generated-data/recording_ratings.json',
                       help='Output file for recording ratings')
    parser.add_argument('--verbose', action='store_true',
                       help='Enable verbose logging')
    
    args = parser.parse_args()
    
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    generator = RecordingRatingsGenerator(
        input_dir=args.input_dir,
        output_file=args.output_file
    )
    
    # Validate input
    if not generator.validate_input_data():
        return 1
    
    # Create output directory
    generator.create_output_directory()
    
    # Process all ratings
    success = generator.process_all_ratings()
    
    if success:
        print(f"✅ Recording ratings generation complete! Output: {args.output_file}")
        return 0
    else:
        print("❌ Recording ratings generation failed. Check logs for details.")
        return 1


if __name__ == '__main__':
    exit(main())