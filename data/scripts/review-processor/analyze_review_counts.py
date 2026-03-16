#!/usr/bin/env python3
"""
Review Count Analysis Script

Analyzes the distribution of review counts across shows to determine
natural thresholds for rating boosts and must-listen criteria.

Usage:
    python scripts/review-processor/analyze_review_counts.py
    python scripts/review-processor/analyze_review_counts.py --verbose
"""

import json
import argparse
from pathlib import Path
from collections import defaultdict
import statistics


def load_show_data(show_path):
    """Load show data and extract review count information."""
    try:
        with open(show_path) as f:
            show_data = json.load(f)
        
        show_id = show_data.get('show_id', show_path.stem)
        recordings = show_data.get('recordings', [])
        
        if not recordings:
            return None
            
        # Sum review counts across all recordings for this show
        total_reviews = 0
        recording_details = []
        
        for recording_id in recordings:
            # Look for the recording metadata file
            recording_path = Path(f"stage01-collected-data/archive/{recording_id}.json")
            
            if recording_path.exists():
                try:
                    with open(recording_path) as f:
                        recording_data = json.load(f)
                    
                    raw_reviews = recording_data.get('raw_reviews', [])
                    review_count = len(raw_reviews)
                    total_reviews += review_count
                    
                    recording_details.append({
                        'recording_id': recording_id,
                        'review_count': review_count
                    })
                except Exception as e:
                    continue
        
        return {
            'show_id': show_id,
            'date': show_data.get('date', 'Unknown'),
            'venue': show_data.get('venue', 'Unknown'),
            'total_reviews': total_reviews,
            'total_recordings': len(recordings),
            'ai_rating': show_data.get('ai_show_review', {}).get('ratings', {}).get('ai_rating'),
            'recording_details': recording_details
        }
        
    except Exception as e:
        return None


def analyze_review_distribution(show_data):
    """Analyze the distribution of review counts."""
    review_counts = [show['total_reviews'] for show in show_data if show['total_reviews'] > 0]
    
    if not review_counts:
        return {}
    
    return {
        'total_shows': len(review_counts),
        'min_reviews': min(review_counts),
        'max_reviews': max(review_counts),
        'mean_reviews': statistics.mean(review_counts),
        'median_reviews': statistics.median(review_counts),
        'percentiles': {
            '10th': sorted(review_counts)[int(0.1 * len(review_counts))],
            '25th': sorted(review_counts)[int(0.25 * len(review_counts))],
            '50th': sorted(review_counts)[int(0.5 * len(review_counts))],
            '75th': sorted(review_counts)[int(0.75 * len(review_counts))],
            '90th': sorted(review_counts)[int(0.9 * len(review_counts))],
            '95th': sorted(review_counts)[int(0.95 * len(review_counts))],
            '99th': sorted(review_counts)[int(0.99 * len(review_counts))]
        }
    }


def create_percentile_based_tiers(percentiles):
    """Create rating boost tiers based on actual percentiles."""
    # Use key percentiles to create 5 tiers for 0.1 point increments
    return {
        'tier_1': {
            'range': f"5-{percentiles['25th']} reviews",
            'boost': 0.1,
            'description': "Above minimal threshold"
        },
        'tier_2': {
            'range': f"{percentiles['25th']+1}-{percentiles['50th']} reviews", 
            'boost': 0.2,
            'description': "Standard attention"
        },
        'tier_3': {
            'range': f"{percentiles['50th']+1}-{percentiles['75th']} reviews",
            'boost': 0.3, 
            'description': "Notable attention"
        },
        'tier_4': {
            'range': f"{percentiles['75th']+1}-{percentiles['95th']} reviews",
            'boost': 0.4,
            'description': "High attention"
        },
        'tier_5': {
            'range': f"{percentiles['95th']+1}+ reviews",
            'boost': 0.5,
            'description': "Exceptional attention"
        }
    }


def create_analysis_ranges(percentiles):
    """Create analysis ranges based on percentiles."""
    return [
        (0, 5, "Minimal (below threshold)"),
        (5, percentiles['25th'], "Limited attention"),
        (percentiles['25th'], percentiles['50th'], "Standard attention"), 
        (percentiles['50th'], percentiles['75th'], "Notable attention"),
        (percentiles['75th'], percentiles['99th'], "High attention"),
        (percentiles['99th'], float('inf'), "Exceptional attention")
    ]


def find_high_review_shows(show_data, min_reviews):
    """Find shows with high review counts for manual inspection."""
    high_review_shows = [
        show for show in show_data 
        if show['total_reviews'] >= min_reviews
    ]
    
    # Sort by review count descending
    high_review_shows.sort(key=lambda x: x['total_reviews'], reverse=True)
    
    return high_review_shows


def main():
    parser = argparse.ArgumentParser(description='Analyze review count distribution across shows')
    parser.add_argument('--verbose', action='store_true', help='Show detailed output')
    parser.add_argument('--output', default='scripts/review-processor/review_count_analysis.json',
                       help='Output file for analysis results')
    
    args = parser.parse_args()
    
    print("🔍 Analyzing review counts across all shows...")
    
    # Find all show files
    show_files = list(Path("stage02-generated-data/shows/").glob("*.json"))
    print(f"   Found {len(show_files)} show files")
    
    # Process all shows
    show_data = []
    processed = 0
    skipped = 0
    
    for show_file in show_files:
        if processed % 100 == 0 and processed > 0:
            print(f"   Processed {processed} shows...")
            
        show_info = load_show_data(show_file)
        if show_info:
            show_data.append(show_info)
            processed += 1
        else:
            skipped += 1
    
    print(f"✅ Processed {processed} shows, skipped {skipped}")
    print()
    
    # Analyze distribution
    distribution = analyze_review_distribution(show_data)
    
    print("📊 Review Count Distribution:")
    print(f"   Total shows with reviews: {distribution['total_shows']}")
    print(f"   Range: {distribution['min_reviews']} - {distribution['max_reviews']} reviews")
    print(f"   Mean: {distribution['mean_reviews']:.1f} reviews")
    print(f"   Median: {distribution['median_reviews']:.1f} reviews")
    print()
    
    print("📈 Percentiles:")
    for percentile, count in distribution['percentiles'].items():
        print(f"   {percentile}: {count} reviews")
    print()
    
    # Create percentile-based tiers
    tiers = create_percentile_based_tiers(distribution['percentiles'])
    print("⭐ Suggested Rating Boost Tiers (based on percentiles):")
    for tier_name, tier_info in tiers.items():
        print(f"   {tier_info['range']}: +{tier_info['boost']} stars ({tier_info['description']})")
    print()
    
    # Find exceptional shows (95th percentile and above)
    exceptional_threshold = distribution['percentiles']['95th']
    high_review_shows = find_high_review_shows(show_data, exceptional_threshold)
    print(f"🔥 Exceptional attention shows ({exceptional_threshold}+ reviews):")
    for show in high_review_shows[:15]:  # Top 15
        ai_rating = show['ai_rating'] or 'N/A'
        print(f"   {show['date']} - {show['venue']}: {show['total_reviews']} reviews (AI: {ai_rating})")
    
    if len(high_review_shows) > 15:
        print(f"   ... and {len(high_review_shows) - 15} more exceptional shows")
    print()
    
    # Show breakdown by percentile-based ranges  
    ranges = create_analysis_ranges(distribution['percentiles'])
    
    print("📋 Show counts by percentile-based ranges:")
    for min_rev, max_rev, label in ranges:
        if max_rev == float('inf'):
            count = len([s for s in show_data if s['total_reviews'] >= min_rev])
            print(f"   {label} ({min_rev}+): {count} shows")
        else:
            count = len([s for s in show_data if min_rev <= s['total_reviews'] < max_rev])
            print(f"   {label} ({min_rev}-{max_rev-1}): {count} shows")
    
    # Prepare analysis results
    analysis_results = {
        'distribution': distribution,
        'rating_boost_tiers': tiers,
        'exceptional_shows': high_review_shows,
        'percentile_ranges': ranges,
        'analysis_date': '2025-09-11',
        'total_shows_analyzed': len(show_data)
    }
    
    # Always save results to output file
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(output_path, 'w') as f:
        json.dump(analysis_results, f, indent=2)
    print(f"💾 Analysis results saved to {output_path}")


if __name__ == '__main__':
    main()
