# TODO / Tech Debt: Transient Toasts

**Status**: Shared component shipped (ADR-0014) ✅ · migration of existing ad-hoc
toasts outstanding.

A single, reusable transient-toast component exists on both platforms. **All new
transient confirmations should go through it.** The remaining work is migrating the
pre-existing one-off toasts onto it — low priority, do opportunistically.

## Convention (use this going forward)

A *transient toast* = a brief, non-actionable confirmation of something that just
happened, with no other on-screen feedback (e.g. "Autoplay on", "Copied to
clipboard", "Download started").

- **Android**: inject `ToastController` (`core/database`) into a ViewModel and call
  `toastController.show("…")`. It renders through the global `AppToast`
  (`core/design/component/AppToast.kt`) overlay in `MainNavigation` — top of the
  z-stack, above every screen and the mini player.
- **iOS**: call `container.toastPresenter.show("…")` (`ToastPresenter` in
  `Core/Design`). Renders as `ActionToastView` in `MainNavigation`.
- Shared message copy belongs in one place per platform (see
  `autoplayToastMessage` for the pattern), so iOS and Android say the same thing.

**Do not** add new `android.widget.Toast`, ad-hoc `SnackbarHost` calls, or one-off
`.alert(...)` sheets for simple confirmations.

### When NOT to use the toast
Keep these as they are — they need an action, a choice, or must not be missed:

- Messages with an action (notification "View") or distinct semantics (offline
  banner) → the existing `snackbarHostState` / banner.
- Destructive confirms, "Switch Recording?" conflicts, import-result details with
  counts → alerts / dialogs.

Note: the toast shows **one message at a time** — a new `show()` replaces the
current one (no queue). Fine for confirmations; don't fire it in a tight loop.

## Migration backlog (existing ad-hoc toasts → shared component)

- [ ] Android Settings (`feature/settings/.../SettingsScreen.kt`): "Favorites
      imported", "Exported to …", "Developer mode enabled", "N taps to enable" —
      currently `Toast.makeText`.
- [ ] Android Search: "QR scanning is coming soon"
      (`SearchBarConfiguration.kt`), the SearchResults message
      (`SearchResultsScreen.kt`).
- [ ] iOS `BugReportView` clipboard copy → add "Copied to clipboard" (no feedback
      today).

## Candidate new uses (not debt — opportunistic polish)

- [ ] "Download started" (the one inline action whose only cue is a small progress
      ring).
- [ ] "Switched recording" after a recording change.
- [ ] "Added to / Removed from Favorites" (conventional; optional).
