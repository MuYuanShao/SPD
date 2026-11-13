import { defineStore } from 'pinia'

export type ThemeId = 'medical-blue' | 'warm-yellow' | 'forest-green'

export interface ThemeDefinition {
  id: ThemeId
  name: string
  hint: string
  /** 用于主题切换器圆点的颜色预览（依次从主色到强调色） */
  swatch: string
}

/**
 * 调色板：用户可自选的业务主色。
 * 选中后通过内联 CSS 变量写入 :root，覆盖主题默认主色，
 * 全站按钮、选中态、标签、进度条等统一跟随。
 */
export interface PaletteColor {
  id: string
  name: string
  /** CSS 颜色值 */
  color: string
}

/** 默认主色：rgb(3, 179, 178) */
export const DEFAULT_PRIMARY_COLOR = 'rgb(3, 179, 178)'

export const PALETTE_COLORS: PaletteColor[] = [
  { id: 'teal', name: '青玉', color: DEFAULT_PRIMARY_COLOR },
  { id: 'medical-blue', name: '医疗蓝', color: 'rgb(24, 144, 255)' },
  { id: 'forest-green', name: '森林绿', color: 'rgb(82, 196, 26)' },
  { id: 'sunset-orange', name: '暖阳橙', color: 'rgb(250, 140, 22)' },
  { id: 'rose', name: '玫瑰粉', color: 'rgb(235, 47, 150)' },
  { id: 'indigo', name: '靛青蓝', color: 'rgb(79, 70, 229)' },
  { id: 'slate', name: '岩板灰', color: 'rgb(71, 85, 105)' }
]

export const THEMES: ThemeDefinition[] = [
  {
    id: 'medical-blue',
    name: '医疗蓝',
    hint: '专业冷静 · 临床标准',
    swatch: 'linear-gradient(135deg, #1890ff 0%, #40a9ff 100%)'
  },
  {
    id: 'warm-yellow',
    name: '暖意黄',
    hint: '柔和亲切 · 病区关怀',
    swatch: 'linear-gradient(135deg, #faad14 0%, #ffc53d 100%)'
  },
  {
    id: 'forest-green',
    name: '森林绿',
    hint: '清新自然 · 长期使用',
    swatch: 'linear-gradient(135deg, #52c41a 0%, #73d13d 100%)'
  }
]

const STORAGE_KEY = 'spd.theme'
const PRIMARY_STORAGE_KEY = 'spd.theme.primary'
const DEFAULT_THEME: ThemeId = 'medical-blue'

/** 把 hex / rgb 颜色与目标色混合，ratio = 0 返回原色，1 返回目标色 */
function mixColor(color: string, target: [number, number, number], ratio: number): string {
  const rgb = parseColor(color)
  if (!rgb) return color
  const r = Math.round(rgb[0] + (target[0] - rgb[0]) * ratio)
  const g = Math.round(rgb[1] + (target[1] - rgb[1]) * ratio)
  const b = Math.round(rgb[2] + (target[2] - rgb[2]) * ratio)
  return `rgb(${r}, ${g}, ${b})`
}

function parseColor(color: string): [number, number, number] | null {
  const value = color.trim().toLowerCase()
  const rgbMatch = value.match(/^rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)/)
  if (rgbMatch) {
    return [Number(rgbMatch[1]), Number(rgbMatch[2]), Number(rgbMatch[3])]
  }
  const hexMatch = value.match(/^#([0-9a-f]{3}|[0-9a-f]{6})$/)
  if (hexMatch) {
    const hex = hexMatch[1]
    const full = hex.length === 3 ? hex.split('').map((c) => c + c).join('') : hex
    return [
      parseInt(full.slice(0, 2), 16),
      parseInt(full.slice(2, 4), 16),
      parseInt(full.slice(4, 6), 16)
    ]
  }
  return null
}

/** 将主色写入 :root 内联变量，覆盖主题默认主色 */
export function applyPrimaryColor(color: string) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.style.setProperty('--theme-primary', color)
  root.style.setProperty('--theme-primary-strong', mixColor(color, [0, 0, 0], 0.22))
  root.style.setProperty('--theme-accent', mixColor(color, [255, 255, 255], 0.22))
  root.style.setProperty('--theme-focus', mixColor(color, [255, 255, 255], 0.3))
}

function readStoredTheme(): ThemeId {
  if (typeof window === 'undefined') return DEFAULT_THEME
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (raw && THEMES.some((t) => t.id === raw)) {
      return raw as ThemeId
    }
  } catch {
    // 忽略 localStorage 异常
  }
  return DEFAULT_THEME
}

function readStoredPrimaryColor(): string {
  if (typeof window === 'undefined') return DEFAULT_PRIMARY_COLOR
  try {
    const raw = window.localStorage.getItem(PRIMARY_STORAGE_KEY)
    if (raw && PALETTE_COLORS.some((c) => c.color === raw)) {
      return raw
    }
  } catch {
    // 忽略 localStorage 异常
  }
  return DEFAULT_PRIMARY_COLOR
}

function applyTheme(theme: ThemeId) {
  if (typeof document === 'undefined') return
  if (theme === DEFAULT_THEME) {
    document.documentElement.removeAttribute('data-theme')
  } else {
    document.documentElement.setAttribute('data-theme', theme)
  }
}

// 启动时立刻应用持久化主题与主色，避免出现「无主题→主题」闪烁
applyTheme(readStoredTheme())
applyPrimaryColor(readStoredPrimaryColor())

export const useThemeStore = defineStore('theme', {
  state: () => ({
    current: readStoredTheme(),
    /** 当前业务主色（来自调色板） */
    primaryColor: readStoredPrimaryColor()
  }),
  getters: {
    palette: () => PALETTE_COLORS,
    currentPaletteColor(state): PaletteColor {
      return (
        PALETTE_COLORS.find((c) => c.color === state.primaryColor) ??
        PALETTE_COLORS[0]
      )
    }
  },
  actions: {
    setTheme(theme: ThemeId) {
      this.current = theme
      applyTheme(theme)
      try {
        window.localStorage.setItem(STORAGE_KEY, theme)
      } catch {
        // 忽略写入失败
      }
    },
    /** 选择调色板主色 */
    setPrimaryColor(color: string) {
      this.primaryColor = color
      applyPrimaryColor(color)
      try {
        window.localStorage.setItem(PRIMARY_STORAGE_KEY, color)
      } catch {
        // 忽略写入失败
      }
    },
    init() {
      applyTheme(this.current)
      applyPrimaryColor(this.primaryColor)
    }
  }
})
