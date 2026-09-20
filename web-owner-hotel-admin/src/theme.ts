import type { ThemeConfig } from 'antd'

/**
 * The portal's half of one design system.
 *
 * <p>The palette is the guest app's, deliberately. A host who books a stay on
 * Saturday and manages their listing on Monday should recognise the same product,
 * and an admin comparing a listing page against the review queue should not have
 * to translate between two sets of blues.
 *
 * <p>Kept as antd tokens rather than CSS overrides so every component — including
 * the ones these screens have not used yet — inherits it without being restyled
 * one at a time.
 */

/** Shared with the guest app's globals.css. Change both together. */
export const palette = {
  brand: '#2563eb',
  brandStrong: '#1d4ed8',
  brandSoft: '#eaf1ff',
  accent: '#f59e0b',
  accentSoft: '#fff5e3',
  teal: '#0d9488',
  tealSoft: '#e3f7f4',
  violet: '#7c3aed',
  rose: '#e11d48',
  ink: '#0f1729',
  inkMuted: '#5a6478',
  line: '#e5e9f0',
  surface: '#ffffff',
  ground: '#f6f8fc',
  danger: '#dc2626',
  success: '#059669',
  warning: '#d97706',
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

const FONT_STACK = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, "
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
    boxShadow: '0 1px 2px rgba(15, 23, 41, 0.04), 0 8px 24px rgba(37, 99, 235, 0.07)',
    boxShadowSecondary: '0 1px 2px rgba(15, 23, 41, 0.04), 0 4px 12px rgba(37, 99, 235, 0.06)',
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
      primaryShadow: '0 1px 2px rgba(37, 99, 235, 0.2)',
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
