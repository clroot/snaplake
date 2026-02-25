interface SnaplakeLogoProps {
  className?: string
}

export function SnaplakeLogo({ className }: SnaplakeLogoProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      {/* Stacked snapshot layers */}
      <path d="M4 6h16" />
      <path d="M6 10h12" />
      <path d="M5 14h14" />

      {/* Lake wave */}
      <path d="M3 19c2-2 4-2 6 0s4 2 6 0 4-2 6 0" />
    </svg>
  )
}
