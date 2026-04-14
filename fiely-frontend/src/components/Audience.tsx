import {
  Heart,
  Building2,
  GraduationCap,
  Landmark,
  TerminalSquare,
} from 'lucide-react';

const AUDIENCE = [
  {
    icon: Heart,
    title: 'Families',
    desc: 'Private photo and file storage, without handing your memories to Big Tech.',
  },
  {
    icon: Building2,
    title: 'Small teams',
    desc: 'Secure sharing and collaboration that feels as good as the tools you love.',
  },
  {
    icon: GraduationCap,
    title: 'Schools & universities',
    desc: 'A DSGVO-compliant, classroom-ready alternative to Google Drive or OneDrive.',
  },
  {
    icon: Landmark,
    title: 'Public sector',
    desc: 'On-premise, auditable and BSI-compatible — built with compliance in mind.',
  },
  {
    icon: TerminalSquare,
    title: 'Self-hosters',
    desc: 'Full control, an open stack and a single docker compose away from running.',
  },
];

export default function Audience() {
  return (
    <section id="who" className="relative py-24 lg:py-32">
      <div className="container-narrow">
        <div className="mx-auto max-w-2xl text-center">
          <span className="eyebrow">Who it&rsquo;s for</span>
          <h2 className="mt-4 text-4xl font-bold tracking-tight sm:text-5xl">
            Built for people who care{' '}
            <span className="gradient-text">where their data lives</span>
          </h2>
        </div>

        <div className="mt-16 grid gap-5 sm:grid-cols-2 lg:grid-cols-5">
          {AUDIENCE.map(({ icon: Icon, title, desc }, i) => (
            <div
              key={title}
              className={[
                'card',
                i === 0 ? 'lg:col-span-2' : '',
                i === AUDIENCE.length - 1 ? 'lg:col-span-2' : '',
              ].join(' ')}
            >
              <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-500/10 text-brand-600 dark:text-brand-300">
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
    </section>
  );
}
