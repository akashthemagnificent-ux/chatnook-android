import type { Config } from 'tailwindcss'

/**
 * Design tokens live in `src/styles/globals.css`; this file only maps them to
 * Tailwind names so no component ever hardcodes a color or size (spec §1, §5).
 */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: ['class', '.theme-dark'],
  theme: {
    extend: {
      colors: {
        base: 'rgb(var(--bg-base) / <alpha-value>)',
        surface: 'rgb(var(--bg-surface) / <alpha-value>)',
        elevated: 'rgb(var(--bg-elevated) / <alpha-value>)',
        hover: 'rgb(var(--bg-hover) / <alpha-value>)',
        active: 'rgb(var(--bg-active) / <alpha-value>)',
        line: 'rgb(var(--line) / <alpha-value>)',
        brand: {
          DEFAULT: 'rgb(var(--brand) / <alpha-value>)',
          strong: 'rgb(var(--brand-strong) / <alpha-value>)',
          glow: 'rgb(var(--brand-glow) / <alpha-value>)',
        },
        ink: {
          primary: 'rgb(var(--text-primary) / <alpha-value>)',
          secondary: 'rgb(var(--text-secondary) / <alpha-value>)',
          muted: 'rgb(var(--text-muted) / <alpha-value>)',
          accent: 'rgb(var(--text-on-accent) / <alpha-value>)',
        },
        success: 'rgb(var(--success) / <alpha-value>)',
        warning: 'rgb(var(--warning) / <alpha-value>)',
        danger: 'rgb(var(--danger) / <alpha-value>)',
        info: 'rgb(var(--info) / <alpha-value>)',
      },
      fontFamily: {
        sans: ['Inter Variable', 'Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      fontSize: {
        xs: ['0.75rem', { lineHeight: '1.05rem' }],
        sm: ['0.8125rem', { lineHeight: '1.15rem' }],
        base: ['0.875rem', { lineHeight: '1.4rem' }],
        md: ['1rem', { lineHeight: '1.5rem' }],
        lg: ['1.125rem', { lineHeight: '1.625rem' }],
        xl: ['1.25rem', { lineHeight: '1.75rem' }],
        title: ['1.5rem', { lineHeight: '1.875rem', letterSpacing: '-0.025em' }],
        display: ['2rem', { lineHeight: '2.25rem', letterSpacing: '-0.035em' }],
      },
      borderRadius: {
        sm: '6px',
        md: '10px',
        lg: '14px',
        xl: '20px',
        '2xl': '16px',
        '3xl': '24px',
      },
      boxShadow: {
        soft: '0 8px 24px -14px rgb(0 0 0 / 0.55)',
        pop: '0 24px 70px -28px rgb(0 0 0 / 0.8)',
        pane: 'inset 0 1px 0 rgb(var(--line) / 0.05), 0 30px 70px -42px rgb(0 0 0 / 0.75)',
        lift: '0 14px 36px -18px rgb(0 0 0 / 0.65)',
        glow: '0 0 0 1px rgb(var(--brand) / 0.22), 0 10px 34px -12px rgb(var(--brand) / 0.45)',
      },
      backgroundImage: {
        'brand-sheen':
          'linear-gradient(110deg, rgb(var(--brand-strong)), rgb(var(--brand)) 45%, rgb(var(--brand-glow)) 55%, rgb(var(--brand-strong)))',
        sheen: 'linear-gradient(110deg, transparent 20%, rgb(255 255 255 / 0.22) 48%, transparent 76%)',
      },
      transitionTimingFunction: {
        out: 'cubic-bezier(0.22, 1, 0.36, 1)',
        spring: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
      },
      keyframes: {
        // Use the individual `translate`/`scale` properties, not `transform`:
        // a transform animation would override the -translate-x/y centering
        // utilities on DialogContent while fill-mode keeps the last frame.
        'fade-up': {
          from: { opacity: '0', translate: '0 6px' },
          to: { opacity: '1', translate: '0 0' },
        },
        'scale-in': {
          from: { opacity: '0', scale: '0.96' },
          to: { opacity: '1', scale: '1' },
        },
        'message-in': {
          from: { opacity: '0', translate: '0 8px', scale: '0.985' },
          to: { opacity: '1', translate: '0 0', scale: '1' },
        },
        'pop-in': {
          '0%': { opacity: '0', scale: '0.6' },
          '60%': { opacity: '1', scale: '1.08' },
          '100%': { opacity: '1', scale: '1' },
        },
        'dot-pulse': {
          '0%, 60%, 100%': { opacity: '0.35', transform: 'translateY(0)' },
          '30%': { opacity: '1', transform: 'translateY(-2px)' },
        },
        'slide-from-left': {
          from: { transform: 'translateX(-100%)' },
          to: { transform: 'translateX(0)' },
        },
        'slide-from-right': {
          from: { transform: 'translateX(100%)' },
          to: { transform: 'translateX(0)' },
        },
        'slide-from-bottom': {
          from: { transform: 'translateY(100%)' },
          to: { transform: 'translateY(0)' },
        },
        'fade-in': {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
      },
      animation: {
        'fade-up': 'fade-up 240ms cubic-bezier(0.22, 1, 0.36, 1) both',
        'scale-in': 'scale-in 200ms cubic-bezier(0.22, 1, 0.36, 1) both',
        'message-in': 'message-in 220ms cubic-bezier(0.22, 1, 0.36, 1) both',
        'pop-in': 'pop-in 280ms cubic-bezier(0.34, 1.56, 0.64, 1) both',
        'dot-pulse': 'dot-pulse 1.2s ease-in-out infinite',
        'slide-from-left': 'slide-from-left 260ms cubic-bezier(0.22, 1, 0.36, 1) both',
        'slide-from-right': 'slide-from-right 260ms cubic-bezier(0.22, 1, 0.36, 1) both',
        'slide-from-bottom': 'slide-from-bottom 260ms cubic-bezier(0.22, 1, 0.36, 1) both',
        'fade-in': 'fade-in 200ms ease-out both',
        aurora: 'aurora-drift 22s ease-in-out infinite alternate',
        'aurora-alt': 'aurora-drift-alt 28s ease-in-out infinite alternate',
        'border-spin': 'border-spin 5s linear infinite',
        shine: 'shine 4.2s ease-in-out infinite',
        float: 'float-y 7s ease-in-out infinite',
        'pulse-ring': 'pulse-ring 2.4s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        glow: 'glow-breathe 5s ease-in-out infinite',
      },
    },
  },
  plugins: [],
} satisfies Config
