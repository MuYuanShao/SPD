import { defineStore } from 'pinia'

/* ============== 配色方案（Scheme） ==============
   配色方案是全站唯一的外观开关：用户在顶栏主题切换器里选择，
   立即作用于**所有页面**，并持久化到 localStorage。

   每个方案是一整套视觉体系（底色、表格网格、状态条、弹窗），
   对应 CSS 文件在 frontend/src/styles/theme/ 下，
   通过 <html data-spd-theme="..."> 上的属性选择器生效。

   - 标准白（default）：默认清爽，竖线网格 + 斑马纹
   - 静墨宣纸（calm-paper）：暖白低墨量，取消竖网格线与斑马纹
   - 暗夜黑（night-black）：浅字暗底 + 琥珀金强调
   ===================================================================== */
export type SchemeId = 'default' | 'calm-paper' | 'night-black'

export interface SchemeDefinition {
  id: SchemeId
  name: string
  hint: string
  /** 主题切换器里的预览色块（线性渐变） */
  swatch: string
  /** 是否为深色底（影响浏览器滚动条与表单控件的渲染策略） */
  dark?: boolean
}

export const SCHEMES: SchemeDefinition[] = [
  {
    id: 'default',
    name: '标准白',
    hint: '默认清爽 · 竖线网格 + 斑马纹',
    swatch: 'linear-gradient(135deg, #ffffff 0%, #eef3f5 100%)'
  },
  {
    id: 'calm-paper',
    name: '静墨宣纸',
    hint: '暖白低墨量 · 长时间注视不疲劳',
    swatch: 'linear-gradient(135deg, #fdfbf7 0%, #e7edeb 100%)'
  },
  {
    id: 'night-black',
    name: '暗夜黑',
    hint: '浅字暗底 · 琥珀金强调',
    swatch: 'linear-gradient(135deg, #131920 0%, #d8a24a 100%)',
    dark: true
  }
]

const SCHEME_STORAGE_KEY = 'spd.theme.scheme'
const DEFAULT_SCHEME: SchemeId = 'default'

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

function readStoredFontSize(): FontSizeId {
  if (typeof window === 'undefined') return DEFAULT_FONT_SIZE
  try {
    const raw = window.localStorage.getItem('spd.theme.fontSize')
    if (raw && FONT_SIZES.some((f) => f.id === raw)) {
      return raw as FontSizeId
    }
  } catch {
    // 忽略 localStorage 异常
  }
  return DEFAULT_FONT_SIZE
}

function readStoredScheme(): SchemeId {
  if (typeof window === 'undefined') return DEFAULT_SCHEME
  try {
    const raw = window.localStorage.getItem(SCHEME_STORAGE_KEY)
    if (raw && SCHEMES.some((s) => s.id === raw)) {
      return raw as SchemeId
    }
  } catch {
    // 忽略 localStorage 异常
  }
  return DEFAULT_SCHEME
}

/**
 * 将全局配色方案写入 <html data-spd-theme>，驱动全站视觉体系。
 *
 * 「暗夜黑」是深色底，需要额外同步 color-scheme，
 * 让浏览器把滚动条、表单控件、焦点环也切到深色。
 */
function applyScheme(scheme: SchemeId) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  const definition = SCHEMES.find((s) => s.id === scheme)
  if (scheme === DEFAULT_SCHEME || !definition) {
    root.removeAttribute('data-spd-theme')
  } else {
    root.setAttribute('data-spd-theme', scheme)
  }
  root.setAttribute('data-color-scheme', definition?.dark ? 'dark' : 'light')
}

/**
 * 清理已下线的外观选项残留数据。
 *
 * 「主色调色板」（spd.theme / spd.theme.primary）与「主题风格」（data-theme）
 * 已移除，用户不再能切换主色；这里清掉旧版本写下的键，
 * 避免 localStorage 里留下无主的孤儿数据。
 */
function clearLegacyAppearanceStorage() {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem('spd.theme')
    window.localStorage.removeItem('spd.theme.primary')
  } catch {
    // 忽略 localStorage 异常
  }
  if (typeof document !== 'undefined') {
    // 清掉可能残留在 <html> 上的 data-theme 属性
    document.documentElement.removeAttribute('data-theme')
  }
}

// 启动时立刻应用持久化的配色方案与字号，避免出现「无主题→主题」闪烁
clearLegacyAppearanceStorage()
applyScheme(readStoredScheme())
applyFontSize(readStoredFontSize())

export const useThemeStore = defineStore('theme', {
  state: () => ({
    /** 当前全局配色方案（data-spd-theme） */
    scheme: readStoredScheme(),
    /** 当前字号档位 */
    fontSize: readStoredFontSize()
  }),
  getters: {
    fontSizes: () => FONT_SIZES,
    schemes: () => SCHEMES,
    currentScheme(state): SchemeDefinition {
      return SCHEMES.find((s) => s.id === state.scheme) ?? SCHEMES[0]
    },
    currentFontSize(state): FontSizeDefinition {
      return FONT_SIZES.find((f) => f.id === state.fontSize) ?? FONT_SIZES[0]
    }
  },
  actions: {
    /** 切换全局配色方案（静墨宣纸 / 暗夜黑 / 标准白） */
    setScheme(scheme: SchemeId) {
      this.scheme = scheme
      applyScheme(scheme)
      try {
        window.localStorage.setItem(SCHEME_STORAGE_KEY, scheme)
      } catch {
        // 忽略写入失败
      }
    },
    /** 切换字号档位（小 / 中 / 大） */
    setFontSize(id: FontSizeId) {
      this.fontSize = id
      applyFontSize(id)
      try {
        window.localStorage.setItem('spd.theme.fontSize', id)
      } catch {
        // 忽略写入失败
      }
    },
    init() {
      applyScheme(this.scheme)
      applyFontSize(this.fontSize)
    }
  }
})
