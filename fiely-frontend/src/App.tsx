import Header from './components/Header';
import Hero from './components/Hero';
import Features from './components/Features';
import AIFeatures from './components/AIFeatures';
import Audience from './components/Audience';
import TechStack from './components/TechStack';
import Roadmap from './components/Roadmap';
import CTA from './components/CTA';
import Footer from './components/Footer';

export default function App() {
  return (
    <div className="relative isolate min-h-screen overflow-x-hidden bg-white dark:bg-ink-950">
      {/* Ambient background */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[900px] bg-radial-fade"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 -z-10 bg-grid-light bg-[size:32px_32px] opacity-40 [mask-image:radial-gradient(ellipse_at_top,black_40%,transparent_75%)] dark:bg-grid-dark"
      />

      <Header />
      <main>
        <Hero />
        <Features />
        <AIFeatures />
        <Audience />
        <TechStack />
        <Roadmap />
        <CTA />
      </main>
      <Footer />
    </div>
  );
}
