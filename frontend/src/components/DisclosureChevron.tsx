// Ícono compartido: un SVG evita que la flecha de texto se desplace
// según la fuente, el sistema operativo o el line-height del botón.
export default function DisclosureChevron() {
  return (
    <svg
      className="disclosure-chevron"
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
      focusable="false"
    >
      <path
        d="m6 9 6 6 6-6"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
