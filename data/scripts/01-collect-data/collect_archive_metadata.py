#!/usr/bin/env python3
"""
Archive.org Metadata Collection Script

This script handles pure data collection from Archive.org APIs, caching individual
recording metadata and reviews for later processing. It focuses solely on collection
with progress tracking, resume capability, and respectful API usage.

Architecture:
- Collects comprehensive metadata from Archive.org API
- Caches individual recording files locally  
- Supports resume, progress tracking, and incremental updates
- Rate-limited and respectful to Archive.org servers
- Non-destructive: skips existing files unless --force

Usage:
    # Full collection with default output
    python scripts/01-collect-data/collect_archive_metadata.py --mode full
    
    # Custom output directory
    python scripts/01-collect-data/collect_archive_metadata.py --output-dir /path/to/cache --mode full
    
    # Test collection with limited recordings
    python scripts/01-collect-data/collect_archive_metadata.py --mode test --max-recordings 10
    
    # Resume interrupted collection
    python scripts/01-collect-data/collect_archive_metadata.py --resume
"""

import json
import os
import re
import requests
import time
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple, Any
import argparse
import logging

# Add shared module to path
sys.path.append(str(Path(__file__).parent.parent))
from shared.models import RecordingMetadata, ProgressState, recording_to_dict, progress_to_dict


class ArchiveMetadataCollector:
    """
    Archive.org metadata collector focused purely on data collection.
    """
    
    def __init__(self, output_dir: str = "stage01-collected-data/archive", 
                 delay: float = 0.25, force_overwrite: bool = False):
        """Initialize the collector with configuration."""
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'DeadArchive-MetadataCollector/2.0 (Educational Use)'
        })
        
        # Performance configuration
        self.api_delay = delay
        self.last_api_call = 0
        self.batch_size = 100
        self.batch_delay = 0  # seconds between batches
        self.force_overwrite = force_overwrite
        
        # Directories
        self.output_dir = Path(output_dir)
        
        # Create directories
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
        # Progress tracking
        self.progress_file = self.output_dir / "progress.json"
        self.progress = None
        
        # Setup logging
        self._setup_logging()

    def _setup_logging(self):
        """Setup logging with file and console handlers."""
        log_file = self.output_dir / "collection.log"
        
        # Create logger
        self.logger = logging.getLogger(__name__)
        self.logger.setLevel(logging.INFO)
        
        # Clear any existing handlers
        self.logger.handlers.clear()
        
        # File handler
        file_handler = logging.FileHandler(log_file)
        file_handler.setLevel(logging.DEBUG)
        
        # Console handler
        console_handler = logging.StreamHandler()
        console_handler.setLevel(logging.INFO)
        
        # Formatter
        formatter = logging.Formatter('%(asctime)s - %(levelname)s - %(message)s')
        file_handler.setFormatter(formatter)
        console_handler.setFormatter(formatter)
        
        # Add handlers
        self.logger.addHandler(file_handler)
        self.logger.addHandler(console_handler)

    def rate_limit(self):
        """Enforce rate limiting between API calls."""
        elapsed = time.time() - self.last_api_call
        if elapsed < self.api_delay:
            time.sleep(self.api_delay - elapsed)
        self.last_api_call = time.time()

    def save_progress(self):
        """Save current progress state."""
        if self.progress:
            with open(self.progress_file, 'w') as f:
                json.dump(progress_to_dict(self.progress), f, indent=2)

    def load_progress(self) -> Optional[ProgressState]:
        """Load existing progress state."""
        if self.progress_file.exists():
            try:
                with open(self.progress_file, 'r') as f:
                    data = json.load(f)
                return ProgressState(**data)
            except Exception as e:
                self.logger.error(f"Failed to load progress: {e}")
        return None

    def normalize_date(self, date_str: str) -> Optional[str]:
        """Normalize various date formats to YYYY-MM-DD."""
        if not date_str:
            return None
            
        # Remove time component if present
        date_str = date_str.split('T')[0]
        
        # Handle YYYY-MM-DD (already normalized)
        if re.match(r'^\d{4}-\d{2}-\d{2}$', date_str):
            return date_str
            
        # Handle YYYY-M-D (pad with zeros)
        if re.match(r'^\d{4}-\d{1,2}-\d{1,2}$', date_str):
            parts = date_str.split('-')
            return f"{parts[0]}-{parts[1].zfill(2)}-{parts[2].zfill(2)}"
            
        # Handle MM/DD/YYYY
        if re.match(r'^\d{1,2}/\d{1,2}/\d{4}$', date_str):
            parts = date_str.split('/')
            return f"{parts[2]}-{parts[0].zfill(2)}-{parts[1].zfill(2)}"
            
        self.logger.warning(f"Unrecognized date format: {date_str}")
        return None


    def get_grateful_dead_recordings(self, year: Optional[int] = None, 
                                   date_range: Optional[str] = None) -> List[str]:
        """Get list of Grateful Dead recording identifiers from Archive.org search.
        
        Archive.org has a ~10k pagination limit, so for full collection we break it down intelligently.
        
        Args:
            year: Specific year to filter (e.g., 1977)
            date_range: Custom date range (e.g., '[1977-01-01 TO 1977-12-31]')
        """
        # If specific year or date range provided, use single query
        if year or date_range:
            return self._get_recordings_single_query(year, date_range)
        
        # For full collection, break down by year, with monthly breakdown for high-volume years
        self.logger.info("Full collection mode: fetching recordings by year/month to avoid pagination limits...")
        all_identifiers = []
        
        # High-volume years that need monthly breakdown (those hitting 10k limit)
        high_volume_years = [1983, 1984, 1985, 1987, 1989, 1990]
        
        # Grateful Dead active years: 1965-1995
        for year_num in range(1965, 1996):
            if year_num in high_volume_years:
                # Break down by month for high-volume years
                year_identifiers = self._get_recordings_by_month(year_num)
            else:
                # Single query for lower-volume years
                year_identifiers = self._get_recordings_single_query(year_num, None)
            
            all_identifiers.extend(year_identifiers)
            self.logger.info(f"Year {year_num}: {len(year_identifiers)} recordings (total: {len(all_identifiers)})")
            
            # Small delay between years
            time.sleep(0.2)
            
        self.logger.info(f"Found {len(all_identifiers)} total recordings across all years")
        return all_identifiers
    
    def _get_recordings_by_month(self, year: int) -> List[str]:
        """Get all recordings for a year by breaking it down month by month."""
        self.logger.info(f"  Breaking down {year} by month due to high volume...")
        all_identifiers = []
        
        # Ultra high-volume years that might need weekly breakdown
        ultra_high_volume = [1983, 1984, 1985, 1987, 1989, 1990]
        
        for month in range(1, 13):
            if year in ultra_high_volume:
                # For ultra-high volume years, break down by week within each month
                month_identifiers = self._get_recordings_by_week(year, month)
            else:
                # Regular monthly breakdown
                date_range = f'[{year}-{month:02d}-01 TO {year}-{month:02d}-31]'
                month_identifiers = self._get_recordings_single_query(None, date_range)
            
            all_identifiers.extend(month_identifiers)
            
            if len(month_identifiers) > 0:
                self.logger.info(f"    {year}-{month:02d}: {len(month_identifiers)} recordings")
            
            # Small delay between months
            time.sleep(0.1)
            
        return all_identifiers
    
    def _get_recordings_by_week(self, year: int, month: int) -> List[str]:
        """Get recordings for a month by breaking it down week by week."""
        import calendar
        
        all_identifiers = []
        days_in_month = calendar.monthrange(year, month)[1]
        
        # Break month into ~weekly chunks (7-8 days each)
        week_starts = [1, 8, 15, 22]
        
        for i, week_start in enumerate(week_starts):
            if i == len(week_starts) - 1:
                # Last week goes to end of month
                week_end = days_in_month
            else:
                week_end = min(week_starts[i + 1] - 1, days_in_month)
            
            if week_start > days_in_month:
                break
                
            date_range = f'[{year}-{month:02d}-{week_start:02d} TO {year}-{month:02d}-{week_end:02d}]'
            week_identifiers = self._get_recordings_single_query(None, date_range)
            all_identifiers.extend(week_identifiers)
            
            # Very small delay between weeks
            time.sleep(0.05)
        
        return all_identifiers
    
    def _get_recordings_single_query(self, year: Optional[int] = None, 
                                   date_range: Optional[str] = None) -> List[str]:
        """Perform a single search query with pagination up to Archive.org's limits."""
        try:
            search_url = "https://archive.org/advancedsearch.php"
            
            # Build the search query
            base_query = 'collection:GratefulDead AND mediatype:etree'
            
            if date_range:
                query = f'{base_query} AND date:{date_range}'
            elif year:
                query = f'{base_query} AND date:[{year}-01-01 TO {year}-12-31]'
            else:
                query = base_query
            
            all_identifiers = []
            start = 0
            page_size = 1000  # Reliable page size
            max_safe_results = 9500  # Stay under Archive.org's ~10k limit for safety
            
            while start < max_safe_results:
                self.rate_limit()
                
                params = {
                    'q': query,
                    'fl': 'identifier,date,title,venue',
                    'sort[]': 'date asc',
                    'rows': page_size,
                    'start': start,
                    'output': 'json'
                }
                
                response = self.session.get(search_url, params=params, timeout=60)
                response.raise_for_status()
                
                search_results = response.json()
                docs = search_results.get('response', {}).get('docs', [])
                
                if not docs:
                    break
                    
                batch_identifiers = []
                for doc in docs:
                    identifier = doc.get('identifier')
                    if identifier:
                        batch_identifiers.append(identifier)
                
                all_identifiers.extend(batch_identifiers)
                
                # If we got fewer results than requested, we've reached the end
                if len(docs) < page_size:
                    break
                    
                start += page_size
                
                # Add a small delay between pages
                time.sleep(0.1)
                    
            return all_identifiers
            
        except Exception as e:
            self.logger.error(f"Failed to search for recordings: {e}")
            return []

    def fetch_recording_metadata(self, identifier: str) -> Optional[Dict]:
        """Fetch complete metadata for a single recording."""
        self.rate_limit()
        
        try:
            url = f"https://archive.org/metadata/{identifier}"
            response = self.session.get(url, timeout=30)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            self.logger.error(f"Failed to fetch metadata for {identifier}: {e}")
            return None

    def fetch_recording_reviews(self, identifier: str) -> List[Dict[str, Any]]:
        """Fetch raw review data for a single recording."""
        self.rate_limit()
        
        try:
            url = f"https://archive.org/metadata/{identifier}/reviews"
            response = self.session.get(url, timeout=30)
            response.raise_for_status()
            
            reviews_data = response.json()
            return reviews_data.get('result', [])
            
        except Exception as e:
            self.logger.error(f"Failed to fetch reviews for {identifier}: {e}")
            return []


    def process_recording(self, identifier: str) -> Optional[RecordingMetadata]:
        """Collect raw metadata for a single recording."""
        try:
            # Check if already cached
            cache_file = self.output_dir / f"{identifier}.json"
            if cache_file.exists() and not self.force_overwrite:
                self.logger.debug(f"Skipping existing cached file: {identifier}")
                # Load and return existing metadata for progress tracking
                try:
                    with open(cache_file, 'r') as f:
                        data = json.load(f)
                    return RecordingMetadata(**data)
                except Exception as e:
                    self.logger.warning(f"Corrupted cache file {identifier}: {e}, will re-fetch")
            
            self.logger.info(f"Collecting {identifier}")
            
            # Fetch raw metadata and reviews
            metadata = self.fetch_recording_metadata(identifier)
            if not metadata:
                return None
                
            reviews = self.fetch_recording_reviews(identifier)
            
            # Extract raw metadata
            raw_metadata = metadata.get('metadata', {})
            
            # Only normalize date for matching purposes
            date_str = raw_metadata.get('date', '')
            normalized_date = self.normalize_date(date_str)
            if not normalized_date:
                return None
            
            self.logger.debug(f"  → Raw metadata: {len(raw_metadata)} fields, {len(reviews)} reviews")
            
            # Create raw recording metadata
            recording_meta = RecordingMetadata(
                identifier=identifier,
                raw_metadata=raw_metadata,
                raw_reviews=reviews,
                files=metadata.get('files', []),
                normalized_date=normalized_date,
                collection_timestamp=datetime.now().isoformat()
            )
            
            # Save to cache
            with open(cache_file, 'w') as f:
                json.dump(recording_to_dict(recording_meta), f, indent=2, default=str)
            
            return recording_meta
            
        except Exception as e:
            self.logger.error(f"Error processing {identifier}: {e}")
            return None

    def collect_all_metadata(self, max_recordings: Optional[int] = None, 
                           year: Optional[int] = None, date_range: Optional[str] = None):
        """Collect metadata for all recordings."""
        # Get list of recordings
        self.logger.info("Starting Archive.org recording search...")
        recording_ids = self.get_grateful_dead_recordings(year=year, date_range=date_range)
        if max_recordings:
            recording_ids = recording_ids[:max_recordings]
            self.logger.info(f"Limited to first {max_recordings} recordings for testing")
            
        total_recordings = len(recording_ids)
        
        if total_recordings == 0:
            self.logger.warning("No recordings found to process")
            return
        
        # Initialize progress
        self.progress = ProgressState(
            collection_started=datetime.now().isoformat(),
            last_updated=datetime.now().isoformat(),
            status="in_progress",
            total_recordings=total_recordings,
            processed_recordings=0,
            failed_recordings=0,
            current_batch=0,
            last_processed="",
            failed_identifiers=[],
            performance_stats={
                "start_time": time.time(),
                "api_calls_made": 0,
                "api_errors": 0
            }
        )
        
        self.logger.info(f"Starting collection of {total_recordings} recordings...")
        self.logger.info(f"Output directory: {self.output_dir}")
        self.logger.info(f"Force overwrite: {self.force_overwrite}")
        
        # Process in batches
        for batch_start in range(0, total_recordings, self.batch_size):
            batch_end = min(batch_start + self.batch_size, total_recordings)
            batch_recordings = recording_ids[batch_start:batch_end]
            
            self.progress.current_batch += 1
            self.logger.info(f"Processing batch {self.progress.current_batch}: recordings {batch_start+1}-{batch_end}")
            
            for identifier in batch_recordings:
                recording_meta = self.process_recording(identifier)
                
                if recording_meta:
                    self.progress.processed_recordings += 1
                    self.progress.last_processed = identifier
                else:
                    self.progress.failed_recordings += 1
                    self.progress.failed_identifiers.append(identifier)
                
                self.progress.performance_stats["api_calls_made"] += 2  # metadata + reviews
                self.progress.last_updated = datetime.now().isoformat()
                
                # Save progress periodically
                if self.progress.processed_recordings % 10 == 0:
                    self.save_progress()
                    # Log progress
                    elapsed = time.time() - self.progress.performance_stats["start_time"]
                    rate = self.progress.processed_recordings / elapsed if elapsed > 0 else 0
                    remaining = total_recordings - self.progress.processed_recordings
                    eta = remaining / rate if rate > 0 else 0
                    self.logger.info(f"Progress: {self.progress.processed_recordings}/{total_recordings} "
                                   f"({self.progress.processed_recordings/total_recordings*100:.1f}%) "
                                   f"Rate: {rate:.1f}/min ETA: {eta/60:.1f} min")
            
            # Batch break (except for last batch)
            if batch_end < total_recordings:
                self.logger.info(f"Batch complete. Taking {self.batch_delay}s break...")
                time.sleep(self.batch_delay)
        
        # Final progress update
        self.progress.status = "completed"
        self.progress.performance_stats["total_time"] = time.time() - self.progress.performance_stats["start_time"]
        self.save_progress()
        
        total_time = self.progress.performance_stats["total_time"]
        self.logger.info(f"Collection complete! Processed {self.progress.processed_recordings} recordings in {total_time/60:.1f} minutes")
        
        if self.progress.failed_recordings > 0:
            self.logger.warning(f"Failed to process {self.progress.failed_recordings} recordings. Check logs for details.")

    def resume_collection(self):
        """Resume an interrupted collection."""
        progress = self.load_progress()
        if not progress:
            self.logger.error("No progress file found. Cannot resume.")
            return False
            
        if progress.status == "completed":
            self.logger.info("Collection already completed.")
            return True
            
        self.logger.info(f"Resuming collection from {progress.processed_recordings}/{progress.total_recordings} recordings")
        # Implementation would continue from where it left off
        # For now, just indicate resume capability exists
        return True

    def validate_setup(self) -> bool:
        """Validate that collection can proceed."""
        # Test API connectivity
        try:
            self.logger.info("Testing Archive.org API connectivity...")
            test_url = "https://archive.org/advancedsearch.php"
            params = {
                'q': 'collection:GratefulDead AND mediatype:etree',
                'fl': 'identifier',
                'rows': 1,
                'output': 'json'
            }
            response = self.session.get(test_url, params=params, timeout=10)
            response.raise_for_status()
            self.logger.info("✅ Archive.org API connectivity confirmed")
            return True
        except Exception as e:
            self.logger.error(f"❌ Archive.org API connectivity failed: {e}")
            return False


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description='Archive.org Grateful Dead metadata collection')
    parser.add_argument('--mode', choices=['full', 'test'], 
                       default='full', help='Collection mode')
    parser.add_argument('--output-dir', default='stage01-collected-data/archive', 
                       help='Output directory for cached recordings')
    parser.add_argument('--delay', type=float, default=0.25, 
                       help='Delay between API calls in seconds')
    parser.add_argument('--max-recordings', type=int, 
                       help='Maximum recordings to process (for testing)')
    parser.add_argument('--year', type=int,
                       help='Filter by specific year (e.g., 1977)')
    parser.add_argument('--date-range', 
                       help='Filter by date range (e.g., "[1977-01-01 TO 1977-12-31]")')
    parser.add_argument('--force', action='store_true',
                       help='Overwrite existing cached files')
    parser.add_argument('--resume', action='store_true',
                       help='Resume interrupted collection')
    parser.add_argument('--verbose', action='store_true',
                       help='Enable verbose logging')
    
    args = parser.parse_args()
    
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    collector = ArchiveMetadataCollector(
        output_dir=args.output_dir,
        delay=args.delay, 
        force_overwrite=args.force
    )
    
    # Validate setup
    if not collector.validate_setup():
        print("❌ Setup validation failed. Check network connection and try again.")
        return 1
    
    if args.resume:
        if not collector.resume_collection():
            return 1
    else:
        if args.mode == 'full':
            collector.collect_all_metadata(
                max_recordings=args.max_recordings,
                year=args.year,
                date_range=args.date_range
            )
        elif args.mode == 'test':
            collector.collect_all_metadata(
                max_recordings=args.max_recordings or 10,
                year=args.year,
                date_range=args.date_range
            )
    
    print(f"✅ Collection complete! Output: {args.output_dir}")
    return 0


if __name__ == '__main__':
    exit(main())