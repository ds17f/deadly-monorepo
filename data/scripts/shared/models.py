#!/usr/bin/env python3
"""
Shared Data Models for Dead Archive Pipeline

This module contains all dataclasses used across the collection and generation
stages of the Dead Archive metadata pipeline. These models ensure consistency
and type safety across all pipeline components.

Used by:
- scripts/01-collect-data/collect_archive_metadata.py
- scripts/02-generate-data/generate_archive_products.py
"""

from dataclasses import dataclass, asdict
from typing import Dict, List, Optional, Any


@dataclass
class ReviewData:
    """Individual review data from Archive.org"""
    stars: float
    review_text: str
    date: str


@dataclass
class RecordingMetadata:
    """Raw metadata for a single recording from Archive.org"""
    identifier: str
    raw_metadata: Dict[str, Any]              # Complete metadata from Archive.org API
    raw_reviews: List[Dict[str, Any]]         # Raw review data from Archive.org API
    files: List[Dict[str, Any]]               # Complete files array from Archive.org
    normalized_date: str                      # Computed YYYY-MM-DD for matching
    collection_timestamp: str
    
    # Convenience properties for commonly accessed fields
    @property
    def title(self) -> str:
        return self.raw_metadata.get('title', '')
    
    @property
    def date(self) -> str:
        return self.raw_metadata.get('date', '')
    
    @property
    def venue(self) -> str:
        return self.raw_metadata.get('venue', '')
    
    @property
    def location(self) -> str:
        return self.raw_metadata.get('coverage', '')
    
    @property
    def description(self) -> str:
        return self.raw_metadata.get('description', '')
    
    @property
    def lineage(self) -> str:
        return self.raw_metadata.get('lineage', '')
    
    @property
    def taper(self) -> str:
        return self.raw_metadata.get('taper', '')
    
    @property
    def source(self) -> str:
        return self.raw_metadata.get('source', '')
    
    @property
    def runtime(self) -> str:
        return self.raw_metadata.get('runtime', '')


@dataclass 
class ProcessedRecordingMetadata:
    """Minimal processed recording metadata for final output"""
    identifier: str
    title: str
    date: str
    venue: str
    location: str
    source_type: str
    lineage: str
    taper: str
    source: str
    runtime: str
    rating: float
    review_count: int
    confidence: float
    raw_rating: float
    high_ratings: int
    low_ratings: int


@dataclass
class ShowMetadata:
    """Aggregated metadata for an entire show"""
    show_key: str
    date: str
    venue: str
    location: str
    recordings: List[str]  # List of recording identifiers
    best_recording: str
    avg_rating: float
    confidence: float
    recording_count: int
    collection_timestamp: str


@dataclass
class ProgressState:
    """Collection progress tracking"""
    collection_started: str
    last_updated: str
    status: str
    total_recordings: int
    processed_recordings: int
    failed_recordings: int
    current_batch: int
    last_processed: str
    failed_identifiers: List[str]
    performance_stats: Dict[str, Any]


def recording_to_dict(recording: RecordingMetadata) -> Dict[str, Any]:
    """Convert RecordingMetadata to dictionary, handling nested objects"""
    return asdict(recording)


def show_to_dict(show: ShowMetadata) -> Dict[str, Any]:
    """Convert ShowMetadata to dictionary"""
    return asdict(show)


def progress_to_dict(progress: ProgressState) -> Dict[str, Any]:
    """Convert ProgressState to dictionary"""
    return asdict(progress)


def processed_recording_to_dict(recording: ProcessedRecordingMetadata) -> Dict[str, Any]:
    """Convert ProcessedRecordingMetadata to dictionary"""
    return asdict(recording)