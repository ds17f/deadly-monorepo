#!/usr/bin/env python3
"""
Update cached recording ratings to include raw rating and distribution data.
This processes existing cached files to avoid needing API calls.
"""

import json
import os
from pathlib import Path
from collections import Counter
from typing import Dict, Any


def calculate_enhanced_rating(reviews):
    """Calculate enhanced rating data from reviews."""
    if not reviews:
        return None
    
    # Filter valid reviews
    valid_reviews = [r for r in reviews if r.get('stars', 0) >= 1.0]
    if not valid_reviews:
        return None
    
    # Raw rating (simple average)
    raw_rating = sum(r['stars'] for r in valid_reviews) / len(valid_reviews)
    
    # Distribution
    distribution = Counter(int(r['stars']) for r in valid_reviews)
    distribution_dict = {k: v for k, v in distribution.items() if v > 0}
    
    # High and low ratings
    high_ratings = sum(1 for r in valid_reviews if r['stars'] >= 4.0)
    low_ratings = sum(1 for r in valid_reviews if r['stars'] <= 2.0)
    
    return {
        'raw_rating': raw_rating,
        'distribution': distribution_dict,
        'high_ratings': high_ratings,
        'low_ratings': low_ratings
    }


def process_cached_recordings():
    """Process all cached recording files."""
    recordings_dir = Path('stage01-collected-data/archive')
    if not recordings_dir.exists():
        print("No cached recordings found")
        return
    
    updated_count = 0
    total_count = 0
    
    for json_file in recordings_dir.glob('*.json'):
        total_count += 1
        
        try:
            with open(json_file, 'r') as f:
                content = f.read().strip()
                if not content:
                    print(f"Skipping empty file: {json_file}")
                    continue
                data = json.loads(content)
            
            reviews = data.get('reviews', [])
            if not reviews:
                continue
            
            enhanced = calculate_enhanced_rating(reviews)
            if enhanced:
                # Update the data
                data.update(enhanced)
                
                # Write back
                with open(json_file, 'w') as f:
                    json.dump(data, f, indent=2)
                
                updated_count += 1
                
                if updated_count % 100 == 0:
                    print(f"Updated {updated_count}/{total_count} files...")
                    
        except Exception as e:
            print(f"Error processing {json_file}: {e}")
    
    print(f"Completed: Updated {updated_count} out of {total_count} files")


def create_enhanced_ratings_json():
    """Create enhanced ratings.json from cached files."""
    recordings_dir = Path('stage01-collected-data/archive')
    recording_ratings = {}
    recording_metadata = {}
    
    # First pass: collect recording ratings and metadata
    for json_file in recordings_dir.glob('*.json'):
        try:
            with open(json_file, 'r') as f:
                content = f.read().strip()
                if not content:
                    print(f"Skipping empty file for ratings: {json_file}")
                    continue
                data = json.loads(content)
            
            identifier = data.get('identifier', json_file.stem)
            
            # Store metadata for show aggregation
            recording_metadata[identifier] = {
                'date': data.get('date', ''),
                'venue': data.get('venue', ''),
                'location': data.get('location', ''),
                'title': data.get('title', ''),
                'source_type': data.get('source_type', 'UNKNOWN')
            }
            
            # Create enhanced rating entry
            recording_ratings[identifier] = {
                'rating': data.get('rating', 0.0),                    # Original weighted rating
                'raw_rating': data.get('raw_rating', 0.0),            # NEW: Simple average
                'review_count': data.get('review_count', 0),
                'source_type': recording_metadata[identifier]['source_type'],
                'confidence': data.get('confidence', 0.0),
                'distribution': data.get('distribution', {}),         # NEW: Distribution
                'high_ratings': data.get('high_ratings', 0),          # NEW: High ratings
                'low_ratings': data.get('low_ratings', 0)             # NEW: Low ratings
            }
            
        except Exception as e:
            print(f"Error processing {json_file} for ratings: {e}")
    
    # Second pass: aggregate show-level ratings
    shows_data = {}
    for identifier, metadata in recording_metadata.items():
        if not metadata['date']:
            continue
            
        # Create show key from date and venue
        date = metadata['date']
        venue = metadata['venue'] or 'Unknown Venue'
        show_key = f"{date}_{venue.replace(' ', '_')}"
        
        if show_key not in shows_data:
            shows_data[show_key] = {
                'date': date,
                'venue': venue,
                'location': metadata.get('location', ''),
                'recordings': []
            }
        
        shows_data[show_key]['recordings'].append(identifier)
    
    # Compute show ratings
    show_ratings = {}
    for show_key, show_data in shows_data.items():
        try:
            show_recordings = show_data['recordings']
            rated_recordings = []
            
            # Get ratings for this show's recordings
            for rec_id in show_recordings:
                if rec_id in recording_ratings:
                    rating_data = recording_ratings[rec_id]
                    if rating_data['raw_rating'] > 0 and rating_data['review_count'] > 0:
                        rated_recordings.append(rating_data)
            
            if not rated_recordings:
                continue
                
            # Compute show-level aggregated rating
            total_weighted_rating = sum(r['rating'] * r['review_count'] for r in rated_recordings)
            total_raw_rating = sum(r['raw_rating'] * r['review_count'] for r in rated_recordings)
            total_reviews = sum(r['review_count'] for r in rated_recordings)
            total_high_ratings = sum(r['high_ratings'] for r in rated_recordings)
            total_low_ratings = sum(r['low_ratings'] for r in rated_recordings)
            
            if total_reviews == 0:
                continue
                
            show_avg_rating = total_weighted_rating / total_reviews
            show_raw_rating = total_raw_rating / total_reviews
            
            # Find best recording (highest raw rating with most reviews)
            best_recording = max(rated_recordings, 
                               key=lambda r: (r['raw_rating'], r['review_count']))
            best_recording_id = next(rec_id for rec_id, rating in recording_ratings.items() 
                                   if rating == best_recording)
            
            # Confidence based on total reviews and recording count
            confidence = min(1.0, (total_reviews / 10.0) * (len(rated_recordings) / len(show_recordings)))
            
            show_ratings[show_key] = {
                'date': show_data['date'],
                'venue': show_data['venue'],
                'rating': round(show_avg_rating, 2),           # Weighted rating for ranking
                'raw_rating': round(show_raw_rating, 2),       # Simple average for display
                'confidence': round(confidence, 2),
                'best_recording': best_recording_id,
                'recording_count': len(show_recordings),
                'total_high_ratings': total_high_ratings,      # NEW: Total 4-5★ reviews
                'total_low_ratings': total_low_ratings         # NEW: Total 1-2★ reviews
            }
            
        except Exception as e:
            print(f"Error computing show rating for {show_key}: {e}")
            continue
    
    print(f"Generated {len(show_ratings)} show ratings from {len(recording_ratings)} recording ratings")
    
    # Save enhanced ratings
    ratings_data = {
        'metadata': {
            'generated_at': '2025-07-12T10:00:00Z',
            'version': '2.0.0',
            'total_recordings': len(recording_ratings),
            'total_shows': len(show_ratings),
            'enhancement': 'Added raw_rating, distribution, high_ratings, low_ratings and show aggregation'
        },
        'recording_ratings': recording_ratings,
        'show_ratings': show_ratings
    }
    
    output_path = Path('stage02-processed-data/ratings_enhanced.json')
    with open(output_path, 'w') as f:
        json.dump(ratings_data, f, indent=2)
    
    print(f"Created enhanced ratings: {output_path}")
    print(f"Total recordings: {len(recording_ratings)}")


if __name__ == '__main__':
    print("Processing cached recordings...")
    process_cached_recordings()
    
    print("Creating enhanced ratings.json...")  
    create_enhanced_ratings_json()
    
    print("Done!")