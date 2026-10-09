import { defineStore } from 'pinia'

export type ThemeId = 'medical-blue' | 'warm-yellow' | 'forest-green'

export interface ThemeDefinition {
  id: ThemeId
  name: string
  hint: string
  /** 用于主题切换器圆点的颜色预览（依次从主色到强调色） */
  swatch: string
}

export const THEMES: ThemeDefinition[] = [
  {
    id: 'medical-blue',
    name: '医疗蓝',
    hint: '专业冷静 · 临床标准',
    swatch: 'linear-gradient(135deg, #1890ff 0%, #40a9ff 100%)',
  },
  {
    id: 'warm-yellow',
    name: '暖意黄',
    hint: '柔和亲切 · 病区关怀',
    swatch: 'linear-gradient(135deg, #faad14 0%, #ffc53d 100%)',
  },
  {
    id: 'forest-green',
    name: '森林绿',
    hint: '清新自然 · 长期使用',
    swatch: 'linear-gradient(135deg, #52c41a 0%, #73d13d 100%)',
  },
]

const STORAGE_KEY = 'spd.theme'
const DEFAULT_THEME: ThemeId = 'medical-blue'

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

function applyTheme(theme: ThemeId) {
  if (typeof document === 'undefined') return
  if (theme === DEFAULT_THEME) {
    document.documentElement.removeAttribute('data-theme')
  } else {
    document.documentElement.setAttribute('data-theme', theme)
  }
}

// 启动时立刻应用持久化主题，避免出现「无主题→主题」闪烁
applyTheme(readStoredTheme())

export const useThemeStore = defineStore('theme', {
  state: () => ({
    current: readStoredTheme(),
  }),
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
    init() {
      applyTheme(this.current)
    },
  },
})
