/**
 * Minimal footer shown on every unauthenticated page. Links are placeholders
 * until the host admin configures Impressum / Datenschutz content — the
 * routes themselves will be owned by a later settings plugin.
 */
export default function LegalFooter() {
  return (
    <footer className="border-t border-ink-100 bg-white/60 px-6 py-4 text-center text-xs text-ink-500 backdrop-blur dark:border-white/10 dark:bg-ink-950/40 dark:text-ink-400">
      <nav className="flex flex-wrap items-center justify-center gap-x-4 gap-y-1">
        <span className="font-semibold text-ink-700 dark:text-ink-200">
          Fiely
        </span>
        <a
          href="/legal/imprint"
          className="hover:text-ink-900 dark:hover:text-white"
        >
          Imprint
        </a>
        <a
          href="/legal/privacy"
          className="hover:text-ink-900 dark:hover:text-white"
        >
          Privacy
        </a>
        <a
          href="/legal/terms"
          className="hover:text-ink-900 dark:hover:text-white"
        >
          Terms
        </a>
        <span aria-hidden>·</span>
        <span>Open source · MIT</span>
      </nav>
    </footer>
  );
}
