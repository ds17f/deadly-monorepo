#!/usr/bin/env python3
"""
Collections Processing Script

Processes collection definitions to:
1. Resolve collection selectors to actual show IDs
2. Add collection membership to show files  
3. Generate collection output files for search/display

Usage:
    python scripts/02-generate-data/process_collections.py --verbose
"""

import json
import os
import sys
from datetime import datetime, date
from pathlib import Path
from typing import Dict, List, Optional, Set, Any
import argparse
import logging

# Add shared module to path
sys.path.append(str(Path(__file__).parent.parent))


class CollectionProcessor:
    """
    Processor for Grateful Dead collections.
    
    Converts date-based collection selectors into show ID lists and adds
    collection metadata to shows.
    """
    
    def __init__(self, 
                 collections_file: str = "stage00-created-data/dead_collections.json",
                 shows_dir: str = "stage02-generated-data/shows",
                 output_dir: str = "stage02-generated-data"):
        """Initialize the collection processor."""
        self.collections_file = Path(collections_file)
        self.shows_dir = Path(shows_dir)
        self.output_dir = Path(output_dir)
        
        # Collections data
        self.collections_data = None
        self.show_files = {}  # Map of date -> show_id for fast lookup
        
        # Setup logging
        self._setup_logging()
    
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
    
    def load_collections_data(self) -> bool:
        """Load collections definitions from JSON file."""
        if not self.collections_file.exists():
            self.logger.error(f"❌ Collections file not found: {self.collections_file}")
            return False
        
        try:
            with open(self.collections_file, 'r', encoding='utf-8') as f:
                self.collections_data = json.load(f)
            
            collection_count = len(self.collections_data.get('collections', []))
            self.logger.info(f"📚 Loaded {collection_count} collection definitions")
            return True
            
        except Exception as e:
            self.logger.error(f"❌ Error loading collections: {e}")
            return False
    
    def index_show_files(self) -> bool:
        """Build index of show files by date for fast lookup."""
        if not self.shows_dir.exists():
            self.logger.error(f"❌ Shows directory not found: {self.shows_dir}")
            return False
        
        show_count = 0
        for show_file in self.shows_dir.glob("*.json"):
            try:
                with open(show_file, 'r', encoding='utf-8') as f:
                    show_data = json.load(f)
                
                show_date = show_data.get('date')
                show_id = show_data.get('show_id')
                
                if show_date and show_id:
                    # Handle multiple shows per date
                    if show_date not in self.show_files:
                        self.show_files[show_date] = []
                    self.show_files[show_date].append({
                        'show_id': show_id,
                        'file_path': show_file,
                        'venue': show_data.get('venue', ''),
                        'show_time': show_data.get('show_time')
                    })
                    show_count += 1
                    
            except Exception as e:
                self.logger.warning(f"⚠️ Could not index show file {show_file}: {e}")
                continue
        
        self.logger.info(f"📊 Indexed {show_count} shows across {len(self.show_files)} unique dates")
        return True
    
    def resolve_collection_shows(self, collection: Dict[str, Any]) -> Set[str]:
        """
        Resolve collection selector to actual show IDs.
        
        Returns set of show_ids that match the collection criteria.
        """
        show_selector = collection.get('show_selector', {})
        matched_shows = set()
        
        # Handle specific dates
        if 'dates' in show_selector:
            for date_str in show_selector['dates']:
                if date_str in self.show_files:
                    for show in self.show_files[date_str]:
                        matched_shows.add(show['show_id'])
        
        # Handle date ranges
        if 'range' in show_selector:
            range_def = show_selector['range']
            start_date = datetime.strptime(range_def['start'], '%Y-%m-%d').date()
            end_date = datetime.strptime(range_def['end'], '%Y-%m-%d').date()
            
            for date_str, shows in self.show_files.items():
                show_date = datetime.strptime(date_str, '%Y-%m-%d').date()
                if start_date <= show_date <= end_date:
                    for show in shows:
                        matched_shows.add(show['show_id'])
        
        # Handle additional dates
        if 'additional_dates' in show_selector:
            for date_str in show_selector['additional_dates']:
                if date_str in self.show_files:
                    for show in self.show_files[date_str]:
                        matched_shows.add(show['show_id'])
        
        # Handle exclusions
        if 'exclusion_dates' in show_selector:
            for date_str in show_selector['exclusion_dates']:
                if date_str in self.show_files:
                    for show in self.show_files[date_str]:
                        matched_shows.discard(show['show_id'])
        
        if 'exclusion_ranges' in show_selector:
            for exclusion in show_selector['exclusion_ranges']:
                start_date = datetime.strptime(exclusion['from'], '%Y-%m-%d').date()
                end_date = datetime.strptime(exclusion['to'], '%Y-%m-%d').date()
                
                for date_str, shows in self.show_files.items():
                    show_date = datetime.strptime(date_str, '%Y-%m-%d').date()
                    if start_date <= show_date <= end_date:
                        for show in shows:
                            matched_shows.discard(show['show_id'])
        
        return matched_shows
    
    def process_collections(self) -> bool:
        """
        Process all collections and generate output files.
        
        1. Resolve collection selectors to show IDs
        2. Add collection metadata to show files
        3. Generate collection summary files
        4. Generate failure report for troubleshooting
        """
        if not self.collections_data:
            self.logger.error("❌ No collections data loaded")
            return False
        
        processed_collections = []
        show_collections_map = {}  # Map show_id -> [collection_ids]
        failed_collections = []  # Track collections with no matches
        
        # Process collections from JSON
        for collection in self.collections_data.get('collections', []):
            collection_id = collection['id']
            
            # Resolve collection to show IDs
            show_ids = self.resolve_collection_shows(collection)
            
            if len(show_ids) > 0:
                # Add resolved show IDs to collection
                processed_collection = collection.copy()
                processed_collection['show_ids'] = sorted(list(show_ids))  # Sort for consistency
                processed_collection['total_shows'] = len(show_ids)
                processed_collections.append(processed_collection)
                
                # Update show -> collections mapping
                for show_id in show_ids:
                    if show_id not in show_collections_map:
                        show_collections_map[show_id] = []
                    show_collections_map[show_id].append(collection_id)
                
                self.logger.info(f"✅ Resolved '{collection_id}': {len(show_ids)} shows")
            else:
                # Track failure details for troubleshooting
                failure_info = self._analyze_collection_failure(collection)
                failed_collections.append(failure_info)
                self.logger.warning(f"⚠️ Collection '{collection_id}' matched 0 shows")
        
        # Update show files with collection membership
        self._update_show_files_with_collections(show_collections_map)
        
        # Generate collection output files
        self._generate_collection_files(processed_collections)
        
        # Generate failure report only if there are failures
        if len(failed_collections) > 0:
            self._generate_failure_report(failed_collections)
        
        self.logger.info(f"🎉 Processed {len(processed_collections)} collections total")
        if len(failed_collections) > 0:
            self.logger.info(f"⚠️ {len(failed_collections)} collections failed - see collection_failures.json for details")
        
        return True
    
    def _update_show_files_with_collections(self, show_collections_map: Dict[str, List[str]]):
        """Add collection membership to individual show files."""
        updated_count = 0
        
        for date_str, shows in self.show_files.items():
            for show in shows:
                show_id = show['show_id']
                file_path = show['file_path']
                
                if show_id in show_collections_map:
                    try:
                        # Load show data
                        with open(file_path, 'r', encoding='utf-8') as f:
                            show_data = json.load(f)
                        
                        # Add collections field (sorted for consistency)
                        show_data['collections'] = sorted(show_collections_map[show_id])
                        
                        # Write back to file
                        with open(file_path, 'w', encoding='utf-8') as f:
                            json.dump(show_data, f, indent=2, ensure_ascii=False)
                        
                        updated_count += 1
                        
                    except Exception as e:
                        self.logger.warning(f"⚠️ Could not update {file_path}: {e}")
                        continue
        
        self.logger.info(f"📝 Updated {updated_count} show files with collection membership")
    
    def _generate_collection_files(self, processed_collections: List[Dict[str, Any]]):
        """Generate collection summary and search files."""
        
        # Generate collections.json for app consumption
        collections_output = {
            "collections": processed_collections,
            "total_collections": len(processed_collections),
            "generation_timestamp": datetime.now().isoformat()
        }
        
        collections_file = self.output_dir / "collections.json"
        with open(collections_file, 'w', encoding='utf-8') as f:
            json.dump(collections_output, f, indent=2, ensure_ascii=False)
        
        self.logger.info(f"📄 Generated collections file: {collections_file}")
        
        # Generate collection search index for Stage 3
        self._generate_collection_search_data(processed_collections)
    
    def _generate_collection_search_data(self, processed_collections: List[Dict[str, Any]]):
        """Generate collection search data for Stage 3 integration."""
        
        search_collections = {}
        
        for collection in processed_collections:
            collection_id = collection['id']
            collection_name = collection['name']
            
            # Normalize collection name for search
            search_key = collection_name.lower().replace(' ', '-').replace("'", "").replace('"', '')
            
            # Build search entry
            search_collections[search_key] = {
                "collection_id": collection_id,
                "name": collection_name,
                "description": collection.get('description', ''),
                "tags": collection.get('tags', []),
                "total_shows": collection.get('total_shows', 0),
                "show_ids": collection.get('show_ids', [])[:10],  # First 10 for preview
                
                # Add search aliases for common collections
                "aliases": self._get_collection_aliases(collection_id, collection_name)
            }
        
        # Save to stage03-search-data for integration with search tables
        search_dir = Path("stage03-search-data")
        search_dir.mkdir(exist_ok=True)
        
        collections_search_file = search_dir / "collections.json"
        with open(collections_search_file, 'w', encoding='utf-8') as f:
            json.dump(search_collections, f, indent=2, ensure_ascii=False)
        
        self.logger.info(f"🔍 Generated collection search data: {collections_search_file}")
    
    def _get_collection_aliases(self, collection_id: str, collection_name: str) -> List[str]:
        """Generate search aliases for common collection names."""
        aliases = []
        
        # Dick's Picks aliases
        if 'dicks-picks' in collection_id:
            aliases.extend(['dp', 'dick picks', 'dicks picks'])
            # Add volume-specific aliases
            if 'vol-' in collection_id:
                vol_num = collection_id.split('vol-')[-1]
                aliases.extend([f'dp{vol_num}', f'dp {vol_num}', f'volume {vol_num}'])
        
        # Era-based aliases
        era_aliases = {
            'pigpen': ['pigpen', 'ron mckernan', 'blues era'],
            'keith': ['keith godchaux', 'keith years'],
            'donna': ['donna godchaux', 'donna jean'],
            'brent': ['brent mydland', 'brent years'],
            'bruce': ['bruce hornsby', 'hornsby']
        }
        
        for key, alias_list in era_aliases.items():
            if key in collection_id.lower():
                aliases.extend(alias_list)
        
        # Venue aliases
        if 'fillmore' in collection_id.lower():
            aliases.extend(['fillmore', 'fillmore west', 'fillmore east'])
        
        return aliases
    
    def _analyze_collection_failure(self, collection: Dict[str, Any]) -> Dict[str, Any]:
        """
        Analyze why a collection failed to match any shows.
        
        Returns detailed failure information for troubleshooting.
        """
        collection_id = collection['id']
        collection_name = collection['name']
        show_selector = collection.get('show_selector', {})
        
        failure_info = {
            'collection_id': collection_id,
            'collection_name': collection_name,
            'description': collection.get('description', ''),
            'tags': collection.get('tags', []),
            'failure_type': 'unknown',
            'details': {},
            'suggestions': []
        }
        
        # Analyze specific dates
        if 'dates' in show_selector:
            missing_dates = []
            found_dates = []
            
            for date_str in show_selector['dates']:
                if date_str in self.show_files:
                    found_dates.append({
                        'date': date_str,
                        'shows': [show['show_id'] for show in self.show_files[date_str]]
                    })
                else:
                    missing_dates.append(date_str)
            
            failure_info['failure_type'] = 'missing_dates'
            failure_info['details'] = {
                'total_dates': len(show_selector['dates']),
                'missing_dates': missing_dates,
                'found_dates': found_dates,
                'missing_count': len(missing_dates),
                'found_count': len(found_dates)
            }
            
            if len(missing_dates) == len(show_selector['dates']):
                failure_info['suggestions'].append("All dates are missing from the dataset")
            elif len(missing_dates) > 0:
                failure_info['suggestions'].append(f"{len(missing_dates)} out of {len(show_selector['dates'])} dates are missing")
            
            # Check for similar dates (off by one day, etc.)
            similar_dates = self._find_similar_dates(missing_dates)
            if similar_dates:
                failure_info['suggestions'].append(f"Similar dates found: {similar_dates}")
        
        # Analyze date ranges
        elif 'range' in show_selector:
            range_def = show_selector['range']
            start_date = range_def['start']
            end_date = range_def['end']
            
            # Count shows in range
            range_shows = []
            try:
                from datetime import datetime
                start_dt = datetime.strptime(start_date, '%Y-%m-%d').date()
                end_dt = datetime.strptime(end_date, '%Y-%m-%d').date()
                
                for date_str, shows in self.show_files.items():
                    try:
                        show_date = datetime.strptime(date_str, '%Y-%m-%d').date()
                        if start_dt <= show_date <= end_dt:
                            range_shows.extend([show['show_id'] for show in shows])
                    except ValueError:
                        continue
                        
            except ValueError:
                failure_info['suggestions'].append(f"Invalid date format in range: {start_date} to {end_date}")
            
            failure_info['failure_type'] = 'range_exclusion'
            failure_info['details'] = {
                'date_range': f"{start_date} to {end_date}",
                'shows_in_range': len(range_shows),
                'exclusion_dates': show_selector.get('exclusion_dates', []),
                'exclusion_ranges': show_selector.get('exclusion_ranges', [])
            }
            
            if len(range_shows) == 0:
                failure_info['suggestions'].append("No shows found in the specified date range")
            else:
                failure_info['suggestions'].append(f"Found {len(range_shows)} shows in range, but all excluded by filters")
        
        # Check for common issues
        if collection_id.startswith('dicks-picks') or collection_id.startswith('daves-picks'):
            failure_info['suggestions'].append("Official release collections may reference shows not in jerrygarcia.com dataset")
        
        if 'absence' in collection_id:
            failure_info['suggestions'].append("Absence collections use exclusion-only logic and may legitimately match 0 shows")
        
        return failure_info
    
    def _find_similar_dates(self, missing_dates: List[str]) -> List[Dict[str, str]]:
        """Find dates in our dataset that are similar to missing dates (off by 1 day, etc.)."""
        similar_dates = []
        
        for missing_date in missing_dates[:5]:  # Only check first 5 to avoid performance issues
            try:
                from datetime import datetime, timedelta
                missing_dt = datetime.strptime(missing_date, '%Y-%m-%d').date()
                
                # Check dates within ±3 days
                for delta in [-3, -2, -1, 1, 2, 3]:
                    check_date = missing_dt + timedelta(days=delta)
                    check_date_str = check_date.strftime('%Y-%m-%d')
                    
                    if check_date_str in self.show_files:
                        similar_dates.append({
                            'missing': missing_date,
                            'found': check_date_str,
                            'shows': [show['show_id'] for show in self.show_files[check_date_str]]
                        })
                        break  # Only find first similar date
                        
            except ValueError:
                continue
        
        return similar_dates
    
    def _generate_failure_report(self, failed_collections: List[Dict[str, Any]]):
        """Generate detailed failure report for troubleshooting."""
        
        failure_report = {
            'generation_timestamp': datetime.now().isoformat(),
            'total_failed_collections': len(failed_collections),
            'failure_summary': {},
            'failed_collections': failed_collections,
            'recommendations': []
        }
        
        # Summarize failure types
        failure_types = {}
        for failure in failed_collections:
            failure_type = failure['failure_type']
            if failure_type not in failure_types:
                failure_types[failure_type] = 0
            failure_types[failure_type] += 1
        
        failure_report['failure_summary'] = failure_types
        
        # Add general recommendations
        failure_report['recommendations'] = [
            "Check stage00-created-data/dead_collections.json for data quality issues",
            "Verify date formats are YYYY-MM-DD in collection selectors",
            "Some official release collections may reference shows not in jerrygarcia.com dataset",
            "Consider adding missing shows to jerrygarcia dataset or updating collection definitions",
            "Exclusion-only collections (like 'absence' collections) may legitimately match 0 shows"
        ]
        
        # Save failure report
        failures_file = self.output_dir / "collection_failures.json"
        with open(failures_file, 'w', encoding='utf-8') as f:
            json.dump(failure_report, f, indent=2, ensure_ascii=False)
        
        self.logger.info(f"📋 Generated failure report: {failures_file}")
        
        # Log summary
        if failure_types:
            self.logger.info("📊 Failure breakdown:")
            for failure_type, count in failure_types.items():
                self.logger.info(f"   • {failure_type}: {count} collections")


def main():
    """Main execution function."""
    parser = argparse.ArgumentParser(description='Process Grateful Dead collections')
    parser.add_argument('--verbose', action='store_true', help='Enable verbose output')
    parser.add_argument('--collections-file', default='stage00-created-data/dead_collections.json',
                        help='Path to collections definition file')
    parser.add_argument('--shows-dir', default='stage02-generated-data/shows',
                        help='Directory containing show JSON files')
    parser.add_argument('--output-dir', default='stage02-generated-data',
                        help='Output directory for generated files')
    
    args = parser.parse_args()
    
    # Initialize processor
    processor = CollectionProcessor(
        collections_file=args.collections_file,
        shows_dir=args.shows_dir,
        output_dir=args.output_dir
    )
    
    # Set logging level
    if args.verbose:
        processor.logger.setLevel(logging.DEBUG)
    
    # Process pipeline
    try:
        processor.logger.info("🚀 Starting collections processing...")
        
        # Load collections data
        if not processor.load_collections_data():
            return 1
        
        # Index show files
        if not processor.index_show_files():
            return 1
        
        # Process collections
        if not processor.process_collections():
            return 1
        
        processor.logger.info("✅ Collections processing completed successfully!")
        return 0
        
    except Exception as e:
        processor.logger.error(f"❌ Collection processing failed: {e}")
        return 1


if __name__ == "__main__":
    exit(main())