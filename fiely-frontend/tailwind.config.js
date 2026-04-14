/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      fontFamily: {
        sans: [
          'Inter',
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          'sans-serif',
        ],
      },
      colors: {
        brand: {
          50: '#eef6ff',
          100: '#d9eaff',
          200: '#bcdbff',
          300: '#8ec4ff',
          400: '#59a4ff',
          500: '#2f82ff',
          600: '#1864f5',
          700: '#134fdb',
          800: '#1642af',
          900: '#173c8a',
          950: '#0f2556',
        },
        ink: {
          50: '#f6f7f9',
          100: '#eceef3',
          200: '#d5dae4',
          300: '#b0bacb',
          400: '#8693ab',
          500: '#677690',
          600: '#515e76',
          700: '#424c60',
          800: '#394151',
          900: '#0d1220',
          950: '#070a15',
        },
      },
      backgroundImage: {
        'grid-light':
          'linear-gradient(to right, rgba(15,23,42,0.06) 1px, transparent 1px), linear-gradient(to bottom, rgba(15,23,42,0.06) 1px, transparent 1px)',
        'grid-dark':
          'linear-gradient(to right, rgba(255,255,255,0.06) 1px, transparent 1px), linear-gradient(to bottom, rgba(255,255,255,0.06) 1px, transparent 1px)',
        'radial-fade':
          'radial-gradient(ellipse 80% 60% at 50% 0%, rgba(47,130,255,0.25), transparent 70%)',
      },
      boxShadow: {
        glow: '0 10px 40px -10px rgba(47,130,255,0.45)',
        soft: '0 1px 2px rgba(15,23,42,0.04), 0 8px 24px -12px rgba(15,23,42,0.12)',
      },
      keyframes: {
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-6px)' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(12px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        float: 'float 6s ease-in-out infinite',
        shimmer: 'shimmer 3s linear infinite',
        'fade-up': 'fade-up 0.6s ease-out both',
      },
    },
  },
  plugins: [],
};
