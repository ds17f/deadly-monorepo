#!/usr/bin/env python3
"""
Must-Listen Sequence Extractor

Extracts complete musical sequences from setlist data based on highlighted songs.
Takes song highlights as anchor points and follows segue chains to capture complete
musical sequences that should be listened to as units.

Usage:
    python scripts/extract_sequences.py "stage02-generated-data/shows/gd1973-02-22*"
    python scripts/extract_sequences.py "stage02-generated-data/shows/gd1982-*" --dry-run
"""

import json
import argparse
import glob
from pathlib import Path
from typing import List, Dict, Any, Set, Tuple


def flatten_setlist(setlist: List[Dict]) -> List[Dict]:
    """Convert nested setlist structure to flat list of songs with positions."""
    flat_songs = []
    for set_data in setlist:
        if isinstance(set_data, dict) and 'songs' in set_data:
            # Nested structure: each set has songs
            songs = set_data.get('songs', [])
            for song in songs:
                flat_songs.append(song)
        else:
            # Already flat structure
            flat_songs.append(set_data)
    return flat_songs


def find_song_positions(song_name: str, flat_setlist: List[Dict]) -> List[int]:
    """Find all positions where a song appears in the flattened setlist."""
    positions = []
    for i, song in enumerate(flat_setlist):
        if song.get('name', '').strip() == song_name.strip():
            positions.append(i)
    return positions


def extract_sequence_from_position(position: int, flat_setlist: List[Dict]) -> List[str]:
    """Extract complete sequence starting from a given position, following segues both ways."""
    if position < 0 or position >= len(flat_setlist):
        return []
    
    # Find the start of the sequence (follow backward)
    start_pos = position
    while start_pos > 0:
        prev_song = flat_setlist[start_pos - 1]
        if prev_song.get('segue_into_next', False):
            start_pos -= 1
        else:
            break
    
    # Find the end of the sequence (follow forward) 
    end_pos = position
    while end_pos < len(flat_setlist) - 1:
        current_song = flat_setlist[end_pos]
        if current_song.get('segue_into_next', False):
            end_pos += 1
        else:
            break
    
    # Extract the sequence
    sequence = []
    for i in range(start_pos, end_pos + 1):
        song_name = flat_setlist[i].get('name', '').strip()
        if song_name:
            sequence.append(song_name)
    
    return sequence


def extract_must_listen_sequences(song_highlights: List[str], setlist: List[Dict]) -> List[List[str]]:
    """
    Extract must-listen sequences from song highlights and setlist data.
    
    Args:
        song_highlights: List of highlighted song names from LLM
        setlist: List of set objects or flat song objects
        
    Returns:
        List of sequences, where each sequence is a list of consecutive song names
    """
    if not song_highlights or not setlist:
        return []
    
    # Flatten the setlist to handle nested structure
    flat_setlist = flatten_setlist(setlist)
    
    if not flat_setlist:
        return []
    
    sequences = []
    processed_positions = set()
    
    # Process each highlighted song
    for highlighted_song in song_highlights:
        # Find all positions of this song in the setlist
        positions = find_song_positions(highlighted_song, flat_setlist)
        
        for position in positions:
            # Skip if we've already processed this position as part of another sequence
            if position in processed_positions:
                continue
            
            # Extract the complete sequence containing this song
            sequence = extract_sequence_from_position(position, flat_setlist)
            
            if sequence:
                sequences.append(sequence)
                
                # Mark all positions in this sequence as processed
                for i, seq_song in enumerate(sequence):
                    # Find the actual positions of these songs to mark as processed
                    seq_positions = find_song_positions(seq_song, flat_setlist)
                    for seq_pos in seq_positions:
                        # Only mark positions that are within the sequence range
                        if seq_pos >= position - len(sequence) and seq_pos <= position + len(sequence):
                            processed_positions.add(seq_pos)
    
    # Remove duplicate sequences (same songs in same order)
    unique_sequences = []
    seen_sequences = set()
    
    for seq in sequences:
        seq_tuple = tuple(seq)
        if seq_tuple not in seen_sequences:
            seen_sequences.add(seq_tuple)
            unique_sequences.append(seq)
    
    return unique_sequences


def process_show_file(show_path: Path, dry_run: bool = False) -> Dict[str, Any]:
    """Process a single show file to extract must-listen sequences."""
    try:
        with open(show_path, 'r') as f:
            show_data = json.load(f)
        
        # Extract data we need
        song_highlights = show_data.get('ai_show_review', {}).get('song_highlights', [])
        setlist = show_data.get('setlist', [])
        
        if not song_highlights:
            return {
                'status': 'skipped',
                'reason': 'No song highlights found',
                'sequences': []
            }
        
        if not setlist:
            return {
                'status': 'skipped', 
                'reason': 'No setlist data found',
                'sequences': []
            }
        
        # Extract sequences
        sequences = extract_must_listen_sequences(song_highlights, setlist)
        
        # Update the show data
        if not dry_run and sequences:
            show_data['ai_show_review']['must_listen_sequences'] = sequences
            
            # Save the updated file
            with open(show_path, 'w') as f:
                json.dump(show_data, f, indent=2, ensure_ascii=False)
        
        return {
            'status': 'processed',
            'song_highlights': song_highlights,
            'sequences': sequences,
            'sequences_count': len(sequences)
        }
        
    except Exception as e:
        return {
            'status': 'error',
            'error': str(e),
            'sequences': []
        }


def main():
    parser = argparse.ArgumentParser(description='Extract must-listen sequences from show reviews')
    parser.add_argument('pattern', help='Glob pattern for show files (e.g., "stage02-generated-data/shows/gd1973*")')
    parser.add_argument('--dry-run', action='store_true', help='Show results without modifying files')
    parser.add_argument('--verbose', action='store_true', help='Show detailed output')
    
    args = parser.parse_args()
    
    # Find matching files
    show_files = glob.glob(args.pattern)
    if not show_files:
        print(f"No files found matching pattern: {args.pattern}")
        return
    
    show_files.sort()
    print(f"Found {len(show_files)} show files to process")
    
    if args.dry_run:
        print("DRY RUN - no files will be modified")
    print()
    
    # Process each file
    processed_count = 0
    skipped_count = 0
    error_count = 0
    total_sequences = 0
    
    for show_file in show_files:
        show_path = Path(show_file)
        show_name = show_path.stem
        
        result = process_show_file(show_path, args.dry_run)
        
        if result['status'] == 'processed':
            processed_count += 1
            sequences_count = result['sequences_count']
            total_sequences += sequences_count
            
            if args.verbose or sequences_count > 0:
                print(f"✅ {show_name}: {sequences_count} sequences")
                
                if args.verbose and result['sequences']:
                    print(f"   Song highlights: {result['song_highlights']}")
                    for i, seq in enumerate(result['sequences'], 1):
                        if len(seq) == 1:
                            print(f"   Sequence {i}: {seq[0]}")
                        else:
                            print(f"   Sequence {i}: {' > '.join(seq)}")
                    print()
                    
        elif result['status'] == 'skipped':
            skipped_count += 1
            if args.verbose:
                print(f"⏭️  {show_name}: {result['reason']}")
                
        else:  # error
            error_count += 1
            print(f"❌ {show_name}: {result['error']}")
    
    # Summary
    print(f"\nSummary:")
    print(f"  Processed: {processed_count} shows")
    print(f"  Skipped: {skipped_count} shows")
    print(f"  Errors: {error_count} shows")
    print(f"  Total sequences extracted: {total_sequences}")
    
    if total_sequences > 0:
        avg_sequences = total_sequences / max(processed_count, 1)
        print(f"  Average sequences per show: {avg_sequences:.1f}")


if __name__ == '__main__':
    main()