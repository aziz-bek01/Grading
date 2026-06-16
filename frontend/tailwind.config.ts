import type { Config } from 'tailwindcss';

/**
 * Design tokens — mirror docs/mvp1/07-design-foundation.md §3.
 * No hardcoded colors anywhere else: only the names exposed here.
 */
const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // HRL brand blue — gradient start. See docs/mvp1/07b-hrl-brand-tokens.md §1.
        primary: {
          50: '#EEF2FD',
          100: '#D6E0FA',
          200: '#AEC0F4',
          300: '#7C95EC',
          400: '#3F63DC',
          500: '#0739B9',
          600: '#062F9C',
          700: '#06277F',
          800: '#051E63',
          900: '#041444',
          DEFAULT: '#0739B9',
        },
        // HRL brand violet/magenta — gradient far end. See 07b §2.
        accent: {
          50: '#FAF0FE',
          100: '#F1DCFD',
          200: '#E1B8FB',
          300: '#C983F8',
          400: '#B14FF6',
          500: '#9529F4',
          600: '#AB27FD',
          700: '#7A1FC9',
          DEFAULT: '#9529F4',
        },
        surface: '#FFFFFF',
        background: '#F8FAFC',
        border: '#E2E8F0',
        'border-strong': '#CBD5E1',
        divider: '#EDF2F7',
        'text-primary': '#0F172A',
        'text-secondary': '#475569',
        'text-muted': '#64748B',
        'text-inverse': '#FFFFFF',
        'text-disabled': '#94A3B8',
        success: { 50: '#ECFDF5', 500: '#10B981', 600: '#059669', 700: '#047857', DEFAULT: '#10B981' },
        warning: { 50: '#FFFBEB', 500: '#F59E0B', 600: '#D97706', 700: '#B45309', DEFAULT: '#F59E0B' },
        danger: { 50: '#FEF2F2', 500: '#EF4444', 600: '#DC2626', 700: '#B91C1C', DEFAULT: '#EF4444' },
        info: { 50: '#EFF6FF', 500: '#3B82F6', 600: '#2563EB', DEFAULT: '#3B82F6' },
        // a11y (Batch 6): darkened slate-500 → slate-600 so the locked badge's
        // 12px label clears WCAG AA (4.5:1) on `locked-bg`. Was #64748B (4.34:1).
        locked: '#475569',
        'locked-bg': '#F1F5F9',
        // Deep teal — reserved security signal, deliberately off the brand axis. See 07b §4.
        'salary-sensitive': '#0F766E',
        'salary-sensitive-bg': '#ECFDF8',
        // a11y (Batch 6): darkened sky-500 → sky-700 so AI-suggestion text/badge
        // clears WCAG AA on `ai-suggestion-bg`. Was #0EA5E9 (2.60:1 — failing).
        // The brighter sky stays available as `chart-6` for non-text chart fills.
        'ai-suggestion': '#0369A1',
        'ai-suggestion-bg': '#F0F9FF',
        'audit-alert': '#DC2626',
        'audit-alert-bg': '#FEF2F2',
        // Chart palette — brand-harmonious, 8 distinguishable series. See 07b §5.
        'chart-1': '#0739B9', // brand blue (primary)
        'chart-2': '#9529F4', // brand violet (accent)
        'chart-3': '#10B981', // success green
        'chart-4': '#F59E0B', // warning amber
        'chart-5': '#0F766E', // deep teal (salary band — matches salary-sensitive)
        'chart-6': '#0EA5E9', // sky (AI / info series)
        'chart-7': '#64748B', // slate (neutral)
        'chart-8': '#DC2626', // danger (semantic only)
      },
      fontFamily: {
        sans: [
          'Inter',
          'ui-sans-serif',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          '"Helvetica Neue"',
          'Arial',
          '"PT Sans"',
          'sans-serif',
        ],
        mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
        tabular: ['Inter', 'system-ui'],
      },
      fontSize: {
        xs: ['12px', { lineHeight: '16px', fontWeight: '500' }],
        sm: ['14px', { lineHeight: '20px' }],
        base: ['15px', { lineHeight: '22px' }],
        md: ['16px', { lineHeight: '24px', fontWeight: '500' }],
        lg: ['18px', { lineHeight: '26px', fontWeight: '600' }],
        xl: ['20px', { lineHeight: '28px', fontWeight: '600' }],
        '2xl': ['24px', { lineHeight: '32px', fontWeight: '600' }],
        '3xl': ['30px', { lineHeight: '38px', fontWeight: '600' }],
        '4xl': ['36px', { lineHeight: '44px', fontWeight: '700' }],
      },
      borderRadius: {
        none: '0',
        sm: '4px',
        md: '6px',
        lg: '8px',
        xl: '12px',
        '2xl': '16px',
        full: '9999px',
      },
      boxShadow: {
        sm: '0 1px 2px rgba(15, 23, 42, 0.04)',
        DEFAULT:
          '0 1px 3px rgba(15, 23, 42, 0.06), 0 1px 2px rgba(15, 23, 42, 0.04)',
        md: '0 4px 6px -1px rgba(15, 23, 42, 0.08), 0 2px 4px -1px rgba(15, 23, 42, 0.04)',
        lg: '0 10px 15px -3px rgba(15, 23, 42, 0.08), 0 4px 6px -2px rgba(15, 23, 42, 0.04)',
        focus: '0 0 0 3px rgba(7, 57, 185, 0.30)',
      },
      backgroundImage: {
        // HRL signature gradient (blue → violet → magenta). See 07b §3.
        brand: 'linear-gradient(135deg, #0739B9 0%, #6E2EE4 55%, #AB27FD 100%)',
        'brand-wash': 'linear-gradient(160deg, #F4F1FB 0%, #FAF0FE 100%)',
      },
    },
  },
  plugins: [],
};

export default config;
