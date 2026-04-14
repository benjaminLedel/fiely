import {
  ArrowRight,
  FileText,
  Image as ImageIcon,
  FileSpreadsheet,
  Folder,
  Sparkles,
  ShieldCheck,
  Search,
} from 'lucide-react';

export default function Hero() {
  return (
    <section className="relative pb-24 pt-16 sm:pt-20 lg:pb-32 lg:pt-28">
      <div className="container-narrow">
        <div className="grid gap-12 lg:grid-cols-12 lg:items-center lg:gap-8">
          {/* Left: copy */}
          <div className="animate-fade-up lg:col-span-6">
            <span className="eyebrow">
              <Sparkles className="h-3.5 w-3.5" />
              Open-source · DSGVO-compliant · AI-native
            </span>

            <h1 className="mt-6 text-balance text-5xl font-extrabold leading-[1.05] tracking-tight sm:text-6xl lg:text-7xl">
              Your files.{' '}
              <span className="gradient-text">Your rules.</span>{' '}
              Your AI.
            </h1>

            <p className="mt-6 max-w-xl text-lg leading-relaxed text-ink-600 dark:text-ink-300">
              Fiely is the modern, open-source alternative to Nextcloud — built for
              teams, schools and organizations that refuse to trade control for
              convenience. No PHP. No bloat. No compromises.
            </p>

            <div className="mt-8 flex flex-wrap items-center gap-3">
              <a href="#cta" className="btn-primary">
                Get early access
                <ArrowRight className="h-4 w-4" />
              </a>
              <a href="#features" className="btn-ghost">
                See what&rsquo;s inside
              </a>
            </div>

            <dl className="mt-12 grid max-w-lg grid-cols-3 gap-6 text-sm">
              <div>
                <dt className="text-ink-500 dark:text-ink-400">Stack</dt>
                <dd className="mt-1 font-semibold">Kotlin + React</dd>
              </div>
              <div>
                <dt className="text-ink-500 dark:text-ink-400">Deploy</dt>
                <dd className="mt-1 font-semibold">Self-hosted</dd>
              </div>
              <div>
                <dt className="text-ink-500 dark:text-ink-400">License</dt>
                <dd className="mt-1 font-semibold">MIT</dd>
              </div>
            </dl>
          </div>

          {/* Right: visual */}
          <div className="relative lg:col-span-6">
            <HeroVisual />
          </div>
        </div>
      </div>
    </section>
  );
}

function HeroVisual() {
  return (
    <div className="relative mx-auto aspect-[4/3] w-full max-w-xl">
      {/* Glow */}
      <div
        aria-hidden
        className="absolute -inset-8 -z-10 rounded-[2.5rem] bg-gradient-to-br from-brand-400/40 via-brand-500/20 to-transparent blur-3xl"
      />

      {/* Main card (browser chrome) */}
      <div className="absolute inset-0 rounded-2xl border border-ink-100 bg-white shadow-[0_30px_80px_-20px_rgba(15,37,86,0.35)] dark:border-white/10 dark:bg-ink-900">
        <div className="flex items-center gap-2 border-b border-ink-100 px-4 py-3 dark:border-white/10">
          <span className="h-2.5 w-2.5 rounded-full bg-red-400/80" />
          <span className="h-2.5 w-2.5 rounded-full bg-yellow-400/80" />
          <span className="h-2.5 w-2.5 rounded-full bg-emerald-400/80" />
          <div className="ml-3 flex flex-1 items-center gap-2 rounded-md bg-ink-50 px-3 py-1 text-xs text-ink-500 dark:bg-white/5 dark:text-ink-300">
            <ShieldCheck className="h-3.5 w-3.5 text-emerald-500" />
            fiely.local / drive
          </div>
        </div>

        <div className="grid grid-cols-12 gap-0">
          {/* Sidebar */}
          <aside className="col-span-4 space-y-1 border-r border-ink-100 p-4 text-sm dark:border-white/10">
            <p className="px-2 pb-2 text-[10px] font-semibold uppercase tracking-wider text-ink-400">
              Workspace
            </p>
            {[
              { label: 'All files', active: true },
              { label: 'Shared with me' },
              { label: 'Favorites' },
              { label: 'Trash' },
            ].map((item) => (
              <div
                key={item.label}
                className={[
                  'flex items-center gap-2 rounded-lg px-2 py-1.5',
                  item.active
                    ? 'bg-brand-500/10 text-brand-700 dark:text-brand-300'
                    : 'text-ink-600 dark:text-ink-300',
                ].join(' ')}
              >
                <Folder className="h-4 w-4" />
                <span className="truncate">{item.label}</span>
              </div>
            ))}
            <div className="mt-4 rounded-xl border border-dashed border-brand-400/40 bg-brand-500/5 p-3">
              <div className="flex items-center gap-2 text-xs font-semibold text-brand-700 dark:text-brand-300">
                <Sparkles className="h-3.5 w-3.5" />
                AI Search
              </div>
              <p className="mt-1 text-[11px] leading-relaxed text-ink-600 dark:text-ink-300">
                &ldquo;Find last March&rsquo;s contract&rdquo;
              </p>
            </div>
          </aside>

          {/* Main file area */}
          <div className="col-span-8 p-4">
            <div className="flex items-center gap-2 rounded-lg border border-ink-100 bg-ink-50/70 px-3 py-2 text-xs text-ink-500 dark:border-white/10 dark:bg-white/5">
              <Search className="h-3.5 w-3.5" />
              Search files, by content or name
            </div>

            <div className="mt-4 grid grid-cols-2 gap-3">
              <FileTile
                icon={<FileText className="h-5 w-5" />}
                name="Q1-report.pdf"
                meta="2.4 MB · AI: Report"
                tone="brand"
              />
              <FileTile
                icon={<ImageIcon className="h-5 w-5" />}
                name="team-offsite.jpg"
                meta="6 photos · AI: Event"
                tone="violet"
              />
              <FileTile
                icon={<FileSpreadsheet className="h-5 w-5" />}
                name="budget-2026.xlsx"
                meta="128 KB · AI: Finance"
                tone="emerald"
              />
              <FileTile
                icon={<FileText className="h-5 w-5" />}
                name="contract-acme.pdf"
                meta="1.1 MB · AI: Contract"
                tone="amber"
              />
            </div>

            <div className="mt-4 rounded-xl border border-ink-100 bg-white p-3 dark:border-white/10 dark:bg-ink-900/60">
              <div className="flex items-center justify-between text-xs">
                <span className="font-semibold">Uploading · design-v2.fig</span>
                <span className="text-ink-500">82%</span>
              </div>
              <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-ink-100 dark:bg-white/10">
                <div
                  className="h-full w-[82%] animate-shimmer rounded-full bg-[linear-gradient(90deg,#2f82ff,#59a4ff,#2f82ff)] bg-[length:200%_100%]"
                />
              </div>
              <p className="mt-2 text-[11px] text-ink-500">
                Resumable · chunked upload via tus.io
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Floating badge */}
      <div className="absolute -bottom-6 -left-6 hidden rounded-2xl border border-ink-100 bg-white p-3 shadow-soft sm:block dark:border-white/10 dark:bg-ink-900 animate-float">
        <div className="flex items-center gap-2 text-xs">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-500/10 text-brand-600 dark:text-brand-300">
            <Sparkles className="h-4 w-4" />
          </span>
          <div>
            <p className="font-semibold">Semantic search</p>
            <p className="text-ink-500">Finds by meaning, not filename</p>
          </div>
        </div>
      </div>

      <div className="absolute -right-4 top-8 hidden rounded-2xl border border-ink-100 bg-white p-3 shadow-soft sm:block dark:border-white/10 dark:bg-ink-900 animate-float [animation-delay:1.5s]">
        <div className="flex items-center gap-2 text-xs">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-600">
            <ShieldCheck className="h-4 w-4" />
          </span>
          <div>
            <p className="font-semibold">DSGVO compliant</p>
            <p className="text-ink-500">Hosted where you choose</p>
          </div>
        </div>
      </div>
    </div>
  );
}

type Tone = 'brand' | 'violet' | 'emerald' | 'amber';

const TONES: Record<Tone, string> = {
  brand: 'bg-brand-500/10 text-brand-600 dark:text-brand-300',
  violet: 'bg-violet-500/10 text-violet-600 dark:text-violet-300',
  emerald: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300',
  amber: 'bg-amber-500/10 text-amber-600 dark:text-amber-300',
};

function FileTile({
  icon,
  name,
  meta,
  tone,
}: {
  icon: React.ReactNode;
  name: string;
  meta: string;
  tone: Tone;
}) {
  return (
    <div className="group rounded-xl border border-ink-100 bg-white p-3 transition-colors hover:border-brand-200 dark:border-white/10 dark:bg-ink-900/60 dark:hover:border-brand-400/30">
      <div className="flex items-start gap-3">
        <span
          className={[
            'flex h-10 w-10 items-center justify-center rounded-lg',
            TONES[tone],
          ].join(' ')}
        >
          {icon}
        </span>
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{name}</p>
          <p className="truncate text-[11px] text-ink-500">{meta}</p>
        </div>
      </div>
    </div>
  );
}
