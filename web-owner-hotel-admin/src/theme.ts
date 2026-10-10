import type { ThemeConfig } from 'antd'

/**
 * The portal's half of one design system.
 *
 * <p>The palette is the guest app's, deliberately. A host who books a stay on
 * Saturday and manages their listing on Monday should recognise the same product,
 * and an admin comparing a listing page against the review queue should not have
 * to translate between two sets of colours.
 *
 * <p>Kept as antd tokens rather than CSS overrides so every component — including
 * the ones these screens have not used yet — inherits it without being restyled
 * one at a time.
 */

/** Shared with the guest app's globals.css. Change both together. */
export const palette = {
  brand: '#b84b2a',
  brandStrong: '#943a1f',
  brandSoft: '#fbe4d9',
  accent: '#c8860a',
  accentSoft: '#fbeedd',
  teal: '#0f8f7d',
  tealSoft: '#dceeea',
  violet: '#7c3aed',
  rose: '#e11d48',
  ink: '#2b211b',
  inkMuted: '#6b5d54',
  line: '#e6d8cb',
  surface: '#ffffff',
  ground: '#fbf6f1',
  danger: '#c23b3b',
  success: '#0f8f7d',
  warning: '#a15a00',
}

/**
 * The series colours for every chart, in order.
 *
 * <p>One list, used everywhere, so the same category keeps the same colour as a
 * reader moves between screens — and so no chart quietly reaches for a colour
 * that means something else in the tags beside it.
 */
export const chartColours = [
  palette.brand,
  palette.teal,
  palette.accent,
  palette.violet,
  palette.rose,
  '#0891b2',
  '#65a30d',
  '#c026d3',
]
// This exact 8-hex set (adjacent order) passed scripts/validate_palette.js from the
// dataviz skill in --mode light: lightness band, chroma floor, CVD separation (worst
// adjacent ΔE 10.8 protan/deutan) and the normal-vision floor (worst ΔE 21.1). The
// gold slot sits under 3:1 contrast by itself — legal only because these charts always
// carry a legend/tooltip (the relief channel), never color alone. Violet and cyan are
// kept from the old palette on purpose: a categorical set needs to spread across the
// hue circle for colour-blind readers, so it is not, and should not be, "all warm".

// Loaded via index.html — see there for why PT Sans/PT Serif rather than a
// trendier pairing (shared reasoning with the guest app's layout.tsx).
const FONT_STACK = "'PT Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, "
  + "'Helvetica Neue', Arial, 'Noto Sans', sans-serif"

export const theme: ThemeConfig = {
  token: {
    colorPrimary: palette.brand,
    colorInfo: palette.brand,
    colorSuccess: palette.success,
    colorWarning: palette.warning,
    colorError: palette.danger,
    colorTextBase: palette.ink,
    colorBgLayout: palette.ground,
    colorBorder: palette.line,
    colorBorderSecondary: palette.line,
    borderRadius: 10,
    borderRadiusLG: 14,
    borderRadiusSM: 8,
    fontFamily: FONT_STACK,
    fontSize: 14,
    // Shadows carry the elevation here, so borders can stay very light without
    // cards dissolving into the background.
    boxShadow: '0 1px 2px rgba(43, 33, 27, 0.05), 0 8px 24px rgba(184, 75, 42, 0.09)',
    boxShadowSecondary: '0 1px 2px rgba(43, 33, 27, 0.05), 0 4px 12px rgba(184, 75, 42, 0.08)',
    wireframe: false,
  },
  components: {
    Layout: {
      headerBg: palette.surface,
      headerHeight: 64,
      bodyBg: palette.ground,
      siderBg: palette.surface,
    },
    Menu: {
      itemBorderRadius: 10,
      itemMarginInline: 10,
      itemHeight: 38,
      itemSelectedBg: palette.brandSoft,
      itemSelectedColor: palette.brandStrong,
      itemHoverBg: palette.ground,
      groupTitleColor: palette.inkMuted,
      groupTitleFontSize: 11,
      iconSize: 15,
    },
    Card: {
      borderRadiusLG: 14,
      paddingLG: 20,
      headerFontSize: 15,
    },
    Statistic: {
      contentFontSize: 26,
      titleFontSize: 13,
    },
    Table: {
      headerBg: palette.ground,
      headerColor: palette.inkMuted,
      headerSplitColor: 'transparent',
      rowHoverBg: palette.brandSoft,
      borderColor: palette.line,
      cellPaddingBlock: 13,
    },
    Tag: {
      borderRadiusSM: 999,
      defaultBg: palette.ground,
    },
    Button: {
      controlHeight: 36,
      fontWeight: 550,
      primaryShadow: '0 1px 2px rgba(184, 75, 42, 0.22)',
    },
    Segmented: {
      itemSelectedBg: palette.surface,
      trackBg: palette.ground,
      borderRadius: 10,
    },
    Descriptions: {
      labelBg: palette.ground,
    },
    Tabs: {
      itemSelectedColor: palette.brandStrong,
      inkBarColor: palette.brand,
      titleFontSize: 14,
    },
  },
}
