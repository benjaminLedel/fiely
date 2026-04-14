type LogoProps = {
  className?: string;
};

export default function Logo({ className = 'h-8 w-8' }: LogoProps) {
  return (
    <svg
      viewBox="0 0 64 64"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      aria-hidden
    >
      <defs>
        <linearGradient id="fiely-logo-g" x1="0" y1="0" x2="64" y2="64">
          <stop offset="0" stopColor="#2f82ff" />
          <stop offset="1" stopColor="#134fdb" />
        </linearGradient>
      </defs>
      <rect width="64" height="64" rx="14" fill="url(#fiely-logo-g)" />
      <path
        d="M18 20a4 4 0 0 1 4-4h10l4 5h10a4 4 0 0 1 4 4v19a4 4 0 0 1-4 4H22a4 4 0 0 1-4-4V20z"
        fill="white"
        fillOpacity="0.95"
      />
      <circle cx="42" cy="34" r="4" fill="#134fdb" />
    </svg>
  );
}
