# Review Processor

A sophisticated LLM-powered system for analyzing Grateful Dead concert reviews and generating comprehensive show summaries. This tool processes raw Archive.org reviews through AI analysis to create insightful, natural-sounding show reviews and ratings.

## System Architecture

### Overview
The Review Processor implements a two-stage pipeline that transforms raw user reviews into structured AI analysis and final show reviews:

1. **Stage 1 - Recording Analysis**: Process raw reviews for individual recordings
2. **Stage 2 - Show Review Generation**: Aggregate recording analyses into comprehensive show reviews

### Critical Data Flow

```
stage01-collected-data/archive/{identifier}.json (raw reviews)
    ↓ [Stage 1: Recording Analysis]
stage02-generated-data/recordings/{identifier}.json (+ ai_review)
    ↓ [Stage 2: Show Summary] 
stage02-generated-data/shows/{show}.json (+ show review)
```

**IMPORTANT**: Recording analyses (`ai_review` objects) are written to `stage02-generated-data/recordings/{identifier}.json`, NOT to show files. This maintains proper data architecture where recordings own their analysis and shows own their summaries.

### Per-Show Processing Flow

The system processes one complete show at a time through both stages before moving to the next show:

1. **Show Selection**: Load `stage02-generated-data/shows/gd1977-05-08.json`
2. **Recording Analysis Loop**:
   - For each recording ID in `.recordings` array
   - Load raw data from `stage01-collected-data/archive/{identifier}.json`
   - Extract `raw_reviews` and send to LLM for analysis
   - Generate `ai_review` object with structured insights
   - **Save to `stage02-generated-data/recordings/{identifier}.json`** (not show file)
3. **Show Review Generation**:
   - Collect all `ai_review` objects from recordings for this show
   - Send combined analysis to LLM for final show review
   - Generate show-level review with ratings and summaries
   - Add review to `stage02-generated-data/shows/gd1977-05-08.json`
   - Mark show as processed
4. **Move to Next Show**

### Data Structure Specifications

#### Recording-Level `ai_review` Object
Added to `stage02-generated-data/recordings/{identifier}.json`:

```json
{
  "ai_review": {
    "summary": "One-line summary of recording quality and show highlights",
    "review": "Detailed analysis of the show and recording quality",
    "sentiment": "positive|negative|mixed",
    "ai_rating": {
      "stars": 4.7,
      "confidence": "high|medium|low",
      "rationale": "Explanation of how the rating was derived from reviews"
    },
    "recording_quality": {
      "source_type": "soundboard|audience|matrix",
      "quality_rating": "excellent|good|fair|poor",
      "technical_notes": "Comments on sound quality, mix, clarity"
    },
    "show_quality": {
      "standout_songs": ["Help on the Way", "Fire on the Mountain"],
      "poor_songs": ["song with issues"],
      "setlist_flow": "Comments on set structure and flow"
    },
    "band_member_comments": {
      "Jerry": "Guitar work assessment",
      "Phil": "Bass performance notes",
      "Bob": "Rhythm guitar and vocals",
      "Keys": "Keyboard player performance (Keith, Brent, or Vince depending on era)",
      "Drums": "Drums assessment (Bill, Mickey)"
    },
    "song_mentions": {
      "Song Name": {
        "positive_mentions": 5,
        "negative_mentions": 0,
        "total_mentions": 5
      }
    },
    "processed_date": "2025-09-24T23:15:06.513883+00:00",
    "model_used": "gpt-oss-120b",
    "recording_id": "gd1977-05-08.sbd.cantor.sacks.266.shnf"
  }
}
```

**Note on schema evolution**: The `song_mentions` and `recording_id` fields were added in the September 2025 processing run. A small number of older recordings (28 as of Feb 2025) still use the previous format without these fields. The `energy_level` field in `show_quality` was removed in the same update.

#### Show-Level Review Object
Added to `stage02-generated-data/shows/{show}.json`:

```json
{
  "ai_show_review": {
    "summary": "Brief one-line show summary for app display",
    "review": "Full 1-2 paragraph review combining all recording insights",
    "ratings": {
      "average_rating": 4.2,
      "ai_rating": 4.5,
      "confidence": "high|medium|low"
    },
    "best_recording": {
      "identifier": "gd77-05-08.sbd.miller.97968.sbeok.flac16",
      "reason": "Why this recording is recommended"
    },
    "key_highlights": [
      "Standout musical moments",
      "Notable performances", 
      "Historical significance"
    ],
    "processed_recordings": 3,
    "processed_date": "2024-01-15T10:35:00Z",
    "model_used": "gpt-4",
    "processing_status": "completed"
  }
}
```

### Processing Status and Skip Logic

Shows are marked with `processing_status: "completed"` to enable smart skipping:
- **Default behavior**: Skip shows with `processing_status: "completed"`
- **`--clobber` flag**: Reprocess all shows regardless of status
- **Resume capability**: Can interrupt and restart processing safely

## Configuration System

### Self-Contained Provider Configuration

Each LLM provider has a complete, independent configuration block in `config.json`:

```json
{
  "providers": {
    "lmstudio": {
      "endpoint": "http://worklaptop.local:1234/v1",
      "api_key": "dummy-key",
      "model": "current_model",
      "rate_limiting": {
        "delay_seconds": 0.1,
        "max_retries": 3,
        "backoff_factor": 2.0
      },
      "custom_headers": {},
      "timeout": 30,
      "max_tokens": 4096
    },
    "openai": {
      "endpoint": "https://api.openai.com/v1", 
      "api_key": "sk-your-key-here",
      "model": "gpt-4",
      "rate_limiting": {
        "delay_seconds": 2.0,
        "max_retries": 5,
        "backoff_factor": 2.0
      },
      "organization": "optional-org-id",
      "timeout": 60,
      "max_tokens": 4096
    },
    "anthropic": {
      "endpoint": "https://api.anthropic.com",
      "api_key": "sk-ant-your-key-here",
      "model": "claude-3-sonnet-20240229",
      "rate_limiting": {
        "delay_seconds": 1.2,
        "max_retries": 3,
        "backoff_factor": 1.5
      },
      "max_tokens": 4096,
      "timeout": 90
    }
  },
  "default_provider": "lmstudio",
  "processing": {
    "cache_responses": true,
    "skip_processed": true,
    "min_reviews_for_processing": 1,
    "output_debug": false,
    "batch_size": 1
  }
}
```

### Provider-Specific Features

- **LMStudio**: Local processing, fast rate limiting, dummy API key
- **OpenAI**: Organization support, higher rate limits, timeout handling
- **Anthropic**: Custom max_tokens, specialized timeout values
- **Independent Rate Limiting**: Each provider can have different delays and retry logic

## Prompt Engineering Framework

### Prompt Organization

Prompts are stored in versioned markdown files in the `prompts/` directory:

- `prompts/recording_analysis.md` - Stage 1: Individual recording review analysis
- `prompts/show_review.md` - Stage 2: Final show review generation  
- `prompts/persona_guidelines.md` - Deadhead writing style and voice guidelines

### Deadhead Persona Guidelines

Reviews should feel natural and authentic, as if written by an experienced Deadhead:

- Use Dead-specific terminology naturally ("smokin' Hot", "Phil bombs", "Jerry's tone")
- Reference musical relationships and song transitions
- Acknowledge both musical excellence and off nights honestly
- Include historical context when relevant
- Avoid overly technical language or AI-sounding phrases
- Capture the communal experience and energy of the shows

### Prompt Versioning

- Prompts are version controlled in the repository
- Use markdown format for readability and collaboration
- Include examples and expected output formats
- Document prompt changes and improvements over time

## CLI Interface

### Basic Usage

```bash
# Process single show pattern
python review_processor.py "stage02-generated-data/shows/gd1977-05-08*"

# Process multiple shows with wildcards  
python review_processor.py "stage02-generated-data/shows/gd1977-*"

# Process all shows (use with caution!)
python review_processor.py "stage02-generated-data/shows/*"
```

### Advanced Options

```bash
# Force reprocess completed shows
python review_processor.py "stage02-generated-data/shows/gd1993-*" --clobber

# Use specific provider and model
python review_processor.py "stage02-generated-data/shows/gd1977-*" --provider anthropic
python review_processor.py "stage02-generated-data/shows/gd1977-*" --provider openai --model gpt-4-turbo

# Debug and analysis modes
python review_processor.py "stage02-generated-data/shows/gd1977-05-08*" --debug --verbose
python review_processor.py --list-providers  # Show configured providers
python review_processor.py --analyze "stage02-generated-data/shows/gd1977-*"  # Analyze without processing
```

### Command Line Arguments

- `path_pattern`: Glob pattern for show files (supports tab completion)
- `--clobber`: Reprocess shows even if already completed
- `--provider`: Override default provider (lmstudio, openai, anthropic)
- `--model`: Override configured model for selected provider
- `--debug`: Enable debug output and response caching
- `--verbose`: Detailed processing information
- `--dry-run`: Show what would be processed without making changes
- `--list-providers`: Display available providers and models
- `--analyze`: Analyze shows and recordings without processing
- `--log-file PATH`: Specify custom log file path
- `--no-log-file`: Disable file logging (console only)

## Error Handling and Recovery

### Graceful Error Handling

- **Missing Shows**: Skip non-existent show files with warning
- **Missing Recordings**: Skip recordings not found in stage01/archive
- **No Reviews**: Skip recordings with empty or missing `raw_reviews`
- **API Failures**: Retry with exponential backoff, skip after max retries
- **Invalid JSON**: Log error and continue with next item

### Recovery Mechanisms

- **Resume Processing**: Skip completed shows automatically
- **Partial Recovery**: Process remaining recordings if some fail
- **Cache Utilization**: Use cached responses when available
- **Progress Tracking**: Clear indication of processing status

### Logging and Debugging

- **Structured Logging**: Clear indication of current processing step  
- **Error Context**: Full context for debugging failures
- **Performance Metrics**: Processing time and API usage statistics
- **Cache Status**: Hit/miss rates and response caching effectiveness

## Performance Considerations

### Rate Limiting Strategy

Different providers require different approaches:
- **LMStudio**: Minimal delays (0.1s) for local processing
- **OpenAI**: Moderate delays (2.0s) to respect rate limits
- **Anthropic**: Conservative delays (1.2s) for stability

### Caching Strategy

- **Response Caching**: Store raw LLM responses to avoid reprocessing
- **Incremental Processing**: Skip completed items automatically  
- **Memory Management**: Process one show at a time to minimize memory usage
- **Disk Usage**: Efficient storage of intermediate results

### Scalability

- **Batch Processing**: Support for processing large show collections
- **Parallel Processing**: Future enhancement for concurrent show processing
- **Resource Monitoring**: Track API usage and costs
- **Progress Reporting**: Clear indication of completion status

## Integration with Existing Pipeline

### Pipeline Compatibility

The Review Processor integrates seamlessly with the existing 4-stage architecture:
- Consumes data from Stage 1 (Archive.org collection)
- Enhances data in Stage 2 (processed shows and recordings)
- Can be included in full pipeline runs via Makefile integration

### Data Integrity

- **Non-destructive**: Never modifies existing data, only adds new fields
- **Backward Compatible**: Enhanced files remain compatible with existing consumers
- **Validation**: Built-in checks for data consistency and completeness

### Future Enhancements

- **V2 Architecture**: Designed to support future database integration
- **API Endpoints**: Could expose review data via REST API
- **Mobile Integration**: Review summaries optimized for mobile app display
- **Analytics**: Support for review quality metrics and analysis

## Processing Status (as of February 2025)

### Coverage Summary

The AI review processing was performed in waves during September 2025 using `gpt-oss-120b` via LMStudio.

| Category | Count | % of Total |
|----------|------:|-----------:|
| Total recordings | 17,854 | 100% |
| No user reviews (nothing to process) | 7,712 | 43.2% |
| Latest format (`ai_review` + `song_mentions`) | 9,860 | 55.2% |
| Older format (`ai_review`, no `song_mentions`) | 28 | 0.2% |
| Has user reviews, missing `ai_review` | 254 | 1.4% |

**Of the 10,142 recordings with user reviews to summarize, 97.5% have been processed.**

### Known Gaps

**254 recordings** have raw fan reviews in `stage01-collected-data/archive/` but no `ai_review` in their recording JSON. These recordings exist within shows that are already marked `processing_status: "completed"` at the show level (110 shows affected). The show-level reviews were generated successfully from the recordings that *were* processed.

**28 recordings** have an `ai_review` from an earlier processing run that lacks the `song_mentions` field added in the latest prompt version.

### Skip Logic Implications

The processor's skip logic operates at the **show level**: if `ai_show_review.processing_status == "completed"`, the entire show is skipped. This means:

- Running the processor again **without** `--clobber` will skip these 110 shows entirely
- Running with `--clobber` will reprocess **all** recordings in those shows, not just the missing ones
- There is no "fill gaps" mode that processes only missing recordings within completed shows

To process only the missing recordings, you would need to either:
1. Use `--clobber` on the affected shows (reprocesses everything, higher LLM cost)
2. Modify the processor to add a `--fill-gaps` mode that checks individual recordings

### Processing History

| Date Range | Wave | Recordings |
|------------|------|-----------|
| Sep 6-11, 2025 | Initial batch + prompt iteration | ~29 |
| Sep 12-19, 2025 | Main bulk processing | ~5,597 |
| Sep 20-23, 2025 | Second wave | ~1,426 |
| Sep 24-26, 2025 | Final batch (latest format) | ~1,637 |

All processing used `gpt-oss-120b` via LMStudio as the default provider.

## Development and Testing

### Development Setup

```bash
cd scripts/review-processor
python -m venv .venv
source .venv/bin/activate  # or .venv\Scripts\activate on Windows
pip install -r requirements.txt
```

### Testing Strategy

- **Unit Testing**: Test individual components (LLM integration, data processing)
- **Integration Testing**: Test full pipeline with sample data
- **Manual Review**: Human review of generated reviews for quality
- **Performance Testing**: Measure processing speed and resource usage

### Code Quality

- **Type Hints**: Full type annotation for better development experience
- **Documentation**: Comprehensive docstrings and inline comments  
- **Error Handling**: Robust error handling with meaningful messages
- **Logging**: Structured logging for debugging and monitoring

---

## Setup and Usage

### Initial Setup (One-time)

**Option 1: Using Makefile (Recommended)**
```bash
# From project root (/home/damian/Developer/dead-metadata)
make setup-review-processor
```

**Option 2: Manual setup**
```bash
# From project root (/home/damian/Developer/dead-metadata)
cd scripts/review-processor

# Create virtual environment
python -m venv .venv
source .venv/bin/activate  # Linux/Mac
# or .venv\Scripts\activate  # Windows

# Install dependencies
pip install -r requirements.txt

# Return to project root
cd ../..
```

### Configure Your LLM Provider

Edit `scripts/review-processor/config.json` with your settings:

**For LMStudio (recommended for development):**
```json
{
  "providers": {
    "lmstudio": {
      "endpoint": "http://worklaptop.local:1234/v1",
      "api_key": "dummy-key",
      "model": "current_model"
    }
  },
  "default_provider": "lmstudio"
}
```

**For OpenAI:**
```json
{
  "providers": {
    "openai": {
      "endpoint": "https://api.openai.com/v1", 
      "api_key": "sk-your-actual-key-here",
      "model": "gpt-4"
    }
  },
  "default_provider": "openai"
}
```

### Running from Project Root

**Important**: All commands should be run from the project root directory (`/home/damian/Developer/dead-metadata`), not from inside the review-processor directory.

**Option 1: Using Makefile (Recommended)**
```bash
# Test what would be processed (safe)
make process-reviews PATTERN="stage02-generated-data/shows/gd1977-*" FLAGS="--dry-run"

# Process a single show (start here!)
make process-reviews PATTERN="stage02-generated-data/shows/gd1977-05-08*" FLAGS="--verbose"

# Process a year of shows
make process-reviews PATTERN="stage02-generated-data/shows/gd1977-*"

# Force reprocess completed shows  
make process-reviews PATTERN="stage02-generated-data/shows/gd1977-*" FLAGS="--clobber"

# Use different provider
make process-reviews PATTERN="stage02-generated-data/shows/gd1977-*" FLAGS="--provider openai"
```

**Option 2: Direct Python execution**
```bash
# Activate the virtual environment (each time)
source scripts/review-processor/.venv/bin/activate

# Test what would be processed (safe)
python scripts/review-processor/review_processor.py "stage02-generated-data/shows/gd1977-*" --dry-run

# Check available providers
python scripts/review-processor/review_processor.py --list-providers

# Process a single show (start here!)
python scripts/review-processor/review_processor.py "stage02-generated-data/shows/gd1977-05-08*" --verbose

# Process a year of shows  
python scripts/review-processor/review_processor.py "stage02-generated-data/shows/gd1977-*"

# Force reprocess completed shows
python scripts/review-processor/review_processor.py "stage02-generated-data/shows/gd1977-*" --clobber

# Use different provider
python scripts/review-processor/review_processor.py "stage02-generated-data/shows/gd1977-*" --provider openai
```

### Testing Your Setup

1. **Check configuration**: `python scripts/review-processor/review_processor.py --list-providers`
2. **Dry run**: `python scripts/review-processor/review_processor.py "stage02-generated-data/shows/gd1977-*" --dry-run`
3. **Process one show**: `python scripts/review-processor/review_processor.py "stage02-generated-data/shows/gd1977-05-08*" --verbose`
4. **Check results**: Look for new `ai_review` fields in `stage02-generated-data/recordings/` and `ai_show_review` in `stage02-generated-data/shows/`

### Troubleshooting

**"No show files found"**: Make sure you're running from the project root, not inside the review-processor directory.

**"Archive file not found"**: Ensure you have raw Archive.org data in `stage01-collected-data/archive/`.

**LLM connection issues**: Verify your provider configuration and that the service is running (for LMStudio) or API key is valid (for OpenAI/Anthropic).

## Logging and Monitoring

### Comprehensive Logging System

The Review Processor includes a sophisticated logging system designed for long-running batch processing:

#### Automatic File Logging
```bash
# Default: Creates logs in .review-processor/ directory
python scripts/review-processor/review_processor.py "stage02-generated-data/shows/gd1977-*"
# → .review-processor/review-processor-2024-01-15.log

# Custom log file (can be anywhere)
python scripts/review-processor/review_processor.py "stage02-generated-data/shows/gd1977-*" --log-file my-processing.log

# Console only (no file logging)
python scripts/review-processor/review_processor.py "stage02-generated-data/shows/gd1977-*" --no-log-file
```

#### Organized Output Directory
All review processor files are stored in `.review-processor/` (automatically created):
- `review-processor-YYYY-MM-DD.log` - Daily log files
- `processing-summary-YYYY-MM-DD_HH-MM-SS.json` - Run summaries with statistics
- Future: Response cache files for LLM responses

#### Log Levels and Content

**Console Output (Normal)**:
- Clean progress bar showing current show being processed
- Processing time estimates and completion percentage  
- Startup/completion messages with emoji
- Error summary at end (recent failures only)
- Final statistics summary

**Console Output (Verbose)**:
- All normal output plus:
- Major processing milestones
- Warnings and non-critical issues
- Still clean and progress-focused (detailed logs go to file)

**File Output (Always Debug Level)**:
- Complete processing timeline with timestamps
- Full error stack traces
- LLM API request/response details
- Token usage and performance metrics
- Processing state for resumability

#### Log Rotation
- Automatic rotation at 10MB file size
- Keeps 5 backup files (50MB total max)
- Timestamped entries for debugging

### Long-Running Processing

For batch processing large numbers of shows:

```bash
# Start background processing with comprehensive logging
nohup python scripts/review-processor/review_processor.py "stage02-generated-data/shows/gd197*" --verbose > console.log 2>&1 &

# Monitor progress
tail -f .review-processor/review-processor-2024-01-15.log

# Check for errors in real-time
tail -f .review-processor/review-processor-2024-01-15.log | grep "ERROR\|❌"

# Monitor API usage
tail -f .review-processor/review-processor-2024-01-15.log | grep "LLM API call"
```

### Error Identification and Recovery

#### Finding Problem Shows
The system tracks all failures in both logs and a summary file:

```bash
# Find all failed shows in logs
grep "❌.*Failed to process" .review-processor/review-processor-*.log

# Check the JSON summary for detailed failure analysis
ls -t .review-processor/processing-summary-*.json | head -1 | xargs cat | jq '.failed_items.shows'
```

#### Reprocessing Failed Items
```bash
# Reprocess specific failed show with debug logging
python scripts/review-processor/review_processor.py "stage02-generated-data/shows/gd1993-05-16*" --verbose --clobber

# Process only shows that haven't been completed (automatic resume)
python scripts/review-processor/review_processor.py "stage02-generated-data/shows/gd1977-*"
```

### Monitoring and Analysis

#### Processing Statistics
Every run generates a comprehensive summary:
- Runtime and performance metrics
- Success/failure rates
- LLM API usage and costs
- Failed items with error details
- Processing timeline

#### Log Analysis Commands
```bash
# Show processing timeline
grep "Processing show\|✅ Completed" .review-processor/review-processor-*.log

# API usage statistics  
grep "LLM API call\|✅ Response received" .review-processor/review-processor-*.log | wc -l

# Error summary
grep "ERROR\|❌" .review-processor/review-processor-*.log | sort | uniq -c

# Performance analysis
grep "Response received in" .review-processor/review-processor-*.log | awk '{print $NF}' | sort -n
```

### Production Monitoring

For large-scale processing operations:

#### Health Monitoring
```bash
# Create a monitoring script
#!/bin/bash
LOG_FILE=".review-processor/review-processor-$(date +%Y-%m-%d).log"
ERROR_COUNT=$(grep -c "ERROR\|❌" "$LOG_FILE" 2>/dev/null || echo 0)
if [ "$ERROR_COUNT" -gt 10 ]; then
    echo "High error rate detected: $ERROR_COUNT errors"
    # Send alert, stop processing, etc.
fi
```

#### Resource Monitoring
- Monitor disk space for log files (automatic rotation handles this)
- Track LLM API usage and rate limits
- Watch memory usage for large batch jobs
- Monitor network connectivity for API providers

## Quick Start Guide

1. **Setup**: Follow the setup instructions above
2. **Configure**: Edit `config.json` with your LLM provider settings  
3. **Test**: Run a dry-run to verify file paths: `--dry-run`
4. **Process**: Start with a single show to verify everything works
5. **Scale**: Process larger collections once comfortable

For detailed architecture and configuration information, refer to the sections above.