"""
Authoritative Grateful Dead keyboardist / Bruce Hornsby correction data.

Source of truth for Bruce Hornsby's appearances: Jeff Lester's curated
"Bruce Hornsby's Grateful Dead Performance History"
(http://www.agitators.com/gd/bruce.html), transcribed 2026-06-08.

Used by two pipeline fixes:
  1. integrate_jerry_garcia_shows.apply_hornsby_lineup_fixes() — corrects the
     jerrygarcia.com lineup (which lists Hornsby for the whole tenure block) by
     removing him from the 22 dates he was absent, and records his 15 guest
     appearances.
  2. fix_band_keyboardist.py — relabels the AI-review band_performance keyboard
     slot, which is hardcoded to "Brent" on every show even after Brent Mydland
     died (1990-07-26).

Keyboardist timeline (post-Brent):
  - Brent Mydland: keyboardist through 1990-07-23 (died 1990-07-26).
  - Vince Welnick: joined 1990-09-07, stayed to the end (1995-07-09).
  - Bruce Hornsby: regular member 1990-09-15 .. 1992-03-24 (co-keyboardist with
    Vince), plus 15 guest appearances outside that window.
"""

# Brent Mydland's death — any show after this date must not credit Brent on keys.
BRENT_DEATH = "1990-07-26"

# Vince Welnick's first show; he is the keyboardist on every show from here on.
VINCE_START = "1990-09-07"

# Bruce Hornsby's regular-member tenure (inclusive), per Jeff Lester:
# "All shows from 9/15/90 MSG through 3/24/92 Auburn Hills except:" the absences.
HORNSBY_TENURE_START = "1990-09-15"
HORNSBY_TENURE_END = "1992-03-24"

# The 22 tenure-window dates Hornsby did NOT play (Vince covered keys alone).
HORNSBY_ABSENT_DATES = frozenset({
    "1990-10-13",  # Stockholm
    "1990-12-14",  # McNichols Arena, Denver
    "1991-02-19", "1991-02-20", "1991-02-21",  # Oakland Coliseum Arena
    "1991-03-23", "1991-03-24", "1991-03-25",  # Knickerbocker Arena, Albany
    "1991-03-27", "1991-03-28", "1991-03-29",  # Nassau Coliseum
    "1991-05-03", "1991-05-04", "1991-05-05",  # Cal Expo, Sacramento
    "1991-11-03",  # Polo Fields, Golden Gate Park
    "1991-12-27", "1991-12-28", "1991-12-30", "1991-12-31",  # Oakland Coliseum
    "1992-02-22", "1992-02-23", "1992-02-24",  # Oakland Coliseum
})

# 15 guest appearances (Hornsby sat in but was NOT the band's keyboardist).
# date -> instrument/capacity note as transcribed from Jeff Lester.
HORNSBY_GUEST_APPEARANCES = {
    # Pre-tenure (6)
    "1988-06-25": "accordion (2 songs)",
    "1988-09-24": "accordion / electric piano (3 songs)",
    "1989-07-12": "accordion (2 songs)",
    "1989-07-13": "accordion (2 songs)",
    "1989-12-10": "accordion / keyboards (8 songs)",
    "1990-07-10": "accordion (8 songs)",
    # Post-tenure (9)
    "1992-06-20": "accordion",
    "1993-03-18": "accordion",
    "1993-06-25": "accordion",
    "1993-06-26": "accordion",
    "1994-03-25": "accordion",
    "1994-08-04": "accordion",
    "1995-03-23": "piano",
    "1995-06-24": "piano",
    "1995-06-25": "piano",
}


def hornsby_is_regular(date: str) -> bool:
    """True if Hornsby was a playing regular member on this show date
    (within tenure and not one of his absences)."""
    if date is None:
        return False
    d = date[:10]
    return (HORNSBY_TENURE_START <= d <= HORNSBY_TENURE_END
            and d not in HORNSBY_ABSENT_DATES)


def keyboardist_label(date: str) -> str | None:
    """The band_performance keyboard-slot label for a post-Brent show.

    Returns "Keys" when both Hornsby and Welnick were regular co-keyboardists,
    "Vince" when Welnick covered keys alone, or None for shows on/before Brent's
    death (where the existing "Brent" slot is correct and must be left alone).
    """
    if date is None:
        return None
    d = date[:10]
    if d <= BRENT_DEATH:
        return None
    return "Keys" if hornsby_is_regular(d) else "Vince"
