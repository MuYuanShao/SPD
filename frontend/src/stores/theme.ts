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

/**
 * 默认主色：低明艳青玉色。
 * 在原 rgb(3,179,178) 青玉基础上继续降低明艳度（明度 70% → 43%），
 * 降低长时间注视的刺眼感；文字仍保持深色，不使用高饱和色。
 */
export const DEFAULT_PRIMARY_COLOR = 'rgb(31, 111, 109)'

export const PALETTE_COLORS: PaletteColor[] = [
  { id: 'teal', name: '青玉', color: DEFAULT_PRIMARY_COLOR },
  { id: 'medical-blue', name: '医疗蓝', color: 'rgb(31, 95, 125)' },
  { id: 'forest-green', name: '森林绿', color: 'rgb(47, 125, 91)' },
  { id: 'sunset-orange', name: '暖阳橙', color: 'rgb(154, 97, 20)' },
  { id: 'rose', name: '玫瑰粉', color: 'rgb(163, 58, 48)' },
  { id: 'indigo', name: '靛青蓝', color: 'rgb(63, 77, 129)' },
  { id: 'slate', name: '岩板灰', color: 'rgb(91, 107, 118)' }
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
const FONT_SIZE_STORAGE_KEY = 'spd.theme.fontSize'
const DEFAULT_THEME: ThemeId = 'medical-blue'

/* ============== 字号档位 ==============
   面向 30 岁以上用户，提供小 / 中 / 大三档。
   通过 <html data-font-size="..."> 驱动整站字号体系。

   档位 id 与像素值沿用历史命名（standard / large / xlarge），
   以兼容既有 localStorage 持久化数据与 CSS 选择器；
   用户可见名称改为「小 / 中 / 大」，其中「中」为默认档。 */
export type FontSizeId = 'standard' | 'large' | 'xlarge'

export interface FontSizeDefinition {
  id: FontSizeId
  name: string
  /** 基准字号 */
  base: string
  hint: string
}

export const FONT_SIZES: FontSizeDefinition[] = [
  { id: 'standard', name: '小', base: '14px', hint: '常规显示器 · 密度优先' },
  { id: 'large', name: '中', base: '16px', hint: '默认档 · 日常办公' },
  { id: 'xlarge', name: '大', base: '18px', hint: '视力偏弱 · 高分辨率远距' }
]

export const DEFAULT_FONT_SIZE: FontSizeId = 'large'

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

/**
 * 将字号档位写入 <html data-font-size>，驱动整站字号体系。
 *
 * 默认档「中」不写属性（removeAttribute），
 * 因此 tokens/typography.css 中 :root 的基准字号就是「中」的 16px，
 * :root[data-font-size='standard'] 为「小」14px，
 * :root[data-font-size='xlarge'] 为「大」18px。
 */
export function applyFontSize(id: FontSizeId) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  if (id === DEFAULT_FONT_SIZE) {
    root.removeAttribute('data-font-size')
  } else {
    root.setAttribute('data-font-size', id)
  }
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

function readStoredFontSize(): FontSizeId {
  if (typeof window === 'undefined') return DEFAULT_FONT_SIZE
  try {
    const raw = window.localStorage.getItem(FONT_SIZE_STORAGE_KEY)
    if (raw && FONT_SIZES.some((f) => f.id === raw)) {
      return raw as FontSizeId
    }
  } catch {
    // 忽略 localStorage 异常
  }
  return DEFAULT_FONT_SIZE
}

function applyTheme(theme: ThemeId) {
  if (typeof document === 'undefined') return
  if (theme === DEFAULT_THEME) {
    document.documentElement.removeAttribute('data-theme')
  } else {
    document.documentElement.setAttribute('data-theme', theme)
  }
}

// 启动时立刻应用持久化主题、主色与字号，避免出现「无主题→主题」闪烁
applyTheme(readStoredTheme())
applyPrimaryColor(readStoredPrimaryColor())
applyFontSize(readStoredFontSize())

export const useThemeStore = defineStore('theme', {
  state: () => ({
    current: readStoredTheme(),
    /** 当前业务主色（来自调色板） */
    primaryColor: readStoredPrimaryColor(),
    /** 当前字号档位 */
    fontSize: readStoredFontSize()
  }),
  getters: {
    palette: () => PALETTE_COLORS,
    fontSizes: () => FONT_SIZES,
    currentPaletteColor(state): PaletteColor {
      return (
        PALETTE_COLORS.find((c) => c.color === state.primaryColor) ??
        PALETTE_COLORS[0]
      )
    },
    currentFontSize(state): FontSizeDefinition {
      return FONT_SIZES.find((f) => f.id === state.fontSize) ?? FONT_SIZES[0]
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
    /** 切换字号档位（小 / 中 / 大） */
    setFontSize(id: FontSizeId) {
      this.fontSize = id
      applyFontSize(id)
      try {
        window.localStorage.setItem(FONT_SIZE_STORAGE_KEY, id)
      } catch {
        // 忽略写入失败
      }
    },
    init() {
      applyTheme(this.current)
      applyPrimaryColor(this.primaryColor)
      applyFontSize(this.fontSize)
    }
  }
})
