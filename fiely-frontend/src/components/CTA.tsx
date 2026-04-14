import { Github, Star, ArrowRight } from 'lucide-react';

export default function CTA() {
  return (
    <section id="cta" className="relative py-24 lg:py-32">
      <div className="container-narrow">
        <div className="relative overflow-hidden rounded-3xl border border-brand-500/20 bg-gradient-to-br from-brand-600 via-brand-700 to-ink-900 p-10 text-white shadow-glow sm:p-14 lg:p-20">
          <div
            aria-hidden
            className="pointer-events-none absolute inset-0 bg-grid-dark bg-[size:32px_32px] opacity-20 [mask-image:radial-gradient(ellipse_at_center,black_40%,transparent_75%)]"
          />
          <div
            aria-hidden
            className="pointer-events-none absolute -right-20 -top-20 h-72 w-72 rounded-full bg-brand-400/30 blur-3xl"
          />
          <div
            aria-hidden
            className="pointer-events-none absolute -bottom-24 -left-16 h-80 w-80 rounded-full bg-brand-500/30 blur-3xl"
          />

          <div className="relative grid gap-10 lg:grid-cols-12 lg:items-center">
            <div className="lg:col-span-7">
              <span className="eyebrow !border-white/20 !bg-white/10 !text-white">
                <Star className="h-3.5 w-3.5" />
                Star us, we&rsquo;re building in public
              </span>
              <h2 className="mt-5 text-balance text-4xl font-extrabold leading-tight tracking-tight sm:text-5xl">
                Your data belongs to you.
                <br />
                Fiely just helps you keep it that way.
              </h2>
              <p className="mt-5 max-w-xl text-lg text-brand-100/90">
                Fiely is in early design phase. Follow along, open an issue with your
                use case, or jump in and help shape the product.
              </p>
            </div>

            <div className="lg:col-span-5">
              <div className="flex flex-col gap-3">
                <a
                  href="https://github.com/benjaminledel/fiely"
                  target="_blank"
                  rel="noreferrer"
                  className="btn w-full bg-white text-ink-900 hover:bg-brand-50"
                >
                  <Github className="h-4 w-4" />
                  Star on GitHub
                </a>
                <a
                  href="mailto:hello@fiely.cloud"
                  className="btn w-full border border-white/20 bg-white/10 text-white backdrop-blur hover:bg-white/15"
                >
                  Get early access
                  <ArrowRight className="h-4 w-4" />
                </a>
                <p className="mt-2 text-xs text-brand-100/70">
                  MIT licensed · DSGVO compliant · Built with love in Germany
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
