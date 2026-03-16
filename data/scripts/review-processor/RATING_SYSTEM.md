# Review-Based Rating System

This document outlines the improved rating system that addresses issues with the current approach where shows receive inflated ratings despite negative feedback.

## Current Problems

1. **Rating Inflation**: Shows with negative feedback still receive 4+ star ratings
2. **Ignoring Negative Sentiment**: LLM focuses on highlights and ignores criticism across recording analyses  
3. **Recording Quality Confusion**: All recording quality issues affect show rating, when only the best recording matters
4. **Must-Listen Inflation**: Too many songs/sequences highlighted, reducing selectivity
5. **Review Count Ignored**: Shows with 100+ reviews should be weighted differently than shows with 3 reviews

## New Rating System Design

### Base Rating Principle
- **Start at 2.5 stars** (middle of 5-star scale)
- Build up from neutral baseline instead of assuming excellence

### Review Count Analysis (from analyze_review_counts.py)

Based on actual data percentiles across all shows:
- **10th percentile**: 13 reviews
- **25th percentile**: 19 reviews  
- **50th percentile**: 28 reviews
- **75th percentile**: 45 reviews
- **90th percentile**: 68 reviews
- **95th percentile**: 92 reviews
- **99th percentile**: 157 reviews

### Rating Boost Tiers (0.1 increments, max +0.5)

| Tier | Review Count | Boost | Description |
|------|--------------|-------|-------------|
| 1 | 5-19 reviews | +0.1 | Above minimal threshold |
| 2 | 20-28 reviews | +0.2 | Standard attention |
| 3 | 29-45 reviews | +0.3 | Notable attention |
| 4 | 46-92 reviews | +0.4 | High attention |
| 5 | 93+ reviews | +0.5 | Exceptional attention |

**Rationale**: More reviews = more important show = deserves rating boost if quality warrants it. High review count also means negative sentiment carries more weight.

## Must-Listen Sequence Selection

### Current Problem
- Shows where "everything is must-listen" dilute the concept
- Need selectivity based on cross-validation across reviews

### New Criteria

**Minimum Threshold**: 5+ total reviews across all recordings for show

**Selection Logic**:
```python
def qualify_for_must_listen(song_data, total_reviews):
    if total_reviews < 5:
        return False
        
    mention_rate = song_data["mention_rate"]  # % of reviews mentioning song
    sentiment_ratio = song_data["positive_mentions"] / song_data["total_mentions"]
    
    # Scale thresholds based on review count
    if total_reviews >= 50:
        return mention_rate > 0.4 and sentiment_ratio > 0.7  # Stricter for well-documented shows
    else:
        return mention_rate > 0.3 and sentiment_ratio > 0.6  # More lenient for sparse data
```

**Key Insight**: Base selection on **total review count**, not number of recordings. One recording with 50 reviews > five recordings with 2 reviews each.

## Sentiment Aggregation

### Recording Quality vs Show Quality
- **Recording Quality**: Only matters for the **best recording** recommendation
- **Show Quality**: Aggregate sentiment from **all recordings** affects the show rating

### Negative Sentiment Weighting
Currently missing - need to subtract points for:
- Multiple recordings mention "sloppy playing"
- "Uneven" or "mixed quality" noted across sources
- First/second set quality disparities
- Technical performance issues (not recording issues)

### Rating Calculation Formula
```
base_rating = 2.5
review_count_boost = calculate_tier_boost(total_reviews)  # 0.0 to 0.5
sentiment_adjustment = aggregate_sentiment_across_recordings()  # -1.0 to +2.0
final_rating = clamp(base_rating + review_count_boost + sentiment_adjustment, 1.0, 5.0)
```

## Song Mention Tracking

### Data Structure Needed
```json
{
  "song_mention_analysis": {
    "total_reviews": 51,
    "total_recordings": 2,
    "song_mentions": {
      "Dark Star": {
        "positive_mentions": 35,
        "negative_mentions": 5, 
        "total_mentions": 40,
        "mention_rate": 0.78,
        "sentiment_ratio": 0.875,
        "recordings_mentioning": ["rec1", "rec2"]
      }
    }
  }
}
```

### Implementation Steps
1. **Recording Analysis**: Extract song mentions from review text, track sentiment
2. **Show Aggregation**: Sum mentions across all recordings for the show
3. **Must-Listen Selection**: Apply thresholds based on mention rates and sentiment ratios

## Examples

### High-Attention Show (100+ reviews)
- Base: 2.5 stars
- Review boost: +0.5 (tier 5)  
- Must-listen threshold: 40%+ mention rate, 70%+ positive sentiment
- Negative sentiment heavily weighted due to sample size

### Low-Attention Show (8 reviews)
- Base: 2.5 stars
- Review boost: +0.1 (tier 1)
- Must-listen threshold: 30%+ mention rate, 60%+ positive sentiment  
- More lenient due to sparse data

### Mixed Sentiment Show
- Base: 2.5 stars
- Review boost: +0.3 (notable attention)
- Sentiment penalty: -0.4 (multiple sources mention problems)
- Final: 2.4 stars (properly reflects mixed quality)

## Implementation Priority

1. ✅ Create review count analysis script
2. ⏳ Update show review prompt with sentiment aggregation rules
3. ⏳ Implement song mention extraction in recording analysis
4. ⏳ Add review count boost logic to rating calculation
5. ⏳ Update must-listen sequence criteria
6. ⏳ Test on known problematic shows (1970-10-24, etc.)

## Known Test Cases

**1970-10-24 Kiel Opera House**:
- Recording analysis: 3.1 stars, "mixed sentiment", "poor recording quality"
- Current show rating: 4.5 stars (wrong!)
- Expected new rating: ~2.8 stars (2.5 base + minimal boost - negative sentiment)