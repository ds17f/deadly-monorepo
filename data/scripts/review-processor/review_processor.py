#!/usr/bin/env python3
"""
Grateful Dead Review Processor

LLM-powered system for analyzing Archive.org reviews and generating comprehensive
show summaries and ratings. Processes raw user reviews through AI analysis to 
create insightful, natural-sounding show reviews.

Usage:
    python review_processor.py "stage02/shows/gd1977-*"
    python review_processor.py "stage02/shows/gd1993-05-16*" --clobber
    python review_processor.py "stage02/shows/*" --provider anthropic
"""

import json
import logging
import argparse
import glob
from pathlib import Path
from typing import Dict, List, Any, Optional, Tuple
from dataclasses import dataclass, asdict
from datetime import datetime, timezone
import time
import sys

# Import sequence extraction functionality
from sequence_extractor import extract_must_listen_sequences

# Third-party imports (will be available after pip install)
try:
    import requests
    from rich.console import Console
    from rich.progress import Progress, TaskID
    from rich.logging import RichHandler
    import click
except ImportError as e:
    print(f"Missing required dependency: {e}")
    print("Please run: pip install -r requirements.txt")
    sys.exit(1)

# Configure rich console
console = Console()

# Logging will be configured in setup_logging() function
logger = logging.getLogger(__name__)

def setup_logging(verbose: bool = False, log_file: Optional[str] = None) -> None:
    """Set up comprehensive logging with clean console and detailed file output."""
    # Clear any existing handlers
    for handler in logger.handlers[:]:
        logger.removeHandler(handler)
    for handler in logging.root.handlers[:]:
        logging.root.removeHandler(handler)
    
    # Create formatters
    file_formatter = logging.Formatter(
        '%(asctime)s - %(name)s - %(levelname)s - %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S'
    )
    
    # File handler with rotation (always detailed)
    if log_file:
        from logging.handlers import RotatingFileHandler
        file_handler = RotatingFileHandler(
            log_file,
            maxBytes=10 * 1024 * 1024,  # 10MB
            backupCount=5
        )
        file_handler.setLevel(logging.DEBUG)  # Always debug level for files
        file_handler.setFormatter(file_formatter)
        logging.root.addHandler(file_handler)
    
    # Define console filter class that both modes can use
    class CleanConsoleFilter(logging.Filter):
        def filter(self, record):
            # Skip noisy debug messages
            if record.levelname == 'DEBUG':
                return False
            # Skip API call details (these go to file)
            message = record.getMessage()
            if any(skip_phrase in message for skip_phrase in [
                'LLM API call',
                'Rate limiting',
                'API call failed',
                'HTTPConnectionPool',
                'Read timed out',
                'Response received in',
                'Attempt', 
                'retrying in'
            ]):
                return False
            return True

    # Console handler - much more restrictive, clean output only
    if verbose:
        # In verbose mode, show more but still clean
        console_handler = RichHandler(
            console=console, 
            rich_tracebacks=True,
            show_path=False,
            markup=True
        )
        console_handler.setLevel(logging.INFO)
        console_handler.addFilter(CleanConsoleFilter())
    else:
        # Normal mode: show INFO and above with same filtering
        console_handler = RichHandler(
            console=console, 
            rich_tracebacks=False,
            show_path=False,
            markup=True
        )
        console_handler.setLevel(logging.INFO)
        # Apply same clean filter for non-verbose mode
        console_handler.addFilter(CleanConsoleFilter())
    
    console_formatter = logging.Formatter('%(message)s')
    console_handler.setFormatter(console_formatter)
    
    # Set up root logger
    logging.root.setLevel(logging.DEBUG)
    logging.root.addHandler(console_handler)
    
    # Set up our logger
    logger.setLevel(logging.DEBUG)


@dataclass
class ProviderConfig:
    """Configuration for a specific LLM provider."""
    endpoint: str
    api_key: str
    model: str
    rate_limiting: Dict[str, Any]
    timeout: int = 60
    max_tokens: int = 4096
    custom_headers: Optional[Dict[str, str]] = None
    organization: Optional[str] = None


@dataclass 
class ProcessingConfig:
    """General processing configuration."""
    cache_responses: bool = True
    skip_processed: bool = True
    min_reviews_for_processing: int = 1
    output_debug: bool = False
    batch_size: int = 1


@dataclass
class Config:
    """Complete application configuration."""
    providers: Dict[str, ProviderConfig]
    default_provider: str
    processing: ProcessingConfig

    @classmethod
    def load(cls, config_path: Path) -> 'Config':
        """Load configuration from JSON file."""
        try:
            with open(config_path) as f:
                data = json.load(f)
            
            # Convert provider dicts to ProviderConfig objects
            providers = {}
            for name, provider_data in data['providers'].items():
                providers[name] = ProviderConfig(**provider_data)
            
            processing = ProcessingConfig(**data['processing'])
            
            return cls(
                providers=providers,
                default_provider=data['default_provider'],
                processing=processing
            )
        except Exception as e:
            logger.error(f"Failed to load config from {config_path}: {e}")
            raise


class LLMClient:
    """Generic client for OpenAI-compatible LLM APIs."""
    
    def __init__(self, provider_config: ProviderConfig):
        self.config = provider_config
        self.session = requests.Session()
        self._last_request_time = None
        
        # Set up headers
        headers = {
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {provider_config.api_key}'
        }
        
        if provider_config.custom_headers:
            headers.update(provider_config.custom_headers)
            
        if provider_config.organization:
            headers['OpenAI-Organization'] = provider_config.organization
            
        self.session.headers.update(headers)
        
        # API call tracking
        self.api_calls = 0
        self.api_failures = 0
    
    def generate_response(self, system_prompt: str, user_prompt: str) -> Dict[str, Any]:
        """Generate a response from the LLM."""
        self.api_calls += 1
        
        payload = {
            'model': self.config.model,
            'messages': [
                {'role': 'system', 'content': system_prompt},
                {'role': 'user', 'content': user_prompt}
            ],
            'max_tokens': self.config.max_tokens,
            'temperature': 0.7
        }
        
        logger.debug(f"🤖 LLM API call #{self.api_calls} - Model: {self.config.model}")
        logger.debug(f"   User prompt length: {len(user_prompt)} chars")
        
        # Add rate limiting delay
        if self._last_request_time:
            elapsed = time.time() - self._last_request_time
            delay = self.config.rate_limiting.get('delay_seconds', 1.0)
            if elapsed < delay:
                sleep_time = delay - elapsed
                logger.debug(f"   Rate limiting: sleeping {sleep_time:.2f}s")
                time.sleep(sleep_time)
        
        max_retries = self.config.rate_limiting.get('max_retries', 3)
        backoff_factor = self.config.rate_limiting.get('backoff_factor', 2.0)
        
        for attempt in range(max_retries):
            try:
                start_time = time.time()
                self._last_request_time = start_time
                
                logger.debug(f"   Attempt {attempt + 1}/{max_retries} - Sending request...")
                
                response = self.session.post(
                    f"{self.config.endpoint}/chat/completions",
                    json=payload,
                    timeout=self.config.timeout
                )
                response.raise_for_status()
                
                result = response.json()
                response_time = time.time() - start_time
                
                if 'choices' in result and len(result['choices']) > 0:
                    content = result['choices'][0]['message']['content']
                    usage = result.get('usage', {})
                    
                    logger.debug(f"   ✅ Response received in {response_time:.2f}s")
                    logger.debug(f"   Response length: {len(content)} chars")
                    if usage:
                        logger.debug(f"   Token usage: {usage}")
                    
                    return {
                        'content': content,
                        'usage': usage,
                        'model': result.get('model', self.config.model),
                        'response_time': response_time
                    }
                else:
                    raise ValueError("Invalid response format from LLM")
                    
            except Exception as e:
                self.api_failures += 1
                if attempt == max_retries - 1:
                    logger.error(f"❌ LLM API call failed after {max_retries} attempts: {e}")
                    logger.debug(f"   Full error details:", exc_info=True)
                    raise
                
                wait_time = self.config.rate_limiting.get('delay_seconds', 1.0) * (backoff_factor ** attempt)
                logger.warning(f"⚠️  API call failed (attempt {attempt + 1}), retrying in {wait_time:.1f}s: {e}")
                time.sleep(wait_time)


class ReviewProcessor:
    """Main processor for analyzing Grateful Dead show reviews."""
    
    def __init__(self, config: Config, provider_name: str = None):
        self.config = config
        self.provider_name = provider_name or config.default_provider
        
        if self.provider_name not in config.providers:
            raise ValueError(f"Provider '{self.provider_name}' not found in config")
            
        provider_config = config.providers[self.provider_name]
        self.llm_client = LLMClient(provider_config)
        
        # Load prompts
        self.prompts = self._load_prompts()
        
        # Statistics tracking
        self.stats = {
            'shows_processed': 0,
            'recordings_processed': 0,
            'shows_skipped': 0,
            'errors': 0,
            'start_time': datetime.now(timezone.utc),
            'failed_shows': [],
            'failed_recordings': [],
            'processing_times': {
                'total': 0.0,
                'recording_analysis': 0.0,
                'show_generation': 0.0
            }
        }
        
        # Log processing start
        logger.info(f"🤖 Review Processor initialized")
        logger.info(f"   Provider: {self.provider_name} ({provider_config.model})")
        logger.info(f"   Rate limiting: {provider_config.rate_limiting.get('delay_seconds', 1.0)}s delay")
        logger.debug(f"   Endpoint: {provider_config.endpoint}")
        logger.debug(f"   Prompts loaded: {list(self.prompts.keys())}")
    
    def _load_prompts(self) -> Dict[str, str]:
        """Load prompt templates from markdown files."""
        prompts_dir = Path(__file__).parent / 'prompts'
        prompts = {}
        
        for prompt_file in ['recording_analysis.md', 'show_review.md']:
            prompt_path = prompts_dir / prompt_file
            if prompt_path.exists():
                with open(prompt_path) as f:
                    # Use filename without extension as key
                    prompt_name = prompt_file.replace('.md', '')
                    prompts[prompt_name] = f.read()
            else:
                logger.warning(f"Prompt file not found: {prompt_path}")
                prompt_name = prompt_file.replace('.md', '')
                prompts[prompt_name] = ""
        
        return prompts
    
    def process_show_pattern(self, pattern: str, clobber: bool = False) -> None:
        """Process all shows matching the given pattern."""
        logger.info(f"🎯 Processing shows matching pattern: {pattern}")
        logger.debug(f"   Clobber mode: {clobber}")
        
        show_files = glob.glob(pattern)
        
        if not show_files:
            logger.error(f"❌ No show files found matching pattern: {pattern}")
            return
        
        logger.info(f"📋 Found {len(show_files)} show files to process")
        logger.debug(f"   Files: {[Path(f).name for f in sorted(show_files)]}")
        
        # Pre-scan to count total recordings for progress bar
        total_recordings = 0
        show_recording_counts = {}
        for show_file in show_files:
            try:
                with open(show_file) as f:
                    show_data = json.load(f)
                recordings = show_data.get('recordings', [])
                
                # Check if already processed and not clobbering
                if not clobber and show_data.get('ai_show_review', {}).get('processing_status') == 'completed':
                    show_recording_counts[show_file] = 0  # Skip entirely
                else:
                    show_recording_counts[show_file] = len(recordings)
                    total_recordings += len(recordings)
            except Exception:
                show_recording_counts[show_file] = 0
        
        logger.info(f"🎵 Total recordings to process: {total_recordings}")
        
        # Log batch processing start
        logger.info(f"🚀 Starting batch processing at {datetime.now(timezone.utc).isoformat()}")
        
        # Start enhanced console output
        console.print()  # Add blank line before processing output
        
        errors_this_session = []
        
        for i, show_file in enumerate(sorted(show_files), 1):
            show_name = Path(show_file).name
            show_recording_count = show_recording_counts[show_file]
            
            # Show-level progress display
            console.print(f"🎵 Processing Show [{i}/{len(show_files)}]: [bold blue]{show_name}[/bold blue]")
            
            try:
                # Skip already processed shows
                if show_recording_count == 0:
                    console.print(f"   ⏭️  Already processed, skipping...")
                    logger.debug(f"Skipping already processed show: {show_name}")
                    continue
                
                # Show recording count and estimate
                console.print(f"   📊 Found {show_recording_count} recordings to process")
                estimated_minutes = show_recording_count * 2  # Rough estimate: 2 minutes per recording
                console.print(f"   ⏳ Estimated: ~{estimated_minutes//60}h {estimated_minutes%60}m (varies by review count and LLM speed)")
                
                # Process the show with enhanced output
                self._process_single_show_enhanced(Path(show_file), clobber, i, len(show_files))
                
            except Exception as e:
                # Enhanced error display
                console.print(f"   ❌ [red]Show processing failed:[/red] {str(e)[:100]}...")
                logger.error(f"❌ Failed to process {show_name}: {str(e)[:100]}...")
                logger.debug(f"   Full error for {show_name}: {e}", exc_info=True)
                
                # Track error
                error_info = {
                    'show': show_name,
                    'error': str(e),
                    'timestamp': datetime.now(timezone.utc).isoformat()
                }
                self.stats['errors'] += 1
                self.stats['failed_shows'].append(error_info)
                errors_this_session.append(f"{show_name}: {str(e)[:80]}")
            
            console.print()  # Blank line between shows
            
        # Show error summary if there were failures
        if errors_this_session:
            console.print(f"\n[yellow]⚠️  {len(errors_this_session)} shows failed processing:[/yellow]")
            for error in errors_this_session[-5:]:  # Show last 5 errors
                console.print(f"   • {error}")
            if len(errors_this_session) > 5:
                console.print(f"   ... and {len(errors_this_session) - 5} more (see log file)")
            console.print(f"[dim]Full error details in log file[/dim]")
        
        logger.info(f"🏁 Batch processing complete at {datetime.now(timezone.utc).isoformat()}")
        self._print_summary()
    
    def _process_single_show_enhanced(self, show_path: Path, clobber: bool, show_index: int, total_shows: int) -> None:
        """Process a single show with enhanced console output."""
        show_name = show_path.name
        
        # Load show data
        try:
            with open(show_path) as f:
                show_data = json.load(f)
        except Exception as e:
            raise Exception(f"Failed to load show data: {e}")
        
        # Check if already processed
        if not clobber and show_data.get('ai_show_review', {}).get('processing_status') == 'completed':
            console.print(f"   ⏭️  Already processed, skipping...")
            self.stats['shows_skipped'] += 1
            return
        
        # Get recordings list
        recordings = show_data.get('recordings', [])
        if not recordings:
            raise Exception(f"No recordings found in show data")
        
        # Stage 1: Process individual recordings with enhanced output
        recording_analyses = []
        failed_recordings = []
        skipped_recordings = []
        
        for i, recording_id in enumerate(recordings, 1):
            try:
                # Show recording being processed
                console.print(f"   🎧 [{i}/{len(recordings)}] [cyan]{recording_id}[/cyan]")
                
                # Get review count info before processing
                archive_path = Path(f"stage01-collected-data/archive/{recording_id}.json")
                review_count = 0
                if archive_path.exists():
                    try:
                        with open(archive_path) as f:
                            archive_data = json.load(f)
                        review_count = len(archive_data.get('raw_reviews', []))
                    except:
                        pass
                
                if review_count == 0:
                    console.print(f"      📝 No reviews found, skipping...")
                    skipped_recordings.append(recording_id)
                    continue
                elif review_count < self.config.processing.min_reviews_for_processing:
                    console.print(f"      📝 Only {review_count} reviews (minimum {self.config.processing.min_reviews_for_processing}), skipping...")
                    skipped_recordings.append(recording_id)
                    continue
                else:
                    confidence_label = "high" if review_count >= 5 else "medium" if review_count >= 3 else "low"
                    console.print(f"      📝 Processing {review_count} reviews ({confidence_label} confidence data)...")
                
                # Process with timing
                start_time = time.time()
                analysis = self._process_recording_enhanced(recording_id)
                processing_time = time.time() - start_time
                
                if analysis and analysis != "skip":
                    # Show results
                    ai_rating = analysis.get('ai_rating', {})
                    stars = ai_rating.get('stars', 0)
                    confidence = ai_rating.get('confidence', 'unknown')
                    
                    console.print(f"      🤖 LLM Analysis: {processing_time//60:.0f}m {processing_time%60:.0f}s → ⭐ {stars:.1f} stars ({confidence} confidence)")
                    console.print(f"      ✅ Recording analysis complete")
                    
                    recording_analyses.append(analysis)
                    self.stats['recordings_processed'] += 1
                else:
                    console.print(f"      ⚠️  Processing failed or skipped")
                    failed_recordings.append(recording_id)
                    
            except Exception as e:
                console.print(f"      ❌ [red]Failed:[/red] {str(e)[:60]}...")
                logger.debug(f"  Failed to process recording {recording_id}: {e}")
                failed_recordings.append(recording_id)
                self.stats['failed_recordings'].append({
                    'recording': recording_id,
                    'show': show_name,
                    'error': str(e),
                    'timestamp': datetime.now(timezone.utc).isoformat()
                })
        
        # Show recording processing summary
        successful_count = len(recording_analyses)
        total_count = len(recordings)
        skipped_count = len(skipped_recordings)
        failed_count = len(failed_recordings)
        
        if successful_count == 0:
            raise Exception(f"No successful recording analyses (failed: {failed_count}, skipped: {skipped_count})")
        
        # Stage 2: Generate show review with enhanced output
        console.print(f"   🎭 Generating show review from {successful_count} successful analyses...")
        
        try:
            start_time = time.time()
            show_review = self._generate_show_review(show_data, recording_analyses)
            processing_time = time.time() - start_time
            
            # Show show review results
            ai_rating = show_review.get('ratings', {}).get('ai_rating', 0)
            confidence = show_review.get('ratings', {}).get('confidence', 'unknown')
            best_recording = show_review.get('best_recording', {}).get('identifier', 'unknown')
            
            console.print(f"   🤖 Show Analysis: {processing_time:.0f}s → ⭐ {ai_rating:.1f} stars ({confidence} confidence)")
            
            # Update show data
            show_data['ai_show_review'] = show_review

            # Save updated show
            with open(show_path, 'w') as f:
                json.dump(show_data, f, indent=2, ensure_ascii=False)

            # Also save AI show review to stage00 as durable source of truth
            stage00_show_path = Path(f"stage00-created-data/ai-reviews/shows/{show_path.stem}.json")
            stage00_show_path.parent.mkdir(parents=True, exist_ok=True)
            with open(stage00_show_path, 'w') as f:
                json.dump(show_review, f, indent=2, ensure_ascii=False)

            self.stats['shows_processed'] += 1

            # Show completion summary
            console.print(f"   ✅ [green]Show complete:[/green] {show_name.replace('.json', '')} ({successful_count}/{total_count} recordings, {skipped_count} skipped)")
            if best_recording != 'unknown':
                # Get recording quality info
                best_recording_short = best_recording.split('.')[-2] if '.' in best_recording else best_recording[-20:]
                console.print(f"   📄 Best recording: {best_recording_short} (recommended)")
            
        except Exception as e:
            raise Exception(f"Failed to generate show review: {e}")
    
    def _process_single_show(self, show_path: Path, clobber: bool, progress=None, task=None) -> None:
        """Process a single show through both analysis stages."""
        show_name = show_path.name
        
        # Detailed logging goes to file only
        logger.debug(f"Processing show: {show_name}")
        
        # Load show data
        try:
            with open(show_path) as f:
                show_data = json.load(f)
        except Exception as e:
            raise Exception(f"Failed to load show data: {e}")
        
        # Check if already processed
        if not clobber and show_data.get('ai_show_review', {}).get('processing_status') == 'completed':
            logger.debug(f"Skipping already processed show: {show_name}")
            self.stats['shows_skipped'] += 1
            return
        
        # Get recordings list
        recordings = show_data.get('recordings', [])
        if not recordings:
            raise Exception(f"No recordings found in show data")
        
        logger.debug(f"Found {len(recordings)} recordings to analyze for {show_name}")
        
        # Stage 1: Process individual recordings
        recording_analyses = []
        failed_recordings = []
        
        for recording_id in recordings:
            try:
                logger.debug(f"  Processing recording: {recording_id}")
                analysis = self._process_recording(recording_id)
                
                if analysis == "skip":
                    # Recording was skipped (no reviews, etc.) - just advance progress
                    pass
                elif analysis:
                    # Successful analysis
                    recording_analyses.append(analysis)
                    self.stats['recordings_processed'] += 1
                else:
                    # Failed analysis
                    failed_recordings.append(recording_id)
                    
                # Always advance progress bar for each recording (processed, skipped, or failed)
                if progress and task:
                    progress.update(task, advance=1)
                    
            except Exception as e:
                logger.debug(f"  Failed to process recording {recording_id}: {e}")
                failed_recordings.append(recording_id)
                self.stats['failed_recordings'].append({
                    'recording': recording_id,
                    'show': show_name,
                    'error': str(e),
                    'timestamp': datetime.now(timezone.utc).isoformat()
                })
                
                # Still advance progress even for failed recordings
                if progress and task:
                    progress.update(task, advance=1)
                continue
        
        if not recording_analyses:
            raise Exception(f"No successful recording analyses (failed: {len(failed_recordings)})")
        
        # Stage 2: Generate show review
        try:
            logger.debug(f"  Generating show review for {show_name}")
            show_review = self._generate_show_review(show_data, recording_analyses)
            
            # Update show data
            show_data['ai_show_review'] = show_review

            # Save updated show
            with open(show_path, 'w') as f:
                json.dump(show_data, f, indent=2, ensure_ascii=False)

            # Also save AI show review to stage00 as durable source of truth
            stage00_show_path = Path(f"stage00-created-data/ai-reviews/shows/{show_path.stem}.json")
            stage00_show_path.parent.mkdir(parents=True, exist_ok=True)
            with open(stage00_show_path, 'w') as f:
                json.dump(show_review, f, indent=2, ensure_ascii=False)

            self.stats['shows_processed'] += 1
            logger.debug(f"✅ Completed show: {show_name} ({len(recording_analyses)} recordings processed)")
            
        except Exception as e:
            raise Exception(f"Failed to generate show review: {e}")
    
    def _process_recording(self, recording_id: str) -> Optional[Dict[str, Any]]:
        """Process a single recording's reviews through LLM analysis."""
        logger.debug(f"    Analyzing recording: {recording_id}")
        
        # Load raw reviews from stage01-collected-data/archive
        archive_path = Path(f"stage01-collected-data/archive/{recording_id}.json")
        if not archive_path.exists():
            logger.debug(f"      Archive file not found: {archive_path}")
            return "skip"  # Signal to skip but still advance progress
        
        try:
            with open(archive_path) as f:
                archive_data = json.load(f)
        except Exception as e:
            logger.debug(f"      Failed to load archive data: {e}")
            return "skip"  # Signal to skip but still advance progress
        
        # Extract raw reviews
        raw_reviews = archive_data.get('raw_reviews', [])
        if not raw_reviews:
            logger.debug(f"      No reviews found for recording: {recording_id}")
            return "skip"  # Signal to skip but still advance progress
        
        if len(raw_reviews) < self.config.processing.min_reviews_for_processing:
            logger.debug(f"      Too few reviews ({len(raw_reviews)}) for processing: {recording_id}")
            return "skip"  # Signal to skip but still advance progress
        
        # Prepare LLM input
        show_date = archive_data.get('date', 'Unknown')
        input_data = {
            'recording_id': recording_id,
            'show_date': show_date,
            'raw_reviews': raw_reviews
        }
        
        user_prompt = f"""
Analyze the following Grateful Dead recording reviews:

**Recording**: {recording_id}
**Show Date**: {show_date}
**Reviews**: {json.dumps(raw_reviews, indent=2)}

Please analyze these reviews and respond with a JSON object following the specified format.
"""
        
        try:
            # Get LLM analysis
            response = self.llm_client.generate_response(
                system_prompt=self.prompts['recording_analysis'],
                user_prompt=user_prompt
            )
            
            # Parse JSON response - handle code block wrapping
            content = response['content'].strip()
            
            # Remove common markdown code block wrappers
            if content.startswith('```json'):
                content = content[7:]  # Remove ```json
            elif content.startswith('```'):
                content = content[3:]   # Remove ```
                
            if content.endswith('```'):
                content = content[:-3]  # Remove closing ```
                
            content = content.strip()
            
            try:
                ai_review = json.loads(content)
            except json.JSONDecodeError as e:
                logger.error(f"      Failed to parse LLM JSON response: {e}")
                logger.debug(f"      Raw response: {content[:500]}...")
                return None
            
            # Add metadata
            ai_review['processed_date'] = datetime.now(timezone.utc).isoformat()
            ai_review['model_used'] = response.get('model', self.llm_client.config.model)
            ai_review['recording_id'] = recording_id
            
            # Save to stage02-generated-data/recordings
            recording_path = Path(f"stage02-generated-data/recordings/{recording_id}.json")
            
            # Load existing recording data if it exists
            if recording_path.exists():
                with open(recording_path) as f:
                    recording_data = json.load(f)
            else:
                # Create basic recording data structure
                recording_data = {
                    'identifier': recording_id,
                    'date': show_date
                }
            
            # Add ai_review to recording data
            recording_data['ai_review'] = ai_review
            
            # Ensure parent directory exists
            recording_path.parent.mkdir(parents=True, exist_ok=True)
            
            # Save updated recording data
            with open(recording_path, 'w') as f:
                json.dump(recording_data, f, indent=2, ensure_ascii=False)

            # Also save AI review to stage00 as durable source of truth
            stage00_path = Path(f"stage00-created-data/ai-reviews/recordings/{recording_id}.json")
            stage00_path.parent.mkdir(parents=True, exist_ok=True)
            with open(stage00_path, 'w') as f:
                json.dump(ai_review, f, indent=2, ensure_ascii=False)

            logger.debug(f"      ✅ Completed recording analysis: {recording_id}")
            return ai_review
            
        except Exception as e:
            logger.debug(f"      Failed to process recording {recording_id}: {e}")
            return None
    
    def _process_recording_enhanced(self, recording_id: str) -> Optional[Dict[str, Any]]:
        """Enhanced recording processing that returns results for display."""
        # Use the existing processing method
        result = self._process_recording(recording_id)
        return result
    
    def _calculate_review_metrics(self, show_data: Dict[str, Any]) -> Dict[str, Any]:
        """Calculate review count totals and boost tiers programmatically."""
        recordings = show_data.get('recordings', [])
        total_reviews = 0
        
        # Sum review counts from each recording's metadata
        for recording_id in recordings:
            recording_path = Path(f"stage02-generated-data/recordings/{recording_id}.json")
            if recording_path.exists():
                try:
                    with open(recording_path) as f:
                        recording_data = json.load(f)
                        total_reviews += recording_data.get('review_count', 0)
                except Exception as e:
                    logger.warning(f"Failed to load review count for {recording_id}: {e}")
                    continue
        
        # Apply corrected tier logic (99th percentile coverage)
        if total_reviews < 5:
            boost = 0.0
            tier = "Below threshold"
        elif 5 <= total_reviews <= 19:
            boost = 0.1
            tier = "Tier 1: Above minimal threshold"
        elif 20 <= total_reviews <= 28:
            boost = 0.2
            tier = "Tier 2: Standard attention"
        elif 29 <= total_reviews <= 45:
            boost = 0.3
            tier = "Tier 3: Notable attention"
        elif 46 <= total_reviews <= 155:
            boost = 0.4
            tier = "Tier 4: High attention"
        else:  # 156+
            boost = 0.5
            tier = "Tier 5: Exceptional attention"
        
        return {
            'total_reviews': total_reviews,
            'review_count_boost': boost,
            'boost_tier': tier
        }

    def _generate_show_review(self, show_data: Dict[str, Any], 
                            recording_analyses: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Generate final show review from recording analyses."""
        logger.debug("    Generating show review...")
        
        # Calculate average rating from show data if available
        average_rating = show_data.get('average_rating', 0.0)
        if not average_rating and 'recordings' in show_data:
            # Try to calculate from recordings if not already present
            # This would be implementation-specific based on your data structure
            pass
        
        # Prepare show information
        show_date = show_data.get('date', 'Unknown')
        venue = show_data.get('venue', {})
        # Handle venue being either a dict or string
        if isinstance(venue, str):
            venue_name = venue
        elif isinstance(venue, dict):
            venue_name = venue.get('name', 'Unknown Venue')
        else:
            venue_name = 'Unknown Venue'
        
        # Calculate review metrics programmatically
        review_metrics = self._calculate_review_metrics(show_data)
        
        # Combine recording analyses
        analyses_summary = []
        for analysis in recording_analyses:
            analyses_summary.append({
                'recording_id': analysis.get('recording_id'),
                'summary': analysis.get('summary', ''),
                'sentiment': analysis.get('sentiment', 'neutral'),
                'recording_quality': analysis.get('recording_quality', {}),
                'show_quality': analysis.get('show_quality', {}),
                'band_member_comments': analysis.get('band_member_comments', {})
            })
        
        # Create user prompt combining all analyses
        user_prompt = f"""
Generate a comprehensive show review based on the following information:

**Show Information:**
- Date: {show_date}
- Venue: {venue_name}
- Average User Rating: {average_rating}

**Pre-calculated Rating Data:**
- Total Reviews: {review_metrics['total_reviews']}
- Review Count Boost: +{review_metrics['review_count_boost']} ({review_metrics['boost_tier']})
- Base Rating: 2.5

**Recording Analyses:**
{json.dumps(analyses_summary, indent=2)}

**Show Data:**
{json.dumps({k: v for k, v in show_data.items() if k not in ['recordings', 'ai_show_review']}, indent=2)}

IMPORTANT: Use the pre-calculated Total Reviews and Review Count Boost values in your rating_calculation block. Do not recalculate them.
Calculate only the sentiment_adjustment based on the recording analyses sentiment patterns.

Please synthesize this information into a comprehensive show review following the specified JSON format.
"""
        
        try:
            # Get LLM show review
            response = self.llm_client.generate_response(
                system_prompt=self.prompts['show_review'],
                user_prompt=user_prompt
            )
            
            # Parse JSON response - handle code block wrapping
            content = response['content'].strip()
            
            # Remove common markdown code block wrappers
            if content.startswith('```json'):
                content = content[7:]  # Remove ```json
            elif content.startswith('```'):
                content = content[3:]   # Remove ```
                
            if content.endswith('```'):
                content = content[:-3]  # Remove closing ```
                
            content = content.strip()
            
            try:
                show_review = json.loads(content)
            except json.JSONDecodeError as e:
                logger.error(f"      Failed to parse show review JSON response: {e}")
                logger.debug(f"      Raw response: {content[:500]}...")
                # Create fallback review
                show_review = self._create_fallback_show_review(recording_analyses, average_rating)
            
            # Add metadata and ensure required fields
            show_review['processed_recordings'] = len(recording_analyses)
            show_review['processed_date'] = datetime.now(timezone.utc).isoformat()
            show_review['model_used'] = response.get('model', self.llm_client.config.model)
            show_review['processing_status'] = 'completed'
            
            # Ensure ratings structure exists
            if 'ratings' not in show_review:
                show_review['ratings'] = {}
            show_review['ratings']['average_rating'] = average_rating
            
            # Ensure best_recording is selected
            if 'best_recording' not in show_review and recording_analyses:
                # Default to first recording if not specified
                show_review['best_recording'] = {
                    'identifier': recording_analyses[0].get('recording_id', ''),
                    'reason': 'Default selection - first available recording'
                }
            
            # Extract must-listen sequences from song highlights and setlist
            try:
                song_highlights = show_review.get('song_highlights', [])
                setlist = show_data.get('setlist', [])
                
                if song_highlights and setlist:
                    must_listen_sequences = extract_must_listen_sequences(song_highlights, setlist)
                    if must_listen_sequences:
                        show_review['must_listen_sequences'] = must_listen_sequences
                        logger.debug(f"      ✅ Extracted {len(must_listen_sequences)} must-listen sequences")
                    else:
                        logger.debug(f"      ℹ️  No must-listen sequences extracted")
                else:
                    logger.debug(f"      ℹ️  Skipping sequence extraction - missing song_highlights or setlist")
                    
            except Exception as e:
                logger.warning(f"      ⚠️  Failed to extract must-listen sequences: {e}")
                # Don't fail the whole process if sequence extraction fails
            
            logger.debug(f"      ✅ Generated show review with AI rating: {show_review.get('ratings', {}).get('ai_rating', 'N/A')}")
            return show_review
            
        except Exception as e:
            logger.debug(f"      Failed to generate show review: {e}")
            # Return fallback review
            return self._create_fallback_show_review(recording_analyses, average_rating)
    
    def _create_fallback_show_review(self, recording_analyses: List[Dict[str, Any]], 
                                   average_rating: float) -> Dict[str, Any]:
        """Create a fallback show review when LLM processing fails."""
        return {
            'summary': 'Show processed with limited analysis due to technical issues',
            'review': 'This show was processed but encountered technical difficulties during AI analysis. Please refer to individual recording reviews for detailed insights.',
            'ratings': {
                'average_rating': average_rating,
                'ai_rating': average_rating,  # Fallback to average
                'confidence': 'low'
            },
            'best_recording': {
                'identifier': recording_analyses[0]['recording_id'] if recording_analyses else '',
                'reason': 'First available recording (fallback selection)'
            },
            'key_highlights': ['Analysis incomplete due to technical issues'],
            'processed_recordings': len(recording_analyses),
            'processed_date': datetime.now(timezone.utc).isoformat(),
            'model_used': 'fallback',
            'processing_status': 'completed_with_errors'
        }
    
    def _print_summary(self) -> None:
        """Print processing summary statistics."""
        end_time = datetime.now(timezone.utc)
        total_runtime = (end_time - self.stats['start_time']).total_seconds()
        
        # Collect LLM stats
        llm_stats = {
            'api_calls': self.llm_client.api_calls,
            'api_failures': self.llm_client.api_failures,
            'success_rate': ((self.llm_client.api_calls - self.llm_client.api_failures) / max(self.llm_client.api_calls, 1)) * 100
        }
        
        # Create comprehensive summary
        summary = {
            'runtime': {
                'total_seconds': total_runtime,
                'start_time': self.stats['start_time'].isoformat(),
                'end_time': end_time.isoformat(),
            },
            'processing': {
                'shows_processed': self.stats['shows_processed'],
                'recordings_processed': self.stats['recordings_processed'], 
                'shows_skipped': self.stats['shows_skipped'],
                'errors': self.stats['errors'],
                'success_rate': (self.stats['shows_processed'] / max(self.stats['shows_processed'] + self.stats['errors'], 1)) * 100
            },
            'llm_usage': llm_stats,
            'failed_items': {
                'shows': self.stats['failed_shows'],
                'recordings': self.stats['failed_recordings']
            }
        }
        
        # Log detailed summary
        logger.info("📊 Processing Summary:")
        logger.info(f"   Runtime: {total_runtime:.1f}s ({total_runtime/60:.1f}m)")
        logger.info(f"   Shows processed: {self.stats['shows_processed']}")
        logger.info(f"   Recordings processed: {self.stats['recordings_processed']}")
        logger.info(f"   Shows skipped: {self.stats['shows_skipped']}")
        logger.info(f"   Errors: {self.stats['errors']}")
        logger.info(f"   Success rate: {summary['processing']['success_rate']:.1f}%")
        logger.info(f"   LLM API calls: {llm_stats['api_calls']} (failures: {llm_stats['api_failures']})")
        logger.info(f"   LLM success rate: {llm_stats['success_rate']:.1f}%")
        
        # Console display (prettier)
        console.print("\n[bold green]🏁 Processing Summary:[/bold green]")
        console.print(f"⏱️  Runtime: {total_runtime:.1f}s ({total_runtime/60:.1f}m)")
        console.print(f"✅ Shows processed: {self.stats['shows_processed']}")
        console.print(f"🎵 Recordings processed: {self.stats['recordings_processed']}")
        console.print(f"⏭️  Shows skipped: {self.stats['shows_skipped']}")
        console.print(f"❌ Errors: {self.stats['errors']}")
        console.print(f"🎯 Success rate: {summary['processing']['success_rate']:.1f}%")
        console.print(f"🤖 LLM calls: {llm_stats['api_calls']} (failures: {llm_stats['api_failures']})")
        
        # Log failed items if any
        if self.stats['failed_shows']:
            logger.warning("⚠️  Failed shows:")
            for failure in self.stats['failed_shows']:
                logger.warning(f"   - {failure['show']}: {failure['error']}")
        
        if self.stats['failed_recordings']:
            logger.warning("⚠️  Failed recordings:")
            for failure in self.stats['failed_recordings']:
                logger.warning(f"   - {failure['recording']}: {failure['error']}")
        
        # Write summary to file for batch processing analysis
        output_dir = Path('.review-processor')
        output_dir.mkdir(exist_ok=True)
        
        timestamp = datetime.now().strftime('%Y-%m-%d_%H-%M-%S')
        summary_file = output_dir / f"processing-summary-{timestamp}.json"
        
        try:
            with open(summary_file, 'w') as f:
                import json
                json.dump(summary, f, indent=2, default=str)
            logger.debug(f"📄 Summary written to {summary_file}")
        except Exception as e:
            logger.warning(f"Failed to write summary file: {e}")


def main():
    """Main entry point for the review processor."""
    parser = argparse.ArgumentParser(
        description="Process Grateful Dead show reviews using LLM analysis",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python review_processor.py "stage02-generated-data/shows/gd1977-*"
  python review_processor.py "stage02-generated-data/shows/gd1993-05-16*" --clobber
  python review_processor.py "stage02-generated-data/shows/*" --provider anthropic
        """
    )
    
    parser.add_argument(
        'path_pattern',
        help='Glob pattern for show files to process'
    )
    
    parser.add_argument(
        '--clobber',
        action='store_true',
        help='Reprocess shows even if already completed'
    )
    
    parser.add_argument(
        '--provider',
        help='Override default LLM provider (lmstudio, openai, anthropic)'
    )
    
    parser.add_argument(
        '--model',
        help='Override default model for selected provider'
    )
    
    parser.add_argument(
        '--config',
        type=Path,
        default=Path(__file__).parent / 'config.json',
        help='Path to configuration file'
    )
    
    parser.add_argument(
        '--list-providers',
        action='store_true',
        help='List available providers and exit'
    )
    
    parser.add_argument(
        '--dry-run',
        action='store_true',
        help='Show what would be processed without making changes'
    )
    
    parser.add_argument(
        '--verbose',
        action='store_true',
        help='Enable verbose logging'
    )
    
    parser.add_argument(
        '--log-file',
        type=Path,
        help='Log file path (default: review-processor-YYYY-MM-DD.log)'
    )
    
    parser.add_argument(
        '--no-log-file',
        action='store_true',
        help='Disable file logging (console only)'
    )
    
    args = parser.parse_args()
    
    # Configure logging
    log_file = None
    if not args.no_log_file:
        # Create .review-processor directory for all output
        output_dir = Path('.review-processor')
        output_dir.mkdir(exist_ok=True)
        
        if args.log_file:
            log_file = args.log_file
        else:
            # Default log file with timestamp in output directory
            timestamp = datetime.now().strftime('%Y-%m-%d')
            log_file = output_dir / f"review-processor-{timestamp}.log"
    
    setup_logging(verbose=args.verbose, log_file=log_file)
    
    # Log startup info
    logger.info(f"🚀 Review Processor starting up at {datetime.now(timezone.utc).isoformat()}")
    if log_file:
        logger.info(f"📝 Logging to file: {log_file}")
    else:
        logger.info("📝 Console logging only")
    logger.debug(f"   Arguments: {vars(args)}")
    
    # Load configuration
    try:
        config = Config.load(args.config)
    except Exception as e:
        console.print(f"[red]Failed to load configuration: {e}[/red]")
        sys.exit(1)
    
    # Handle list-providers command
    if args.list_providers:
        console.print("[bold]Available Providers:[/bold]")
        for name, provider in config.providers.items():
            default_marker = " (default)" if name == config.default_provider else ""
            console.print(f"  {name}: {provider.model}{default_marker}")
        sys.exit(0)
    
    # Handle dry-run
    if args.dry_run:
        show_files = glob.glob(args.path_pattern)
        console.print(f"[yellow]Dry run - would process {len(show_files)} files:[/yellow]")
        for show_file in sorted(show_files)[:10]:  # Show first 10
            console.print(f"  {show_file}")
        if len(show_files) > 10:
            console.print(f"  ... and {len(show_files) - 10} more")
        sys.exit(0)
    
    # Create processor
    try:
        processor = ReviewProcessor(config, args.provider)
    except Exception as e:
        console.print(f"[red]Failed to initialize processor: {e}[/red]")
        sys.exit(1)
    
    # Process shows
    try:
        processor.process_show_pattern(args.path_pattern, args.clobber)
    except KeyboardInterrupt:
        console.print("\n[yellow]Processing interrupted by user[/yellow]")
        processor._print_summary()
    except Exception as e:
        console.print(f"[red]Processing failed: {e}[/red]")
        sys.exit(1)


if __name__ == '__main__':
    main()