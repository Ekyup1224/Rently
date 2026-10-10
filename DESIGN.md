# Rently brand & design system

This is the single source of truth for this product's brand identity. Any UI
change — in `client-app` or `web-owner-hotel-admin`, from Cursor, from Claude
in any session, or by hand — reuses what's here rather than introducing a new
color, font, or mark. If something isn't covered here, match the nearest
existing pattern in `client-app/app/globals.css` or
`web-owner-hotel-admin/src/theme.ts` rather than inventing a new one.

The hexes below are a quick-reference copy. The CSS custom properties in
`globals.css` and the `palette` object in `theme.ts` are the actual source of
truth — if the two ever disagree, the code wins and this file is stale and
needs updating in the same change.

## Naming — read this first

The product's in-app name is **"Rently"**. The earlier "Stay" placeholder was
renamed on request, so page titles, the PWA manifest `name`/`short_name`, the
guest header wordmark and the admin sidebar label all read "Rently" now.

Two things that deliberately did *not* change, and shouldn't be "fixed":

- **The common noun.** "Stay" still means a booking in plenty of places —
  `minStay`, `trips.yourStay`, `search.typeOfStay`, the admin's "Stay" /
  "Stays" column headings, "Stay closed." Those are correct English about a
  guest's stay, not leftovers. Renaming them would be a bug.
- **Infrastructure identifiers.** The Java package `mn.innex.stay`, the
  Postgres database and role `stay`, the `stay-*` container names, the
  `stay-media` bucket and `spring.application.name: stay-backend` are all
  still "stay". None of them is user-visible, and renaming the database in
  particular means recreating the volume. Treat those as a separate,
  deliberate migration rather than part of a branding pass.

The brand *mark* and palette below are final regardless.

## Brand mark

The toono — the roof-crown of a traditional Mongolian ger — not a generic
house icon. Chosen for being distinctive to this market rather than another
Airbnb-style house glyph, and because its radial symmetry stays legible at a
16px favicon.

Source files (edit these, never the PNGs):
- `client-app/public/brand/mark.svg` / `web-owner-hotel-admin/public/brand/mark.svg` — flat terracotta square, full bleed, no corner rounding baked in (OS/browser applies its own mask). This is what `icon-192.png`, `icon-512.png`, and `apple-icon.png` are rendered from.
- `client-app/public/brand/mark-mono.svg` / the admin equivalent — outline only, colored via `currentColor`, for sitting inline on an existing background (see the admin sidebar badge in `AppShell.tsx`).

Re-render the PNGs after editing `mark.svg` (from `client-app/`):
```
rsvg-convert -w 512 -h 512 -o public/icon-512.png public/brand/mark.svg
rsvg-convert -w 192 -h 192 -o public/icon-192.png public/brand/mark.svg
rsvg-convert -w 180 -h 180 -o public/apple-icon.png public/brand/mark.svg
```
`app/favicon.ico` is generated from the same source as a multi-size ICO (16
through 256) so the browser can pick its own size for the tab.

These were ImageMagick `convert` commands; this machine has `rsvg-convert`
(librsvg) and no ImageMagick, so they are written for that instead.

Do: keep it a flat, single-color terracotta fill. Give it clearspace equal to
half its own width on every side. Don't: gradient it, drop-shadow it, outline
it, or recolor it to anything but the reversed (`--ground` on `--brand`)
variant.

## Color

Semantic tokens, not raw hex, in both apps — reach for `var(--brand)` /
`palette.brand`, never `#b84b2a` typed inline.

| Token | Hex | Use |
|---|---|---|
| `brand` | `#b84b2a` | Primary actions, the mark, focus rings |
| `brand-strong` | `#943a1f` | Hover/pressed state of a brand fill |
| `brand-soft` | `#fbe4d9` | Tinted background behind brand-colored text |
| `accent` | `#a15a00` (client-app) / `#c8860a` (admin charts) | Secondary highlight — deliberately different shades: charts need more chroma than body text does |
| `teal` | `#0f8f7d` | Confirmed/verified/success — teal, not green, so it doesn't collide with `danger` for a color-blind reader |
| `teal-soft` | `#dceeea` | Tinted background for teal text |
| `ink` | `#2b211b` | Primary text |
| `ink-muted` | `#6b5d54` | Secondary/metadata text |
| `line` | `#e6d8cb` | Borders, dividers |
| `surface` | `#ffffff` | Cards |
| `ground` | `#fbf6f1` | Page background |
| `danger` | `#c23b3b` | Errors, cancellations, destructive actions |

Shadows are warm-tinted (mixed from ink/brand, never pure black) — see
`--shadow-sm/--shadow/--shadow-lg` in `globals.css`.

The admin chart palette (`theme.ts`'s `chartColours`) is validated
separately with the `dataviz` skill's palette checker — don't hand-edit it
without re-running that validation; see the comment above it in `theme.ts`.

## Type

Two families, both already loaded — never swap in Inter, Roboto, or another
"trendy" default:

- **PT Serif** (`--font-display` / admin's display use) — hero headings and
  the wordmark only. Chosen over Fraunces specifically because this is a
  Mongolian-first product (`lang="mn"`) and PT's Cyrillic support was built
  in, not bolted on.
- **PT Sans** (`--font-sans` / `FONT_STACK` in admin) — everything else: body
  copy, labels, buttons, prices. Chosen over Plus Jakarta Sans for the same
  Cyrillic reason.

## Shape, spacing, motion

Nothing in either app uses a hard 0 or 4px corner — radius scales with how
"held" an element feels (a chip vs. a card vs. a sheet); see the `--radius*`
tokens in `globals.css`. Chips (`.chip` / `FilterChip`-style toggles) invert
to a solid `ink` fill when selected, never `brand` — brand stays reserved for
the one primary action per screen. Keep motion to confirmations, not
decoration: a ~150ms ease on hover/press, nothing that loops or auto-plays.

## Where the full rationale lives

The private "Rently Brand Identity" canvas artifact has the logo concepts
that were considered and rejected, the full lockup variants, and in-context
mockups (browser tab, home screen, icon at every size). This file is the
condensed, code-adjacent version of it for day-to-day work.
