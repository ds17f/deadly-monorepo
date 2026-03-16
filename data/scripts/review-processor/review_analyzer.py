#!/usr/bin/env python3
"""
Review Analysis Console Tool

Interactive tool for analyzing AI-generated reviews vs original fan reviews.
Allows browsing shows, comparing AI analysis with raw review data, and 
validating review quality across recordings.

Usage:
    python scripts/review-processor/review_analyzer.py
"""

import json
import sys
import termios
import tty
import subprocess
import tempfile
import difflib
from pathlib import Path
from typing import List, Dict, Any, Optional
from glob import glob

from rich.console import Console
from rich.panel import Panel
from rich.table import Table
from rich.text import Text
from rich.prompt import Prompt, IntPrompt, Confirm
from rich.columns import Columns
from rich.layout import Layout
from rich.live import Live
from rich.syntax import Syntax


class ReviewAnalyzer:
    """Console tool for analyzing AI reviews against source data."""
    
    def __init__(self):
        self.console = Console()
        self.shows_dir = Path("stage02-generated-data/shows")
        self.recordings_dir = Path("stage02-generated-data/recordings")
        self.archive_dir = Path("stage01-collected-data/archive")
        
    def run(self):
        """Main application loop."""
        self.console.print(Panel.fit(
            "🎵 [bold cyan]Grateful Dead Review Analyzer[/bold cyan] 🎵\n"
            "Compare AI-generated reviews with original fan reviews",
            style="cyan"
        ))
        
        while True:
            try:
                shows = self.select_shows()
                if not shows:
                    continue
                    
                if len(shows) == 1:
                    # Single show - go to menu
                    selected_show = self.pick_show(shows)
                    if not selected_show:
                        continue
                    self.show_main_menu(selected_show)
                else:
                    # Multiple shows - go directly to multi-show analysis
                    self.multi_show_analysis(shows)
                
            except KeyboardInterrupt:
                self.console.print("\n[yellow]👋 Goodbye![/yellow]")
                break
            except Exception as e:
                self.console.print(f"[red]❌ Error: {e}[/red]")
                continue
    
    def select_shows(self) -> List[Path]:
        """Allow user to select shows using wildcard patterns."""
        self.console.print("\n[bold green]📁 Show Selection[/bold green]")
        
        while True:
            pattern = Prompt.ask(
                "Enter show pattern (e.g., '1982-09-*', '1977-05-08*') or 'quit'",
                default="1982-*"
            )
            
            if pattern.lower() in ['quit', 'q', 'exit']:
                return []
                
            # Find matching show files
            search_pattern = str(self.shows_dir / f"{pattern}.json")
            matches = glob(search_pattern)
            
            if not matches:
                self.console.print(f"[yellow]⚠️  No shows found matching '{pattern}'[/yellow]")
                continue
                
            shows = [Path(match) for match in sorted(matches)]
            self.console.print(f"[green]✅ Found {len(shows)} matching shows[/green]")
            return shows
    
    def pick_show(self, shows: List[Path]) -> Optional[Path]:
        """Display shows list and let user pick one."""
        if len(shows) == 1:
            return shows[0]
            
        self.console.print(f"\n[bold blue]📋 Select Show ({len(shows)} found)[/bold blue]")
        
        # Create table of shows
        table = Table(show_header=True, header_style="bold magenta")
        table.add_column("#", style="dim", width=3)
        table.add_column("Date", style="cyan")
        table.add_column("Venue", style="green")
        table.add_column("AI Rating", style="yellow")
        
        show_data = []
        for i, show_path in enumerate(shows, 1):
            try:
                with open(show_path) as f:
                    data = json.load(f)
                    
                ai_review = data.get('ai_show_review', {})
                ai_rating = ai_review.get('ratings', {}).get('ai_rating', 'N/A')
                confidence = ai_review.get('ratings', {}).get('confidence', '')
                
                # Parse show name for display
                show_name = show_path.stem
                parts = show_name.split('-')
                date = '-'.join(parts[:3]) if len(parts) >= 3 else show_name
                venue = ' '.join(parts[3:]).replace('-', ' ').title() if len(parts) > 3 else "Unknown"
                
                rating_str = f"⭐ {ai_rating}" + (f" ({confidence})" if confidence else "") if ai_rating != 'N/A' else "No AI Review"
                
                table.add_row(str(i), date, venue, rating_str)
                show_data.append(show_path)
                
            except Exception as e:
                table.add_row(str(i), "Error", str(e), "Failed to load")
                show_data.append(show_path)
        
        self.console.print(table)
        
        while True:
            try:
                choice = IntPrompt.ask(
                    f"Select show (1-{len(shows)}) or 0 to go back",
                    default=1
                )
                
                if choice == 0:
                    return None
                    
                if 1 <= choice <= len(shows):
                    return show_data[choice - 1]
                    
                self.console.print(f"[red]Please enter a number between 1 and {len(shows)}[/red]")
                
            except (ValueError, KeyboardInterrupt):
                return None
    
    def show_main_menu(self, show_path: Path):
        """Display main menu for selected show."""
        # Load show data
        try:
            with open(show_path) as f:
                show_data = json.load(f)
        except Exception as e:
            self.console.print(f"[red]❌ Failed to load show data: {e}[/red]")
            return
            
        show_name = show_path.stem.replace('-', ' ').title()
        
        while True:
            self.console.print(f"\n[bold cyan]🎵 {show_name}[/bold cyan]")
            
            # Show quick stats
            ai_review = show_data.get('ai_show_review', {})
            recordings = show_data.get('recordings', [])
            
            if ai_review:
                summary = ai_review.get('summary', 'No summary')
                ai_rating = ai_review.get('ratings', {}).get('ai_rating', 'N/A')
                confidence = ai_review.get('ratings', {}).get('confidence', '')
                
                self.console.print(Panel(
                    f"[bold]Summary:[/bold] {summary}\n"
                    f"[bold]AI Rating:[/bold] ⭐ {ai_rating} " + (f"({confidence} confidence)" if confidence else "") + f"\n"
                    f"[bold]Recordings:[/bold] {len(recordings)} available",
                    title="Show Info",
                    style="blue"
                ))
            
            # Menu options
            self.console.print("\n[bold green]📋 Analysis Options[/bold green]")
            options = [
                "1. Show AI Review vs Recording Evidence (DEFAULT)",
                "2. View Show AI Review Only",
                "3. View All Recording Reviews", 
                "4. Compare Recording AI vs Raw Reviews",
                "5. View Raw Reviews for Recording",
                "6. Refine AI Review Language (Fix Repetitive Terms)",
                "0. Back to Show Selection"
            ]
            
            for option in options:
                self.console.print(f"  {option}")
            
            try:
                choice = IntPrompt.ask("Select option", default=1)
                
                if choice == 0:
                    break
                elif choice == 1:
                    # Go directly to show vs recording evidence with "all"
                    self.interactive_show_vs_all_recordings(show_data, recordings)
                elif choice == 2:
                    self.display_show_review(show_data)
                elif choice == 3:
                    self.display_recording_reviews(recordings)
                elif choice == 4:
                    self.compare_recording_reviews(recordings)
                elif choice == 5:
                    self.display_raw_reviews(recordings)
                elif choice == 6:
                    self.refine_show_review(show_path, show_data)
                else:
                    self.console.print("[red]Invalid option[/red]")
                    
            except (ValueError, KeyboardInterrupt):
                break
    
    def multi_show_analysis(self, shows: List[Path]):
        """Multi-show analysis - jumps directly to show vs recording evidence view."""
        if not shows:
            return
            
        current_show_index = 0
        show_scroll_position = 0  # Track scroll position across recordings
        
        # Add batch refinement option
        self.console.print(Panel.fit(
            "📋 [bold cyan]Multi-Show Analysis Mode[/bold cyan]\n"
            "Press [bold yellow]B[/bold yellow] at any time during navigation for batch refinement",
            style="cyan"
        ))
        
        while current_show_index < len(shows):
            show_path = shows[current_show_index]
            
            try:
                # Load show data
                with open(show_path) as f:
                    show_data = json.load(f)
                    
                recordings = show_data.get('recordings', [])
                if not recordings:
                    self.console.print(f"[yellow]⚠️  No recordings in {show_path.name}, skipping...[/yellow]")
                    current_show_index += 1
                    continue
                
                # Start the multi-show comparison
                result = self.multi_show_vs_recordings_analysis(
                    show_data, 
                    recordings, 
                    current_show_index + 1, 
                    len(shows),
                    show_path.name,
                    shows  # Pass all shows for batch refinement
                )
                
                if result == "next_show":
                    current_show_index += 1
                elif result == "prev_show":
                    current_show_index = max(0, current_show_index - 1)
                    # Continue to load the new show
                    continue
                elif result == "quit":
                    break
                elif result == "jump_show":
                    try:
                        jump_to = IntPrompt.ask(
                            f"Jump to show (1-{len(shows)})",
                            default=current_show_index + 1
                        )
                        if 1 <= jump_to <= len(shows):
                            current_show_index = jump_to - 1
                            # Continue to load the jumped-to show
                            continue
                    except (ValueError, KeyboardInterrupt):
                        continue
                        
            except Exception as e:
                self.console.print(f"[red]❌ Error loading {show_path.name}: {e}[/red]")
                current_show_index += 1
                continue
    
    def multi_show_vs_recordings_analysis(self, show_data: Dict[str, Any], recording_ids: List[str], 
                                        show_current: int, show_total: int, show_name: str, 
                                        all_shows: List[Path]) -> str:
        """Multi-show analysis with show-level and recording-level navigation."""
        current_recording_index = 0
        show_scroll_position = 0  # Initialize scroll position tracking
        
        while current_recording_index < len(recording_ids):
            recording_id = recording_ids[current_recording_index]
            
            result, show_scroll_position = self.enhanced_show_vs_recording_comparison(
                show_data,
                recording_id, 
                current_recording_index + 1, 
                len(recording_ids),
                show_current,
                show_total,
                show_name,
                show_scroll_position
            )
            
            # Recording-level navigation (N/P/X)
            if result == "next_recording":
                current_recording_index += 1
            elif result == "prev_recording":
                current_recording_index = max(0, current_recording_index - 1)
            elif result == "jump_recording":
                try:
                    jump_to = IntPrompt.ask(
                        f"Jump to recording (1-{len(recording_ids)})",
                        default=current_recording_index + 1
                    )
                    if 1 <= jump_to <= len(recording_ids):
                        current_recording_index = jump_to - 1
                except (ValueError, KeyboardInterrupt):
                    continue
            elif result == "stay":
                # Stay on current recording (after refinement)
                continue
            elif result == "batch_refine":
                # Handle batch refinement - clear screen and start batch process
                self.console.print(f"[yellow]DEBUG: Multi-show function got 'batch_refine' result[/yellow]")
                self.console.clear()
                self.console.print("\n[bold yellow]🔧 Starting Batch Refinement[/bold yellow]")
                
                try:
                    instruction = Prompt.ask(
                        "Enter refinement instruction for all shows",
                        default="Replace overused terms like 'high energy', 'monster', 'fire', 'scorching', 'tight' with more varied and specific language."
                    )
                    
                    if Confirm.ask(f"Apply refinement to all {len(all_shows)} shows?"):
                        self.batch_refine_reviews(all_shows, instruction)
                except Exception as e:
                    self.console.print(f"[red]❌ Batch refinement error: {e}[/red]")
                    input("Press Enter to continue...")
                
                # Continue with current show after batch refinement
                continue
                    
            # Show-level navigation (Shift+N/P/X)
            elif result == "next_show":
                return "next_show"
            elif result == "prev_show":
                return "prev_show"
            elif result == "jump_show":
                return "jump_show"
            elif result == "quit":
                return "quit"
        
        # Finished all recordings in this show, automatically go to next show
        return "next_show"
    
    def display_show_review(self, show_data: Dict[str, Any]):
        """Display comprehensive show AI review."""
        ai_review = show_data.get('ai_show_review', {})
        if not ai_review:
            self.console.print("[yellow]⚠️  No AI review available for this show[/yellow]")
            return
            
        self.console.print("\n" + "="*80)
        self.console.print(Panel.fit(
            "🎭 [bold cyan]Show AI Review[/bold cyan]",
            style="cyan"
        ))
        
        # Summary and ratings
        summary = ai_review.get('summary', 'No summary available')
        review = ai_review.get('review', 'No review available')
        ratings = ai_review.get('ratings', {})
        
        ai_rating = ratings.get('ai_rating', 'N/A')
        confidence = ratings.get('confidence', '')
        avg_rating = ratings.get('average_rating', 0)
        
        self.console.print(Panel(
            f"[bold yellow]{summary}[/bold yellow]",
            title="Summary",
            style="yellow"
        ))
        
        # Ratings panel
        rating_text = f"⭐ AI Rating: [bold]{ai_rating}[/bold]"
        if confidence:
            rating_text += f" ([italic]{confidence} confidence[/italic])"
        if avg_rating:
            rating_text += f"\n📊 Average User Rating: [bold]{avg_rating:.1f}[/bold]"
            
        self.console.print(Panel(
            rating_text,
            title="Ratings",
            style="green"
        ))
        
        # Full review
        self.console.print(Panel(
            review,
            title="Full Review", 
            style="blue"
        ))
        
        # Key highlights
        highlights = ai_review.get('key_highlights', [])
        if highlights:
            highlight_text = "\n".join([f"• {highlight}" for highlight in highlights])
            self.console.print(Panel(
                highlight_text,
                title="Key Highlights",
                style="magenta"
            ))
        
        # Best recording
        best_recording = ai_review.get('best_recording', {})
        if best_recording:
            identifier = best_recording.get('identifier', 'Unknown')
            reason = best_recording.get('reason', 'No reason provided')
            
            self.console.print(Panel(
                f"[bold]Recording:[/bold] {identifier}\n[bold]Reason:[/bold] {reason}",
                title="Best Recording Recommendation",
                style="cyan"
            ))
        
        # Band performance
        band_performance = ai_review.get('band_performance', {})
        if band_performance:
            band_table = Table(title="Band Performance Analysis")
            band_table.add_column("Member", style="cyan", width=8)
            band_table.add_column("Analysis", style="white")
            
            for member, analysis in band_performance.items():
                if analysis.strip():  # Only show non-empty analyses
                    band_table.add_row(member, analysis)
            
            if band_table.row_count > 0:
                self.console.print(band_table)
        
        input("\nPress Enter to continue...")
    
    def display_recording_reviews(self, recording_ids: List[str]):
        """Display list of all recording AI reviews with interactive selection."""
        if not recording_ids:
            self.console.print("[yellow]⚠️  No recordings found for this show[/yellow]")
            return
            
        while True:
            self.console.print("\n" + "="*80)
            self.console.print(Panel.fit(
                f"🎧 [bold cyan]Recording AI Reviews ({len(recording_ids)} recordings)[/bold cyan]",
                style="cyan"
            ))
            
            table = Table(show_header=True, header_style="bold magenta")
            table.add_column("#", style="dim", width=3)
            table.add_column("Recording ID", style="cyan", max_width=40)
            table.add_column("AI Rating", style="yellow", width=15)
            table.add_column("Summary", style="white")
            
            for i, recording_id in enumerate(recording_ids, 1):
                recording_path = self.recordings_dir / f"{recording_id}.json"
                
                try:
                    with open(recording_path) as f:
                        recording_data = json.load(f)
                        
                    ai_review = recording_data.get('ai_review', {})
                    if ai_review:
                        ai_rating = ai_review.get('ai_rating', {})
                        stars = ai_rating.get('stars', 'N/A')
                        confidence = ai_rating.get('confidence', '')
                        summary = ai_review.get('summary', 'No summary')
                        
                        rating_str = f"⭐ {stars}"
                        if confidence:
                            rating_str += f" ({confidence})"
                    else:
                        rating_str = "No AI Review"
                        summary = "No analysis available"
                        
                except FileNotFoundError:
                    rating_str = "File Not Found"
                    summary = "Recording file missing"
                except Exception as e:
                    rating_str = "Error"
                    summary = f"Failed to load: {str(e)[:30]}..."
                
                # Truncate recording ID for display
                display_id = recording_id if len(recording_id) <= 40 else recording_id[:37] + "..."
                
                table.add_row(str(i), display_id, rating_str, summary)
            
            self.console.print(table)
            
            # Interactive menu
            self.console.print("\n[bold green]Options:[/bold green]")
            self.console.print("  Enter a number (1-{}) to view raw reviews for that recording".format(len(recording_ids)))
            self.console.print("  Enter 'c' to compare AI vs raw reviews for a recording")
            self.console.print("  Enter 'q' to go back to main menu")
            
            try:
                choice = Prompt.ask("Your choice", default="q")
                
                if choice.lower() in ['q', 'quit', 'back']:
                    break
                elif choice.lower() in ['c', 'compare']:
                    recording_id = self.select_recording(recording_ids, "Compare AI vs Raw Reviews")
                    if recording_id:
                        self.display_recording_comparison(recording_id)
                else:
                    # Try to parse as number
                    try:
                        num = int(choice)
                        if 1 <= num <= len(recording_ids):
                            recording_id = recording_ids[num - 1]
                            self.display_raw_reviews_for_recording(recording_id)
                        else:
                            self.console.print(f"[red]Please enter a number between 1 and {len(recording_ids)}[/red]")
                    except ValueError:
                        self.console.print("[red]Invalid input. Please enter a number, 'c' for compare, or 'q' to quit.[/red]")
                        
            except KeyboardInterrupt:
                break
    
    def compare_show_vs_recordings(self, show_data: Dict[str, Any], recording_ids: List[str]):
        """Compare show AI review with individual recording evidence."""
        if not recording_ids:
            self.console.print("[yellow]⚠️  No recordings found for this show[/yellow]")
            return
            
        recording_choice = self.select_recording(recording_ids, "Show vs Recording Evidence")
        if not recording_choice:
            return
            
        if recording_choice == "ALL":
            self.interactive_show_vs_all_recordings(show_data, recording_ids)
        else:
            self.interactive_show_vs_recording(show_data, recording_choice)
    
    def interactive_show_vs_all_recordings(self, show_data: Dict[str, Any], recording_ids: List[str]):
        """Step through all recordings comparing show review vs recording evidence."""
        current_index = 0
        
        while current_index < len(recording_ids):
            recording_id = recording_ids[current_index]
            result = self.interactive_show_vs_recording_comparison(
                show_data,
                recording_id, 
                current_index + 1, 
                len(recording_ids)
            )
            
            if result == "next":
                current_index += 1
            elif result == "prev":
                current_index = max(0, current_index - 1)
            elif result == "quit":
                break
            elif result == "jump":
                try:
                    jump_to = IntPrompt.ask(
                        f"Jump to recording (1-{len(recording_ids)})",
                        default=current_index + 1
                    )
                    if 1 <= jump_to <= len(recording_ids):
                        current_index = jump_to - 1
                except (ValueError, KeyboardInterrupt):
                    continue
            elif result == "refine":
                # Handle individual show refinement
                self.console.print(f"[yellow]DEBUG: Got 'refine' in default view[/yellow]")
                show_id = show_data.get('show_id', 'unknown')
                show_path = Path(f"stage02-generated-data/shows/{show_id}.json")
                try:
                    self.refine_show_review(show_path, show_data)
                except Exception as e:
                    self.console.print(f"[red]❌ Refinement error: {e}[/red]")
                    input("Press Enter to continue...")
                # Stay on current recording
                continue
            elif result == "batch_refine":
                # Handle batch refinement - not really applicable in single show view
                self.console.print(f"[yellow]DEBUG: Got 'batch_refine' in default view - not applicable[/yellow]")
                self.console.print("[yellow]⚠️  Batch refinement only available in multi-show mode[/yellow]")
                input("Press Enter to continue...")
                continue
    
    def interactive_show_vs_recording_comparison(self, show_data: Dict[str, Any], recording_id: str, current: int, total: int) -> str:
        """Show interactive comparison between show AI review and recording evidence."""
        # Load recording data
        recording_path = self.recordings_dir / f"{recording_id}.json"
        archive_path = self.archive_dir / f"{recording_id}.json"
        
        try:
            # Load AI review for recording
            recording_ai_data = {}
            if recording_path.exists():
                with open(recording_path) as f:
                    recording_data = json.load(f)
                    recording_ai_data = recording_data.get('ai_review', {})
            
            # Load raw reviews for recording
            raw_reviews = []
            if archive_path.exists():
                with open(archive_path) as f:
                    archive_data = json.load(f)
                    raw_reviews = archive_data.get('raw_reviews', [])
        except Exception as e:
            self.console.print(f"[red]❌ Error loading recording data: {e}[/red]")
            return "next"
        
        # Initialize scroll positions
        show_scroll = 0
        recording_scroll = 0
        
        # Prepare display data
        show_lines = self.format_comprehensive_show_review(show_data)
        recording_lines = self.format_recording_evidence(recording_ai_data, raw_reviews, recording_id)
        
        layout = Layout()
        layout.split_row(
            Layout(name="show", ratio=1),
            Layout(name="recording", ratio=1)
        )
        
        # Initialize display once
        def update_display():
            viewport_height = self.get_viewport_height()
            show_viewport = self.create_viewport(show_lines, show_scroll, viewport_height)
            recording_viewport = self.create_viewport(recording_lines, recording_scroll, viewport_height)

            header = f"Recording {current}/{total}: {recording_id[:50]}{'...' if len(recording_id) > 50 else ''}"

            layout["show"].update(Panel(
                show_viewport,
                title=f"🎭 Show AI Review (D/F to scroll) [{show_scroll+1}-{min(show_scroll+viewport_height, len(show_lines))}/{len(show_lines)}]",
                style="blue",
                subtitle=f"Show: {show_data.get('date', 'Unknown Date')}",
                border_style="blue"
            ))
            
            layout["recording"].update(Panel(
                recording_viewport,
                title=f"🎧 Recording Evidence (J/K to scroll) [{recording_scroll+1}-{min(recording_scroll+viewport_height, len(recording_lines))}/{len(recording_lines)}]",
                style="green",
                subtitle="Controls: H/L=recordings, S/G=shows, D/F=scroll left, J/K=scroll right, R=refine, B=batch, Q=quit",
                border_style="green"
            ))

        # Main interaction loop
        with Live(layout, console=self.console, refresh_per_second=2, auto_refresh=False) as live:
            update_display()
            live.refresh()
            
            while True:
                # Get single key input
                key = self.get_key()
                
                if key.lower() == 'q':
                    return "quit"
                elif key.lower() == 'h':  # prev recording
                    return "prev"
                elif key.lower() == 'l':  # next recording
                    return "next" 
                elif key.lower() == 's':  # prev show
                    return "prev"
                elif key.lower() == 'g':  # next show  
                    return "next"
                elif key.lower() == 'x':  # Jump to specific recording
                    return "jump"
                elif key.lower() == 'r':  # Refine review language
                    # Exit the Live context before showing debug
                    live.stop()
                    self.console.print(f"[yellow]DEBUG: R key pressed - returning 'refine'[/yellow]")
                    input("Press Enter to continue...")
                    return "refine"
                elif key.lower() == 'b':  # Batch refinement
                    # Exit the Live context before showing debug
                    live.stop()
                    self.console.print(f"[yellow]DEBUG: B key pressed - returning 'batch_refine'[/yellow]")
                    input("Press Enter to continue...")
                    return "batch_refine"
                elif key.lower() == 'd':  # scroll left panel (show review) UP
                    old_scroll = show_scroll
                    show_scroll = max(0, show_scroll - 1)
                    if show_scroll != old_scroll:
                        update_display()
                        live.refresh()
                elif key.lower() == 'f':  # scroll left panel (show review) DOWN
                    old_scroll = show_scroll
                    viewport_height = self.get_viewport_height()
                    show_scroll = min(show_scroll + 1, max(0, len(show_lines) - viewport_height))
                    if show_scroll != old_scroll:
                        update_display()
                        live.refresh()
                elif key.lower() == 'j':  # scroll right panel (recording evidence) DOWN
                    old_scroll = recording_scroll
                    viewport_height = self.get_viewport_height()
                    recording_scroll = min(recording_scroll + 1, max(0, len(recording_lines) - viewport_height))
                    if recording_scroll != old_scroll:
                        update_display()
                        live.refresh()
                elif key.lower() == 'k':  # scroll right panel (recording evidence) UP
                    old_scroll = recording_scroll
                    recording_scroll = max(0, recording_scroll - 1)
                    if recording_scroll != old_scroll:
                        update_display()
                        live.refresh()
                else:
                    # Debug: show what key was actually pressed
                    live.stop()
                    self.console.print(f"[yellow]DEBUG: Unhandled key pressed: '{key}' (ASCII: {ord(key) if len(key) == 1 else 'multi-char'})[/yellow]")
                    input("Press Enter to continue...")
                    return "quit"
    
    def interactive_show_vs_recording(self, show_data: Dict[str, Any], recording_id: str):
        """Single recording comparison with show review."""
        self.interactive_show_vs_recording_comparison(show_data, recording_id, 1, 1)
    
    def format_comprehensive_show_review(self, show_data: Dict[str, Any]) -> List[str]:
        """Format complete show AI review data for scrollable display."""
        lines = []
        
        ai_show_review = show_data.get('ai_show_review', {})
        if not ai_show_review:
            lines.append("[yellow]No show AI review available[/yellow]")
            return lines
        
        # Show header
        show_name = show_data.get('date', 'Unknown Date')
        venue = show_data.get('venue', 'Unknown Venue')
        lines.append(f"[bold cyan]Show:[/bold cyan] {show_name}")
        lines.append(f"[bold cyan]Venue:[/bold cyan] {venue}")
        lines.append("")
        
        # Show ratings
        ratings = ai_show_review.get('ratings', {})
        ai_rating = ratings.get('ai_rating', 'N/A')
        confidence = ratings.get('confidence', '')
        avg_rating = ratings.get('average_rating', 0)
        
        lines.append(f"[bold yellow]⭐ Show AI Rating:[/bold yellow] {ai_rating}")
        if confidence:
            lines.append(f"[bold yellow]Confidence:[/bold yellow] {confidence}")
        if avg_rating:
            lines.append(f"[bold yellow]Average User Rating:[/bold yellow] {avg_rating:.1f}")
        lines.append("")
        
        # Rating calculation breakdown
        rating_calc = ratings.get('rating_calculation', {})
        if rating_calc:
            lines.append(f"[bold cyan]🧮 Rating Calculation:[/bold cyan]")
            base = rating_calc.get('base_rating', 2.5)
            total_reviews = rating_calc.get('review_count_total', 0)
            boost = rating_calc.get('review_count_boost', 0.0)
            sentiment = rating_calc.get('sentiment_adjustment', 0.0)
            calculation = rating_calc.get('final_calculation', '')
            
            lines.append(f"  Base Rating: [cyan]{base}[/cyan]")
            lines.append(f"  Total Reviews: [cyan]{total_reviews}[/cyan]")
            lines.append(f"  Review Count Boost: [green]+{boost}[/green]")
            lines.append(f"  Sentiment Adjustment: [yellow]{sentiment:+.1f}[/yellow]")
            lines.append(f"  [bold]Final: {calculation}[/bold]")
            
            # Sentiment rationale
            rationale = rating_calc.get('sentiment_rationale', '')
            if rationale:
                lines.append(f"[bold cyan]💭 Sentiment Rationale:[/bold cyan]")
                lines.extend(self.wrap_text(rationale, 100))
            lines.append("")
        
        # Summary
        summary = ai_show_review.get('summary', '')
        if summary:
            lines.append(f"[bold cyan]Summary:[/bold cyan]")
            lines.extend(self.wrap_text(summary, 100))
            lines.append("")
        
        # Blurb
        blurb = ai_show_review.get('blurb', '')
        if blurb:
            lines.append(f"[bold green]📝 Key Details:[/bold green]")
            lines.extend(self.wrap_text(blurb, 100))
            lines.append("")
        
        # Song highlights
        song_highlights = ai_show_review.get('song_highlights', [])
        if song_highlights:
            lines.append(f"[bold magenta]🎵 Standout Songs:[/bold magenta]")
            # Format song names in columns for better space usage
            songs_per_row = 2
            for i in range(0, len(song_highlights), songs_per_row):
                row_songs = song_highlights[i:i+songs_per_row]
                if len(row_songs) == 2:
                    lines.append(f"  • {row_songs[0]:<25} • {row_songs[1]}")
                else:
                    lines.append(f"  • {row_songs[0]}")
            lines.append("")
        
        # Must-listen sequences
        must_listen_sequences = ai_show_review.get('must_listen_sequences', [])
        if must_listen_sequences:
            lines.append(f"[bold red]🎧 Must-Listen Sequences:[/bold red]")
            for i, sequence in enumerate(must_listen_sequences, 1):
                if len(sequence) == 1:
                    # Single song
                    lines.append(f"  {i}. {sequence[0]}")
                else:
                    # Multi-song sequence
                    sequence_text = " > ".join(sequence)
                    # Wrap long sequences nicely
                    if len(sequence_text) <= 90:
                        lines.append(f"  {i}. {sequence_text}")
                    else:
                        lines.append(f"  {i}. {sequence[0]} >")
                        current_line = "     "
                        for song in sequence[1:]:
                            if len(current_line + song + " > ") <= 90:
                                if current_line == "     ":
                                    current_line += song
                                else:
                                    current_line += " > " + song
                            else:
                                lines.append(current_line + " >")
                                current_line = "     " + song
                        lines.append(current_line)
            lines.append("")
        
        # Key highlights
        highlights = ai_show_review.get('key_highlights', [])
        if highlights:
            lines.append(f"[bold magenta]🎯 Key Highlights:[/bold magenta]")
            for highlight in highlights:
                lines.append(f"  • {highlight}")
            lines.append("")
        
        # Best recording
        best_recording = ai_show_review.get('best_recording', {})
        if best_recording:
            identifier = best_recording.get('identifier', 'Unknown')
            reason = best_recording.get('reason', 'No reason provided')
            lines.append(f"[bold green]🏆 Best Recording:[/bold green]")
            lines.append(f"  {identifier}")
            lines.append(f"  Reason: {reason}")
            lines.append("")
        
        # Band performance analysis
        band_performance = ai_show_review.get('band_performance', {})
        if band_performance and any(v.strip() for v in band_performance.values()):
            lines.append(f"[bold purple]🎸 Show-Level Band Analysis:[/bold purple]")
            for member, analysis in band_performance.items():
                if analysis and analysis.strip():
                    lines.append(f"  [bold]{member}:[/bold]")
                    lines.extend(["    " + line for line in self.wrap_text(analysis, 96)])
                    lines.append("")
        
        # Full review at the bottom
        review = ai_show_review.get('review', '')
        if review:
            lines.append(f"[bold blue]📖 Full Show Review:[/bold blue]")
            lines.extend(self.wrap_text(review, 100))
            lines.append("")
        
        return lines
    
    def format_recording_evidence(self, ai_data: Dict[str, Any], raw_reviews: List[Dict[str, Any]], recording_id: str) -> List[str]:
        """Format recording AI analysis + raw reviews as evidence for show-level decisions."""
        lines = []
        
        lines.append(f"[bold cyan]Recording:[/bold cyan] {recording_id}")
        lines.append("")
        
        # AI Analysis Section
        lines.append("[bold yellow]🤖 RECORDING AI ANALYSIS[/bold yellow]")
        lines.append("─" * 50)
        
        if ai_data:
            # AI Rating
            ai_rating = ai_data.get('ai_rating', {})
            stars = ai_rating.get('stars', 'N/A')
            confidence = ai_rating.get('confidence', '')
            
            lines.append(f"⭐ Recording AI Rating: {stars} ({confidence} confidence)")
            
            # Summary
            summary = ai_data.get('summary', '')
            if summary:
                lines.append(f"Summary: {summary}")
            
            # Recording quality assessment
            recording_quality = ai_data.get('recording_quality', {})
            if recording_quality:
                source_type = recording_quality.get('source_type', 'unknown')
                quality_rating = recording_quality.get('quality_rating', 'unknown')
                lines.append(f"Source: {source_type} | Quality: {quality_rating}")
            
            # Show quality assessment from this recording
            show_quality = ai_data.get('show_quality', {})
            if show_quality:
                energy = show_quality.get('energy_level', 'unknown')
                lines.append(f"Energy Level: {energy}")
                
                standout_songs = show_quality.get('standout_songs', [])
                if standout_songs:
                    lines.append(f"Standout Songs: {', '.join(standout_songs)}")
        else:
            lines.append("[yellow]No AI analysis available[/yellow]")
        
        lines.append("")
        lines.append("")
        
        # Raw Reviews Section
        lines.append("[bold green]👥 RAW FAN REVIEWS (EVIDENCE)[/bold green]")
        lines.append("─" * 50)
        
        if raw_reviews:
            # Statistics
            total_reviews = len(raw_reviews)
            valid_ratings = []
            for r in raw_reviews:
                stars = r.get('stars', 0)
                try:
                    if isinstance(stars, str):
                        stars = float(stars) if stars else 0
                    elif isinstance(stars, (int, float)):
                        stars = float(stars)
                    else:
                        stars = 0
                    valid_ratings.append(stars)
                except (ValueError, TypeError):
                    valid_ratings.append(0)
            
            avg_rating = sum(valid_ratings) / total_reviews if total_reviews > 0 else 0
            
            lines.append(f"Total Reviews: {total_reviews} | Average: ⭐ {avg_rating:.1f}")
            lines.append("")
            
            # Individual reviews
            for i, review in enumerate(raw_reviews, 1):
                stars = valid_ratings[i-1] if i-1 < len(valid_ratings) else 0
                reviewer = review.get('reviewer', 'Anonymous')
                date = review.get('reviewdate', '')
                body = review.get('reviewbody', 'No review text')
                
                header = f"Review #{i} - {reviewer}"
                if date:
                    header += f" ({date})"
                header += f" - ⭐ {stars:.1f}"
                
                lines.append(f"[bold]{header}[/bold]")
                lines.extend(self.wrap_text(body, 100))
                lines.append("")
                lines.append("")
        else:
            lines.append("[yellow]No raw reviews available[/yellow]")
        
        return lines
    
    def enhanced_show_vs_recording_comparison(self, show_data: Dict[str, Any], recording_id: str, 
                                           recording_current: int, recording_total: int,
                                           show_current: int, show_total: int, show_name: str, 
                                           initial_show_scroll: int = 0) -> tuple[str, int]:
        """Enhanced comparison with dual navigation - shows and recordings."""
        # Load recording data
        recording_path = self.recordings_dir / f"{recording_id}.json"
        archive_path = self.archive_dir / f"{recording_id}.json"
        
        try:
            # Load AI review for recording
            recording_ai_data = {}
            if recording_path.exists():
                with open(recording_path) as f:
                    recording_data = json.load(f)
                    recording_ai_data = recording_data.get('ai_review', {})
            
            # Load raw reviews for recording
            raw_reviews = []
            if archive_path.exists():
                with open(archive_path) as f:
                    archive_data = json.load(f)
                    raw_reviews = archive_data.get('raw_reviews', [])
        except Exception as e:
            self.console.print(f"[red]❌ Error loading recording data: {e}[/red]")
            return "next_recording", initial_show_scroll
        
        # Initialize scroll positions
        show_scroll = initial_show_scroll  # Preserve show scroll position
        recording_scroll = 0
        
        # Prepare display data
        show_lines = self.format_comprehensive_show_review(show_data)
        recording_lines = self.format_recording_evidence(recording_ai_data, raw_reviews, recording_id)
        
        layout = Layout()
        layout.split_row(
            Layout(name="show", ratio=1),
            Layout(name="recording", ratio=1)
        )
        
        # Initialize display once
        def update_display():
            viewport_height = self.get_viewport_height()
            show_viewport = self.create_viewport(show_lines, show_scroll, viewport_height)
            recording_viewport = self.create_viewport(recording_lines, recording_scroll, viewport_height)

            show_header = f"Show {show_current}/{show_total}: {show_name.replace('.json', '').replace('-', ' ')}"
            recording_header = f"Recording {recording_current}/{recording_total}: {recording_id[:40]}{'...' if len(recording_id) > 40 else ''}"

            layout["show"].update(Panel(
                show_viewport,
                title=f"🎭 Show AI Review (D/F=scroll, S/G=shows, Shift+X=jump show) [{show_scroll+1}-{min(show_scroll+viewport_height, len(show_lines))}/{len(show_lines)}]",
                style="blue",
                subtitle=show_header,
                border_style="blue"
            ))
            
            layout["recording"].update(Panel(
                recording_viewport,
                title=f"🎧 Recording Evidence (J/K=scroll, H/L=recordings, X=jump) [{recording_scroll+1}-{min(recording_scroll+viewport_height, len(recording_lines))}/{len(recording_lines)}]",
                style="green",
                subtitle=recording_header,
                border_style="green"
            ))

        # Main interaction loop
        with Live(layout, console=self.console, refresh_per_second=2, auto_refresh=False) as live:
            update_display()
            live.refresh()
            
            while True:
                # Get single key input
                key = self.get_key()
                
                if key.lower() == 'q':
                    return "quit", show_scroll
                    
                # Recording-level navigation (h/l keys)
                elif key.lower() == 'h':
                    return "prev_recording", show_scroll
                elif key.lower() == 'l':
                    return "next_recording", show_scroll
                elif key.lower() == 'x':
                    return "jump_recording", show_scroll
                    
                # Show-level navigation (s/g keys)
                elif key.lower() == 's':
                    return "prev_show", show_scroll
                elif key.lower() == 'g':
                    return "next_show", show_scroll
                elif key == 'X':  # Capital X for show jumping
                    return "jump_show", show_scroll
                    
                # Scrolling controls - d/f for left panel (show review)
                elif key.lower() == 'd':  # scroll left panel (show review) UP
                    old_scroll = show_scroll
                    show_scroll = max(0, show_scroll - 1)
                    if show_scroll != old_scroll:
                        update_display()
                        live.refresh()
                elif key.lower() == 'f':  # scroll left panel (show review) DOWN
                    old_scroll = show_scroll
                    viewport_height = self.get_viewport_height()
                    show_scroll = min(show_scroll + 1, max(0, len(show_lines) - viewport_height))
                    if show_scroll != old_scroll:
                        update_display()
                        live.refresh()
                elif key.lower() == 'j':  # scroll right panel (recording evidence) DOWN
                    old_scroll = recording_scroll
                    viewport_height = self.get_viewport_height()
                    recording_scroll = min(recording_scroll + 1, max(0, len(recording_lines) - viewport_height))
                    if recording_scroll != old_scroll:
                        update_display()
                        live.refresh()
                elif key.lower() == 'k':  # scroll right panel (recording evidence) UP
                    old_scroll = recording_scroll
                    recording_scroll = max(0, recording_scroll - 1)
                    if recording_scroll != old_scroll:
                        update_display()
                        live.refresh()
                elif key.lower() == 'b':  # Batch refinement
                    # Exit the Live context before batch refinement
                    live.stop()
                    return "batch_refine", show_scroll
                elif key.lower() == 'r':  # Refine review language
                    # Exit the Live context before refinement
                    live.stop()
                    
                    # Handle refinement request
                    self.console.print(f"[yellow]DEBUG: Enhanced function handling refinement[/yellow]")
                    # Extract show path from show_name - construct proper filename
                    if not show_name.endswith('.json'):
                        show_path = Path(f"stage02-generated-data/shows/{show_name}.json")
                    else:
                        show_path = Path(f"stage02-generated-data/shows/{show_name}")
                    
                    try:
                        self.refine_show_review(show_path, show_data)
                    except Exception as e:
                        self.console.print(f"[red]❌ Refinement error: {e}[/red]")
                        input("Press Enter to continue...")
                    # Continue with current view after refinement
                    return "stay", show_scroll

    def compare_recording_reviews(self, recording_ids: List[str]):
        """Compare AI review with raw reviews for a selected recording."""
        if not recording_ids:
            self.console.print("[yellow]⚠️  No recordings found for this show[/yellow]")
            return
            
        recording_id = self.select_recording(recording_ids, "Compare AI vs Raw Reviews")
        if not recording_id:
            return
            
        if recording_id == "ALL":
            self.interactive_comparison_all(recording_ids)
        else:
            self.display_recording_comparison(recording_id)
    
    def display_raw_reviews(self, recording_ids: List[str]):
        """Display raw fan reviews for a selected recording."""
        if not recording_ids:
            self.console.print("[yellow]⚠️  No recordings found for this show[/yellow]")
            return
            
        recording_id = self.select_recording(recording_ids, "View Raw Reviews")
        if not recording_id:
            return
            
        self.display_raw_reviews_for_recording(recording_id)
    
    def select_recording(self, recording_ids: List[str], title: str) -> Optional[str]:
        """Allow user to select a specific recording from the list."""
        self.console.print(f"\n[bold blue]🎧 {title}[/bold blue]")
        
        if len(recording_ids) == 1:
            return recording_ids[0]
            
        # Show simplified list
        for i, recording_id in enumerate(recording_ids, 1):
            display_id = recording_id if len(recording_id) <= 60 else recording_id[:57] + "..."
            self.console.print(f"  {i}. {display_id}")
        
        self.console.print(f"  [bold]a.[/bold] All recordings (interactive step-through)")
        
        while True:
            try:
                choice = Prompt.ask(
                    f"Select recording (1-{len(recording_ids)}, 'a' for all, or '0' to go back)",
                    default="1"
                )
                
                if choice == "0":
                    return None
                elif choice.lower() == "a":
                    return "ALL"
                else:
                    choice_num = int(choice)
                    if 1 <= choice_num <= len(recording_ids):
                        return recording_ids[choice_num - 1]
                    else:
                        self.console.print(f"[red]Please enter a number between 1 and {len(recording_ids)}[/red]")
                
            except ValueError:
                self.console.print("[red]Invalid input. Please enter a number, 'a' for all, or '0' to go back.[/red]")
            except KeyboardInterrupt:
                return None
    
    def interactive_comparison_all(self, recording_ids: List[str]):
        """Step through all recordings with interactive scrollable comparison."""
        current_index = 0
        
        while current_index < len(recording_ids):
            recording_id = recording_ids[current_index]
            result = self.interactive_recording_comparison(
                recording_id, 
                current_index + 1, 
                len(recording_ids)
            )
            
            if result == "next":
                current_index += 1
            elif result == "prev":
                current_index = max(0, current_index - 1)
            elif result == "quit":
                break
            elif result == "jump":
                try:
                    jump_to = IntPrompt.ask(
                        f"Jump to recording (1-{len(recording_ids)})",
                        default=current_index + 1
                    )
                    if 1 <= jump_to <= len(recording_ids):
                        current_index = jump_to - 1
                except (ValueError, KeyboardInterrupt):
                    continue
    
    def interactive_recording_comparison(self, recording_id: str, current: int, total: int) -> str:
        """Show interactive scrollable comparison for a single recording."""
        # Load data
        recording_path = self.recordings_dir / f"{recording_id}.json"
        archive_path = self.archive_dir / f"{recording_id}.json"
        
        try:
            # Load AI review
            ai_data = {}
            if recording_path.exists():
                with open(recording_path) as f:
                    recording_data = json.load(f)
                    ai_data = recording_data.get('ai_review', {})
            
            # Load raw reviews
            raw_reviews = []
            if archive_path.exists():
                with open(archive_path) as f:
                    archive_data = json.load(f)
                    raw_reviews = archive_data.get('raw_reviews', [])
        except Exception as e:
            self.console.print(f"[red]❌ Error loading data: {e}[/red]")
            return "next"
        
        # Initialize scroll positions
        ai_scroll = 0
        raw_scroll = 0
        
        # Prepare display data
        ai_lines = self.format_comprehensive_ai_review(ai_data, recording_id)
        raw_lines = self.format_comprehensive_raw_reviews(raw_reviews)
        
        layout = Layout()
        layout.split_row(
            Layout(name="ai", ratio=1),
            Layout(name="raw", ratio=1)
        )
        
        # Initialize display once
        def update_display():
            viewport_height = self.get_viewport_height()
            ai_viewport = self.create_viewport(ai_lines, ai_scroll, viewport_height)
            raw_viewport = self.create_viewport(raw_lines, raw_scroll, viewport_height)

            header = f"Recording {current}/{total}: {recording_id[:50]}{'...' if len(recording_id) > 50 else ''}"

            layout["ai"].update(Panel(
                ai_viewport,
                title=f"🤖 AI Review (D/F to scroll) [{ai_scroll+1}-{min(ai_scroll+viewport_height, len(ai_lines))}/{len(ai_lines)}]",
                style="blue",
                subtitle=header,
                border_style="blue"
            ))
            
            layout["raw"].update(Panel(
                raw_viewport,
                title=f"👥 Raw Reviews (J/K to scroll) [{raw_scroll+1}-{min(raw_scroll+viewport_height, len(raw_lines))}/{len(raw_lines)}]",
                style="green",
                subtitle="Controls: H/L=recordings, S/G=shows, X=jump, Q=quit",
                border_style="green"
            ))

        # Main interaction loop
        with Live(layout, console=self.console, refresh_per_second=2, auto_refresh=False) as live:
            update_display()
            live.refresh()
            
            while True:
                # Get single key input
                key = self.get_key()
                
                if key.lower() == 'q':
                    return "quit"
                elif key.lower() == 'h':  # prev recording
                    return "prev"
                elif key.lower() == 'l':  # next recording
                    return "next" 
                elif key.lower() == 's':  # prev show
                    return "prev"
                elif key.lower() == 'g':  # next show
                    return "next"
                elif key.lower() == 'x':  # jump
                    return "jump"
                elif key.lower() == 'j':  # scroll right panel (raw reviews) DOWN
                    old_scroll = raw_scroll
                    viewport_height = self.get_viewport_height()
                    raw_scroll = min(raw_scroll + 1, max(0, len(raw_lines) - viewport_height))
                    if raw_scroll != old_scroll:
                        update_display()
                        live.refresh()
                elif key.lower() == 'k':  # scroll right panel (raw reviews) UP
                    old_scroll = raw_scroll
                    raw_scroll = max(0, raw_scroll - 1)
                    if raw_scroll != old_scroll:
                        update_display()
                        live.refresh()
                elif key.lower() == 'd':  # scroll left panel (AI review) UP
                    old_scroll = ai_scroll
                    ai_scroll = max(0, ai_scroll - 1)
                    if ai_scroll != old_scroll:
                        update_display()
                        live.refresh()
                elif key.lower() == 'f':  # scroll left panel (AI review) DOWN
                    old_scroll = ai_scroll
                    viewport_height = self.get_viewport_height()
                    ai_scroll = min(ai_scroll + 1, max(0, len(ai_lines) - viewport_height))
                    if ai_scroll != old_scroll:
                        update_display()
                        live.refresh()
    
    def get_viewport_height(self) -> int:
        """Get optimal viewport height based on terminal size."""
        try:
            import os
            terminal_height = os.get_terminal_size().lines
            # Reserve space for panel titles, borders, and controls (about 8 lines)
            # Use 80% of available height for viewport content
            return max(10, int((terminal_height - 8) * 0.8))
        except:
            # Fallback to larger default if terminal size detection fails
            return 35

    def create_viewport(self, lines: List[str], scroll: int, height: int) -> str:
        """Create a viewport showing a portion of lines."""
        start = max(0, scroll)
        end = min(len(lines), start + height)
        viewport_lines = lines[start:end]

        # Pad with empty lines if needed
        while len(viewport_lines) < height:
            viewport_lines.append("")

        return "\n".join(viewport_lines)
    
    def format_comprehensive_ai_review(self, ai_data: Dict[str, Any], recording_id: str) -> List[str]:
        """Format complete AI review data into lines for scrollable display."""
        lines = []
        
        if not ai_data:
            lines.append("[yellow]No AI review available[/yellow]")
            return lines
        
        lines.append(f"[bold cyan]Recording:[/bold cyan] {recording_id}")
        lines.append("")
        
        # AI Rating
        ai_rating = ai_data.get('ai_rating', {})
        stars = ai_rating.get('stars', 'N/A')
        confidence = ai_rating.get('confidence', '')
        rationale = ai_rating.get('rationale', '')
        
        lines.append(f"[bold yellow]⭐ AI Rating:[/bold yellow] {stars}")
        if confidence:
            lines.append(f"[bold yellow]Confidence:[/bold yellow] {confidence}")
        if rationale:
            lines.append(f"[bold yellow]Rationale:[/bold yellow] {rationale}")
        lines.append("")
        
        # Summary
        summary = ai_data.get('summary', '')
        if summary:
            lines.append(f"[bold cyan]Summary:[/bold cyan]")
            lines.extend(self.wrap_text(summary, 100))
            lines.append("")
        
        # Review
        review = ai_data.get('review', '')
        if review:
            lines.append(f"[bold blue]Full Review:[/bold blue]")
            lines.extend(self.wrap_text(review, 100))
            lines.append("")
        
        # Sentiment
        sentiment = ai_data.get('sentiment', '')
        if sentiment:
            lines.append(f"[bold magenta]Sentiment:[/bold magenta] {sentiment}")
            lines.append("")
        
        # Recording Quality
        recording_quality = ai_data.get('recording_quality', {})
        if recording_quality:
            lines.append(f"[bold green]📼 Recording Quality:[/bold green]")
            source_type = recording_quality.get('source_type', 'unknown')
            quality_rating = recording_quality.get('quality_rating', 'unknown')
            technical_notes = recording_quality.get('technical_notes', '')
            
            lines.append(f"  Source Type: {source_type}")
            lines.append(f"  Quality: {quality_rating}")
            if technical_notes:
                lines.append(f"  Technical Notes:")
                lines.extend(["    " + line for line in self.wrap_text(technical_notes, 46)])
            lines.append("")
        
        # Show Quality
        show_quality = ai_data.get('show_quality', {})
        if show_quality:
            lines.append(f"[bold red]🎭 Show Quality:[/bold red]")
            energy_level = show_quality.get('energy_level', 'unknown')
            setlist_flow = show_quality.get('setlist_flow', '')
            standout_songs = show_quality.get('standout_songs', [])
            poor_songs = show_quality.get('poor_songs', [])
            
            lines.append(f"  Energy Level: {energy_level}")
            
            if standout_songs:
                lines.append(f"  Standout Songs:")
                for song in standout_songs:
                    lines.append(f"    • {song}")
            
            if poor_songs:
                lines.append(f"  Poor Songs:")
                for song in poor_songs:
                    lines.append(f"    • {song}")
            
            if setlist_flow:
                lines.append(f"  Setlist Flow:")
                lines.extend(["    " + line for line in self.wrap_text(setlist_flow, 46)])
            lines.append("")
        
        # Band Member Comments
        band_comments = ai_data.get('band_member_comments', {})
        if band_comments and any(v.strip() for v in band_comments.values()):
            lines.append(f"[bold purple]🎸 Band Member Analysis:[/bold purple]")
            for member, comment in band_comments.items():
                if comment and comment.strip():
                    lines.append(f"  [bold]{member}:[/bold]")
                    lines.extend(["    " + line for line in self.wrap_text(comment, 46)])
                    lines.append("")
        
        return lines
    
    def format_comprehensive_raw_reviews(self, raw_reviews: List[Dict[str, Any]]) -> List[str]:
        """Format raw reviews into lines for scrollable display."""
        lines = []
        
        if not raw_reviews:
            lines.append("[yellow]No raw reviews available[/yellow]")
            return lines
        
        # Statistics
        total_reviews = len(raw_reviews)
        valid_ratings = []
        for r in raw_reviews:
            stars = r.get('stars', 0)
            try:
                if isinstance(stars, str):
                    stars = float(stars) if stars else 0
                elif isinstance(stars, (int, float)):
                    stars = float(stars)
                else:
                    stars = 0
                valid_ratings.append(stars)
            except (ValueError, TypeError):
                valid_ratings.append(0)
        
        avg_rating = sum(valid_ratings) / total_reviews if total_reviews > 0 else 0
        
        lines.append(f"[bold yellow]📊 Review Statistics[/bold yellow]")
        lines.append(f"Total Reviews: {total_reviews}")
        lines.append(f"Average Rating: ⭐ {avg_rating:.1f}")
        lines.append("")
        
        # Individual reviews
        lines.append(f"[bold blue]📝 Individual Reviews[/bold blue]")
        lines.append("")
        
        for i, review in enumerate(raw_reviews, 1):
            stars = valid_ratings[i-1] if i-1 < len(valid_ratings) else 0
            reviewer = review.get('reviewer', 'Anonymous')
            date = review.get('reviewdate', '')
            body = review.get('reviewbody', 'No review text')
            
            header = f"[bold]Review #{i} - {reviewer}[/bold]"
            if date:
                header += f" ({date})"
            header += f" - ⭐ {stars:.1f}"
            
            lines.append(header)
            lines.append("─" * 50)
            lines.extend(self.wrap_text(body, 100))
            lines.append("")
            lines.append("")
        
        return lines
    
    def wrap_text(self, text: str, width: int) -> List[str]:
        """Wrap text to specified width."""
        words = text.split()
        lines = []
        current_line = []
        current_length = 0
        
        for word in words:
            if current_length + len(word) + 1 <= width:
                current_line.append(word)
                current_length += len(word) + 1
            else:
                if current_line:
                    lines.append(" ".join(current_line))
                current_line = [word]
                current_length = len(word)
        
        if current_line:
            lines.append(" ".join(current_line))
        
        return lines
    
    def get_key(self) -> str:
        """Get a single key press from the user."""
        try:
            fd = sys.stdin.fileno()
            old_settings = termios.tcgetattr(fd)
            tty.setraw(sys.stdin.fileno())
            key = sys.stdin.read(1)
            termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
            
            # Handle special keys and control sequences
            if key == '\x1b':  # Escape sequence
                try:
                    key += sys.stdin.read(2)
                    if key == '\x1b[A':  # Up arrow - show navigation
                        return 'UP_ARROW'
                    elif key == '\x1b[B':  # Down arrow - show navigation
                        return 'DOWN_ARROW'
                    elif key == '\x1b[C':  # Right arrow - show navigation
                        return 'RIGHT_ARROW' 
                    elif key == '\x1b[D':  # Left arrow - show navigation
                        return 'LEFT_ARROW'
                except:
                    pass
            
            # Return the raw key - uppercase letters indicate Shift was pressed
            return key
        except:
            return 'q'  # Fallback to quit on any error
    
    def display_recording_comparison(self, recording_id: str):
        """Show side-by-side comparison of AI review vs raw reviews."""
        self.console.print("\n" + "="*100)
        self.console.print(Panel.fit(
            f"📊 [bold cyan]AI vs Raw Reviews Comparison[/bold cyan]",
            style="cyan"
        ))
        
        self.console.print(f"[bold]Recording:[/bold] {recording_id}")
        
        # Load recording data
        recording_path = self.recordings_dir / f"{recording_id}.json"
        archive_path = self.archive_dir / f"{recording_id}.json"
        
        try:
            # Load AI review
            ai_data = {}
            if recording_path.exists():
                with open(recording_path) as f:
                    recording_data = json.load(f)
                    ai_data = recording_data.get('ai_review', {})
            
            # Load raw reviews
            raw_reviews = []
            if archive_path.exists():
                with open(archive_path) as f:
                    archive_data = json.load(f)
                    raw_reviews = archive_data.get('raw_reviews', [])
            
            # Create side-by-side layout
            layout = Layout()
            layout.split_row(
                Layout(name="ai", ratio=1),
                Layout(name="raw", ratio=1)
            )
            
            # AI Review Panel
            ai_content = self.format_ai_review_for_comparison(ai_data)
            layout["ai"].update(Panel(
                ai_content,
                title="🤖 AI Review",
                style="blue"
            ))
            
            # Raw Reviews Panel  
            raw_content = self.format_raw_reviews_for_comparison(raw_reviews)
            layout["raw"].update(Panel(
                raw_content,
                title="👥 Raw Fan Reviews",
                style="green"
            ))
            
            self.console.print(layout)
            
        except Exception as e:
            self.console.print(f"[red]❌ Error loading comparison data: {e}[/red]")
        
        input("\nPress Enter to continue...")
    
    def format_ai_review_for_comparison(self, ai_data: Dict[str, Any]) -> str:
        """Format AI review data for comparison display."""
        if not ai_data:
            return "[yellow]No AI review available[/yellow]"
        
        content = []
        
        # Rating
        ai_rating = ai_data.get('ai_rating', {})
        stars = ai_rating.get('stars', 'N/A')
        confidence = ai_rating.get('confidence', '')
        
        content.append(f"[bold yellow]Rating:[/bold yellow] ⭐ {stars}")
        if confidence:
            content.append(f"[bold yellow]Confidence:[/bold yellow] {confidence}")
        
        # Summary
        summary = ai_data.get('summary', '')
        if summary:
            content.append(f"\n[bold cyan]Summary:[/bold cyan]\n{summary}")
        
        # Review
        review = ai_data.get('review', '')
        if review:
            content.append(f"\n[bold blue]Review:[/bold blue]\n{review}")
        
        # Recording quality
        recording_quality = ai_data.get('recording_quality', {})
        if recording_quality:
            source_type = recording_quality.get('source_type', 'unknown')
            quality_rating = recording_quality.get('quality_rating', 'unknown')
            content.append(f"\n[bold green]Source:[/bold green] {source_type}")
            content.append(f"[bold green]Quality:[/bold green] {quality_rating}")
        
        return "\n".join(content)
    
    def format_raw_reviews_for_comparison(self, raw_reviews: List[Dict[str, Any]]) -> str:
        """Format raw reviews data for comparison display."""
        if not raw_reviews:
            return "[yellow]No raw reviews available[/yellow]"
        
        content = []
        
        # Statistics
        total_reviews = len(raw_reviews)
        
        # Calculate average rating, handling both string and numeric types
        valid_ratings = []
        for r in raw_reviews:
            stars = r.get('stars', 0)
            try:
                if isinstance(stars, str):
                    stars = float(stars) if stars else 0
                elif isinstance(stars, (int, float)):
                    stars = float(stars)
                else:
                    stars = 0
                valid_ratings.append(stars)
            except (ValueError, TypeError):
                valid_ratings.append(0)
        
        avg_rating = sum(valid_ratings) / total_reviews if total_reviews > 0 else 0
        
        content.append(f"[bold yellow]Total Reviews:[/bold yellow] {total_reviews}")
        content.append(f"[bold yellow]Average Rating:[/bold yellow] ⭐ {avg_rating:.1f}")
        
        # Show first few reviews
        content.append(f"\n[bold blue]Sample Reviews:[/bold blue]")
        
        for i, review in enumerate(raw_reviews[:5], 1):  # Show first 5 reviews
            stars = review.get('stars', 0)
            # Ensure stars is properly formatted
            try:
                if isinstance(stars, str):
                    stars = float(stars) if stars else 0
                elif isinstance(stars, (int, float)):
                    stars = float(stars)
                else:
                    stars = 0
            except (ValueError, TypeError):
                stars = 0
                
            reviewer = review.get('reviewer', 'Anonymous')
            body = review.get('reviewbody', '')
            
            # Truncate long reviews
            if len(body) > 150:
                body = body[:147] + "..."
            
            content.append(f"\n[dim]{i}. {reviewer}[/dim] (⭐ {stars:.1f})")
            content.append(f"[white]{body}[/white]")
        
        if total_reviews > 5:
            content.append(f"\n[dim]... and {total_reviews - 5} more reviews[/dim]")
        
        return "\n".join(content)
    
    def display_raw_reviews_for_recording(self, recording_id: str):
        """Display all raw reviews for a recording."""
        self.console.print("\n" + "="*80)
        self.console.print(Panel.fit(
            f"👥 [bold cyan]Raw Fan Reviews[/bold cyan]",
            style="cyan"
        ))
        
        self.console.print(f"[bold]Recording:[/bold] {recording_id}")
        
        # Load raw reviews
        archive_path = self.archive_dir / f"{recording_id}.json"
        
        try:
            if not archive_path.exists():
                self.console.print("[yellow]⚠️  No archive data found for this recording[/yellow]")
                return
                
            with open(archive_path) as f:
                archive_data = json.load(f)
                
            raw_reviews = archive_data.get('raw_reviews', [])
            
            if not raw_reviews:
                self.console.print("[yellow]⚠️  No reviews found in archive data[/yellow]")
                return
            
            # Show statistics
            total_reviews = len(raw_reviews)
            
            # Calculate average rating, handling both string and numeric types
            valid_ratings = []
            for r in raw_reviews:
                stars = r.get('stars', 0)
                try:
                    if isinstance(stars, str):
                        stars = float(stars) if stars else 0
                    elif isinstance(stars, (int, float)):
                        stars = float(stars)
                    else:
                        stars = 0
                    valid_ratings.append(stars)
                except (ValueError, TypeError):
                    valid_ratings.append(0)
            
            avg_rating = sum(valid_ratings) / total_reviews if total_reviews > 0 else 0
            
            self.console.print(Panel(
                f"[bold]Total Reviews:[/bold] {total_reviews}\n"
                f"[bold]Average Rating:[/bold] ⭐ {avg_rating:.1f}",
                title="Review Statistics",
                style="yellow"
            ))
            
            # Display all reviews
            for i, review in enumerate(raw_reviews, 1):
                stars = review.get('stars', 0)
                # Ensure stars is properly formatted
                try:
                    if isinstance(stars, str):
                        stars = float(stars) if stars else 0
                    elif isinstance(stars, (int, float)):
                        stars = float(stars)
                    else:
                        stars = 0
                except (ValueError, TypeError):
                    stars = 0
                    
                reviewer = review.get('reviewer', 'Anonymous')
                date = review.get('reviewdate', '')
                body = review.get('reviewbody', 'No review text')
                
                header = f"Review #{i} - {reviewer}"
                if date:
                    header += f" ({date})"
                header += f" - ⭐ {stars:.1f}"
                
                self.console.print(Panel(
                    body,
                    title=header,
                    style="white"
                ))
                
                # Pause every 5 reviews
                if i % 5 == 0 and i < total_reviews:
                    cont = Prompt.ask(f"Showing {i}/{total_reviews} reviews. Continue? (y/n/q)", default="y")
                    if cont.lower() in ['n', 'no']:
                        break
                    elif cont.lower() in ['q', 'quit']:
                        return
            
        except Exception as e:
            self.console.print(f"[red]❌ Error loading raw reviews: {e}[/red]")
        
        input("\nPress Enter to continue...")

    def refine_show_review(self, show_path: Path, show_data: Dict[str, Any]):
        """Interactive review refinement with local LLM processing."""
        ai_review = show_data.get('ai_show_review', {})
        if not ai_review:
            # Clear the current display and show error
            self.console.clear()
            self.console.print("[red]❌ No AI review found for this show[/red]")
            input("Press Enter to continue...")
            return
        
        # Clear the current display for refinement interface
        self.console.clear()
            
        self.console.print(Panel.fit(
            "🔧 [bold cyan]AI Review Language Refinement[/bold cyan]\n"
            "Fix repetitive terms and improve language variety using local LLM",
            style="cyan"
        ))
        
        while True:
            # Display current review sections
            self.display_review_sections(ai_review)
            
            # Get refinement instructions
            self.console.print("\n[bold green]Refinement Instructions:[/bold green]")
            self.console.print("  Enter specific instructions like:")
            self.console.print("  - 'Replace high energy with more varied terms'")
            self.console.print("  - 'Use different words instead of monster and fire'")
            self.console.print("  - 'Vary the language in the summary'")
            self.console.print("  - 'all' to apply standard language variety fixes")
            self.console.print("  - 'quit' to return to main menu")
            
            instruction = Prompt.ask("Refinement instruction").strip()
            
            if instruction.lower() in ['quit', 'q', 'exit']:
                break
            elif instruction.lower() == 'all':
                instruction = "Replace overused terms like 'high energy', 'monster', 'fire', 'scorching', 'tight' with more varied and specific language. Avoid repetitive phrases and use diverse descriptive terms throughout all fields."
            elif not instruction:
                self.console.print("[yellow]Please enter a refinement instruction[/yellow]")
                continue
                
            # Process with LLM
            try:
                refined_review = self.process_refinement_with_llm(ai_review, instruction, show_data)
                if refined_review:
                    # Show changes and get user decision
                    decision = self.review_refinement_changes(ai_review, refined_review)
                    
                    if decision == 'accept':
                        # Update the show data
                        show_data['ai_show_review'] = refined_review
                        self.save_refined_review(show_path, show_data)
                        self.console.print("[green]✅ Review updated successfully![/green]")
                        break
                    elif decision == 'refine':
                        # Get additional refinement instructions
                        additional_instruction = Prompt.ask("Additional refinement instruction")
                        if additional_instruction.strip():
                            refined_review = self.process_refinement_with_llm(
                                refined_review, additional_instruction, show_data
                            )
                            if refined_review:
                                decision = self.review_refinement_changes(ai_review, refined_review)
                                if decision == 'accept':
                                    show_data['ai_show_review'] = refined_review
                                    self.save_refined_review(show_path, show_data)
                                    self.console.print("[green]✅ Review updated successfully![/green]")
                                    break
                    # If reject, continue to next iteration
                    
            except Exception as e:
                self.console.print(f"[red]❌ Refinement failed: {e}[/red]")
                input("Press Enter to continue...")

    def display_review_sections(self, ai_review: Dict[str, Any]):
        """Display the current AI review sections for refinement preview."""
        sections = [
            ("Summary", ai_review.get('summary', '')),
            ("Blurb", ai_review.get('blurb', '')),
            ("Key Highlights", '\n'.join(ai_review.get('key_highlights', []))),
            ("Song Highlights", ', '.join(ai_review.get('song_highlights', []))),
            ("Review Preview", ai_review.get('review', '')[:200] + '...' if len(ai_review.get('review', '')) > 200 else ai_review.get('review', ''))
        ]
        
        for title, content in sections:
            if content:
                self.console.print(Panel(
                    content,
                    title=f"[bold cyan]{title}[/bold cyan]",
                    style="white"
                ))

    def process_refinement_with_llm(self, original_review: Dict[str, Any], instruction: str, show_data: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        """Process review refinement using existing LLM infrastructure."""
        self.console.print("\n[yellow]🤖 Processing with LLM...[/yellow]")
        
        try:
            # Add current directory to Python path to import review_processor  
            current_dir = Path(__file__).parent  # review-processor directory
            sys.path.insert(0, str(current_dir))
            
            # Import and initialize the existing LLM client
            from review_processor import Config, LLMClient
            
            # Load configuration
            config_path = current_dir / "config.json"
            if not config_path.exists():
                self.console.print(f"[red]❌ LLM configuration file not found at {config_path}[/red]")
                self.console.print("Please set up the review processor configuration first.")
                input("Press Enter to continue...")
                return None
            
            config = Config.load(config_path)
            provider_config = config.providers[config.default_provider]
            llm_client = LLMClient(provider_config)
            
            # Load refinement prompt
            prompt_path = current_dir / "prompts" / "refine_review.md"
            if not prompt_path.exists():
                raise FileNotFoundError(f"Refinement prompt not found at {prompt_path}")
                
            with open(prompt_path) as f:
                system_prompt = f.read()
            
            # Collect source reviews for authentic language
            source_reviews = []
            for recording_id in show_data.get('recordings', []):
                archive_path = Path(f"stage01-collected-data/archive/{recording_id}.json")
                if archive_path.exists():
                    try:
                        with open(archive_path) as f:
                            archive_data = json.load(f)
                            raw_reviews = archive_data.get('raw_reviews', [])
                            for review in raw_reviews[:3]:  # Include up to 3 reviews per recording
                                review_text = review.get('reviewbody', '').strip()
                                if review_text and len(review_text) > 20:  # Only meaningful reviews
                                    source_reviews.append({
                                        'reviewer': review.get('reviewer', 'Anonymous'),
                                        'text': review_text[:500]  # Limit length
                                    })
                    except:
                        continue
            
            # Create user prompt with the refinement data
            source_reviews_text = ""
            if source_reviews:
                source_reviews_text = "\n**Original Fan Reviews (Source Material for Authentic Language):**\n"
                for i, review in enumerate(source_reviews[:10], 1):  # Max 10 reviews
                    source_reviews_text += f"{i}. {review['reviewer']}: \"{review['text']}\"\n\n"
                source_reviews_text += "**IMPORTANT**: Use the language and expressions from these original reviews when refining the AI review. Draw authentic terminology and descriptions directly from how these fans described the show.\n"
            
            user_prompt = f"""## Input Data

**Original Review:**
```json
{json.dumps(original_review, indent=2)}
```

**Refinement Instruction:**
{instruction}

{source_reviews_text}

**Show Context:**
- Date: {show_data.get('date', 'Unknown')}
- Venue: {show_data.get('venue', 'Unknown')}
- Location: {show_data.get('location_raw', 'Unknown')}

Please provide the refined review as a complete JSON object with the same structure as the original, but with improved language based on the refinement instruction and the authentic language from the original fan reviews."""

            # Call the LLM using existing infrastructure
            response = llm_client.generate_response(system_prompt, user_prompt)
            
            # Extract content from response
            if 'content' in response:
                content = response['content']
            else:
                raise Exception("Invalid response format from LLM")
            
            # Extract JSON from response (handle markdown code blocks)
            if '```json' in content:
                start = content.find('```json') + 7
                end = content.find('```', start)
                json_str = content[start:end].strip()
            elif '```' in content:
                start = content.find('```') + 3
                end = content.find('```', start)
                json_str = content[start:end].strip()
            else:
                json_str = content.strip()
            
            refined_review = json.loads(json_str)
            
            # Validate structure
            required_fields = ['summary', 'blurb', 'review', 'ratings', 'best_recording', 
                             'key_highlights', 'song_highlights', 'band_performance']
            
            for field in required_fields:
                if field not in refined_review:
                    raise ValueError(f"Missing required field: {field}")
            
            self.console.print("[green]✅ Successfully processed refinement with LLM[/green]")
            return refined_review
            
        except FileNotFoundError as e:
            self.console.print(f"[red]❌ File not found: {e}[/red]")
        except json.JSONDecodeError as e:
            self.console.print(f"[red]❌ Failed to parse LLM response as JSON: {e}[/red]")
            self.console.print(f"[dim]Raw response: {content[:200] if 'content' in locals() else 'N/A'}...[/dim]")
        except ImportError as e:
            self.console.print(f"[red]❌ Failed to import review processor: {e}[/red]")
            self.console.print("Make sure the review processor is properly set up.")
        except Exception as e:
            self.console.print(f"[red]❌ LLM processing failed: {e}[/red]")
        
        input("Press Enter to continue...")
        return None

    def review_refinement_changes(self, original: Dict[str, Any], refined: Dict[str, Any]) -> str:
        """Show changes and get user decision on refinement."""
        self.console.print(Panel.fit(
            "📋 [bold cyan]Review Refinement Changes[/bold cyan]",
            style="cyan"
        ))
        
        # Show side-by-side comparison for key fields
        fields_to_compare = ['summary', 'blurb', 'review']
        
        for field in fields_to_compare:
            if field in original and field in refined:
                original_text = original[field]
                refined_text = refined[field]
                
                if original_text != refined_text:
                    self.console.print(f"\n[bold yellow]{field.upper()} Changes:[/bold yellow]")
                    
                    # Create diff
                    diff_lines = list(difflib.unified_diff(
                        original_text.split('\n'),
                        refined_text.split('\n'),
                        fromfile='Original',
                        tofile='Refined',
                        lineterm=''
                    ))
                    
                    if diff_lines:
                        diff_text = '\n'.join(diff_lines[2:])  # Skip headers
                        
                        # Color code the diff
                        colored_diff = Text()
                        for line in diff_lines[2:]:
                            if line.startswith('+'):
                                colored_diff.append(line + '\n', style="green")
                            elif line.startswith('-'):
                                colored_diff.append(line + '\n', style="red") 
                            else:
                                colored_diff.append(line + '\n', style="white")
                        
                        self.console.print(Panel(colored_diff, title=f"{field} Changes"))
                    else:
                        # Fallback: show before/after
                        self.console.print(Panel(
                            f"[red]BEFORE:[/red] {original_text}\n\n[green]AFTER:[/green] {refined_text}",
                            title=f"{field} Comparison"
                        ))
        
        # Show other changes
        other_changes = []
        for key in ['key_highlights', 'song_highlights']:
            if key in original and key in refined:
                if original[key] != refined[key]:
                    other_changes.append(f"{key}: Modified")
        
        if other_changes:
            self.console.print(f"\n[bold yellow]Other Changes:[/bold yellow] {', '.join(other_changes)}")
        
        # Get user decision
        self.console.print("\n[bold green]Decision Options:[/bold green]")
        self.console.print("  [green]accept[/green] - Apply these changes")
        self.console.print("  [yellow]refine[/yellow] - Make additional refinements")
        self.console.print("  [red]reject[/red] - Discard and try again")
        
        while True:
            decision = Prompt.ask("Your decision", choices=['accept', 'refine', 'reject'])
            if decision in ['accept', 'refine', 'reject']:
                return decision

    def save_refined_review(self, show_path: Path, show_data: Dict[str, Any]):
        """Save the refined review back to the show file."""
        try:
            with open(show_path, 'w') as f:
                json.dump(show_data, f, indent=2, ensure_ascii=False)
        except Exception as e:
            self.console.print(f"[red]❌ Failed to save refined review: {e}[/red]")
            raise

    def get_key(self) -> str:
        """Get a single key press from the user."""
        try:
            fd = sys.stdin.fileno()
            old_settings = termios.tcgetattr(fd)
            tty.setraw(sys.stdin.fileno())
            key = sys.stdin.read(1)
            termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
            
            # Handle special keys and control sequences
            if key == '\x1b':  # Escape sequence
                try:
                    key += sys.stdin.read(2)
                    if key == '\x1b[A':  # Up arrow - show navigation
                        return 'UP_ARROW'
                    elif key == '\x1b[B':  # Down arrow - show navigation
                        return 'DOWN_ARROW'
                    elif key == '\x1b[C':  # Right arrow - show navigation
                        return 'RIGHT_ARROW' 
                    elif key == '\x1b[D':  # Left arrow - show navigation
                        return 'LEFT_ARROW'
                except:
                    pass
            
            # Return the raw key - uppercase letters indicate Shift was pressed
            return key
        except:
            return 'q'  # Fallback to quit on any error

    def batch_refine_reviews(self, shows: List[Path], instruction: str):
        """Apply refinement instruction to multiple shows."""
        self.console.print(Panel.fit(
            f"🔧 [bold cyan]Batch Review Refinement[/bold cyan]\n"
            f"Applying refinement to {len(shows)} shows",
            style="cyan"
        ))
        
        successful_refinements = []
        failed_refinements = []
        
        for i, show_path in enumerate(shows, 1):
            self.console.print(f"\n[bold yellow]Processing {i}/{len(shows)}: {show_path.name}[/bold yellow]")
            
            try:
                # Load show data
                with open(show_path) as f:
                    show_data = json.load(f)
                
                ai_review = show_data.get('ai_show_review', {})
                if not ai_review:
                    self.console.print(f"[yellow]⚠️  No AI review found, skipping...[/yellow]")
                    continue
                
                # Process refinement
                refined_review = self.process_refinement_with_llm(ai_review, instruction, show_data)
                
                if refined_review:
                    # Update and save
                    show_data['ai_show_review'] = refined_review
                    self.save_refined_review(show_path, show_data)
                    successful_refinements.append(show_path.name)
                    self.console.print(f"[green]✅ Successfully refined[/green]")
                else:
                    failed_refinements.append(show_path.name)
                    self.console.print(f"[red]❌ Failed to refine[/red]")
                    
            except Exception as e:
                failed_refinements.append(show_path.name)
                self.console.print(f"[red]❌ Error: {e}[/red]")
        
        # Summary
        self.console.print(Panel(
            f"[bold green]Successful refinements:[/bold green] {len(successful_refinements)}\n"
            f"[bold red]Failed refinements:[/bold red] {len(failed_refinements)}\n\n"
            f"[dim]Successful: {', '.join(successful_refinements[:5])}" + 
            ("..." if len(successful_refinements) > 5 else "") + "[/dim]",
            title="Batch Refinement Summary"
        ))
        
        input("Press Enter to continue...")


def main():
    """Entry point for the review analyzer."""
    try:
        analyzer = ReviewAnalyzer()
        analyzer.run()
    except KeyboardInterrupt:
        print("\n👋 Goodbye!")
    except Exception as e:
        print(f"❌ Fatal error: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()