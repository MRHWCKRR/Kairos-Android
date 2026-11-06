# Icons, tab motion and search parity

The owner reported that Material icons were merely similar to iOS, tab switching lacked the iOS bounce, and search appeared twice and used a different flow. They explicitly chose one shared icon set in both iOS and Android. The implementation uses portable MIT-licensed Phosphor SVG artwork, never extracted Apple SF Symbols.

Both apps use identical SVG path data for shared symbols; Android maps existing semantic call sites centrally and iOS preserves its symbol-name semantics through an asset catalog wrapper. Preserve brand colour, native Liquid Glass on iOS, and solid Android surfaces. Existing iOS tab behavior is the reference: 0.34-second spring, damping 0.86, newly selected icon bounce and compact labels. Android respects disabled system animation and does not bounce on an unchanged selection.

Keep one search entry beside the tab dock at every width. Remove the Home toolbar duplicate. Search stays above the current screen and draft, with Search/Done header, query field, All/Unfinished/Overdue/Completed filters, count and contextual board/task result cards. Opening a result preserves search and permits viewing actual board/task context. Android's current model lacks recurrence, notes and assignments; do not fake those fields or alter shared persistence to simulate parity.

Validation covers icon asset equality/coverage, search filtering and identity validity, narrow/large-text dock sizing, animation selection and reduced-motion behavior, builds and emulator interactions. Existing authentication and planner data formats remain intact.
