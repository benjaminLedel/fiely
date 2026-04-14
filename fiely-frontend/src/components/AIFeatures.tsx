import { Sparkles, Search, Tag, MessageSquareText, PenLine, Cpu, Cloud } from 'lucide-react';

const AI_FEATURES = [
  {
    icon: Search,
    title: 'Semantic search',
    desc: 'Find files by content, not just filename. "The contract from last March" actually works.',
  },
  {
    icon: Tag,
    title: 'Auto-tagging',
    desc: 'Fiely understands what your files are. Invoice, contract, photo, code — automatically categorized.',
  },
  {
    icon: MessageSquareText,
    title: 'Chat with your files',
    desc: 'Ask questions across your documents. Privacy-first RAG, grounded in your own data.',
  },
  {
    icon: PenLine,
    title: 'Content generation',
    desc: 'Draft summaries, emails or reports based on stored files. Your drafts, powered by your data.',
  },
];

export default function AIFeatures() {
  return (
    <section id="ai" className="relative py-24 lg:py-32">
      {/* Background accent */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-1/2 -z-10 -translate-y-1/2"
      >
        <div className="mx-auto h-[500px] w-[900px] max-w-full rounded-full bg-brand-500/10 blur-3xl" />
      </div>

      <div className="container-narrow">
        <div className="grid gap-12 lg:grid-cols-12 lg:items-center lg:gap-16">
          <div className="lg:col-span-5">
            <span className="eyebrow">
              <Sparkles className="h-3.5 w-3.5" />
              AI, where it makes sense
            </span>
            <h2 className="mt-4 text-balance text-4xl font-bold tracking-tight sm:text-5xl">
              AI is not the product. AI makes the product{' '}
              <span className="gradient-text">better.</span>
            </h2>
            <p className="mt-5 text-lg text-ink-600 dark:text-ink-300">
              No bolt-on chatbot in the corner. Fiely uses AI to help you find, organize
              and act on your files — and only when it&rsquo;s genuinely useful.
            </p>

            <div className="mt-8 grid gap-3 sm:grid-cols-2">
              <ProviderPill icon={Cpu} title="Run local" subtitle="Ollama, GPU or CPU" />
              <ProviderPill icon={Cloud} title="Or cloud" subtitle="OpenAI, Claude, pluggable" />
            </div>

            <p className="mt-6 text-sm text-ink-500">
              Prefer no AI at all? Fiely runs perfectly without it.
            </p>
          </div>

          <div className="lg:col-span-7">
            <div className="grid gap-5 sm:grid-cols-2">
              {AI_FEATURES.map(({ icon: Icon, title, desc }, i) => (
                <div
                  key={title}
                  className={[
                    'card',
                    i % 2 === 0 ? 'sm:translate-y-0' : 'sm:translate-y-8',
                  ].join(' ')}
                >
                  <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-brand-500 to-brand-700 text-white shadow-glow">
                    <Icon className="h-5 w-5" />
                  </div>
                  <h3 className="mt-5 text-lg font-semibold">{title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-ink-600 dark:text-ink-300">
                    {desc}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

function ProviderPill({
  icon: Icon,
  title,
  subtitle,
}: {
  icon: typeof Cpu;
  title: string;
  subtitle: string;
}) {
  return (
    <div className="flex items-center gap-3 rounded-xl border border-ink-100 bg-white/70 p-3 backdrop-blur dark:border-white/10 dark:bg-white/5">
      <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-500/10 text-brand-600 dark:text-brand-300">
        <Icon className="h-5 w-5" />
      </span>
      <div>
        <p className="text-sm font-semibold">{title}</p>
        <p className="text-xs text-ink-500">{subtitle}</p>
      </div>
    </div>
  );
}
