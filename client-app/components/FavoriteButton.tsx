'use client'

import { useFavorite } from '@/lib/favorites'
import { useT } from '@/lib/i18n'

/**
 * The heart toggle overlaid on a listing card.
 *
 * <p>Always render this as a sibling of the card's own link, inside a
 * `position: relative` wrapper — never nested inside the `<a>` itself. The
 * click handler stops propagation so tapping the heart doesn't also follow
 * the card into the listing page, but a `<button>` nested inside an `<a>` is
 * invalid HTML regardless, so the two elements need to be siblings.
 */
export function FavoriteButton(
  { listingId, className }: { listingId: string; className?: string },
) {
  const t = useT()
  const [saved, toggle] = useFavorite(listingId)

  return (
    <button
      type="button"
      className={`supply-card__fav${className ? ` ${className}` : ''}`}
      aria-pressed={saved}
      aria-label={t(saved ? 'favorites.remove' : 'favorites.add')}
      onClick={(event) => {
        event.preventDefault()
        event.stopPropagation()
        toggle()
      }}
    >
      <svg viewBox="0 0 24 24" fill={saved ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="2">
        <path
          strokeLinejoin="round"
          strokeLinecap="round"
          d="M12 20.5s-7.6-4.6-10.1-9.4C.4 7.7 2.3 4.3 5.8 3.9c2-.2 3.9.7 5 2.4 1.1-1.7 3-2.6 5-2.4 3.5.4 5.4 3.8 3.9 7.2-2.5 4.8-10.1 9.4-10.1 9.4z"
        />
      </svg>
    </button>
  )
}
