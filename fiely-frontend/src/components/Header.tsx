import { useEffect, useState } from 'react';
import { Github, Menu, X } from 'lucide-react';
import Logo from './Logo';

const NAV = [
  { href: '#features', label: 'Features' },
  { href: '#ai', label: 'AI' },
  { href: '#who', label: 'Who it\u2019s for' },
  { href: '#stack', label: 'Tech' },
  { href: '#roadmap', label: 'Roadmap' },
];

export default function Header() {
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <header
      className={[
        'sticky top-0 z-40 transition-all duration-300',
        scrolled
          ? 'border-b border-ink-100/80 bg-white/80 backdrop-blur-lg dark:border-white/10 dark:bg-ink-950/70'
          : 'border-b border-transparent',
      ].join(' ')}
    >
      <div className="container-narrow flex h-16 items-center justify-between">
        <a href="#" className="flex items-center gap-2.5">
          <Logo className="h-9 w-9 drop-shadow-sm" />
          <span className="text-lg font-bold tracking-tight">
            Fiely
          </span>
          <span className="hidden rounded-full border border-brand-500/20 bg-brand-500/10 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wider text-brand-700 sm:inline-block dark:text-brand-300">
            Beta
          </span>
        </a>

        <nav className="hidden items-center gap-8 md:flex">
          {NAV.map((item) => (
            <a
              key={item.href}
              href={item.href}
              className="text-sm font-medium text-ink-600 transition-colors hover:text-ink-900 dark:text-ink-300 dark:hover:text-white"
            >
              {item.label}
            </a>
          ))}
        </nav>

        <div className="hidden items-center gap-3 md:flex">
          <a
            href="https://github.com/benjaminledel/fiely"
            target="_blank"
            rel="noreferrer"
            className="btn-ghost"
          >
            <Github className="h-4 w-4" />
            Star on GitHub
          </a>
          <a href="#cta" className="btn-primary">
            Get early access
          </a>
        </div>

        <button
          type="button"
          className="inline-flex h-10 w-10 items-center justify-center rounded-lg border border-ink-200 text-ink-700 md:hidden dark:border-white/10 dark:text-ink-200"
          onClick={() => setOpen((v) => !v)}
          aria-label="Toggle menu"
        >
          {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
      </div>

      {open && (
        <div className="border-t border-ink-100 bg-white md:hidden dark:border-white/10 dark:bg-ink-950">
          <div className="container-narrow flex flex-col gap-1 py-4">
            {NAV.map((item) => (
              <a
                key={item.href}
                href={item.href}
                onClick={() => setOpen(false)}
                className="rounded-lg px-3 py-2 text-sm font-medium text-ink-700 hover:bg-ink-50 dark:text-ink-200 dark:hover:bg-white/5"
              >
                {item.label}
              </a>
            ))}
            <div className="mt-2 flex flex-col gap-2">
              <a
                href="https://github.com/benjaminledel/fiely"
                target="_blank"
                rel="noreferrer"
                className="btn-ghost w-full"
              >
                <Github className="h-4 w-4" />
                Star on GitHub
              </a>
              <a href="#cta" onClick={() => setOpen(false)} className="btn-primary w-full">
                Get early access
              </a>
            </div>
          </div>
        </div>
      )}
    </header>
  );
}
