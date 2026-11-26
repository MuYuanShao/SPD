# -*- coding: utf-8 -*-
"""TASK 4 step 1: extend useListColumns with order support (normalize + moveColumn + reset)."""
import io
import sys

PATH = 'frontend/src/composables/useListColumns.ts'

OLD_NORMALIZE = """  function normalize(saved: Array<Partial<ListColumnConfig> & { key: string }>): ListColumnConfig[] {
    const map = new Map(saved.map((item) => [item.key, item]))
    return options.defaults.map((fallback) => {
      const previous = map.get(fallback.key)
      const width = Math.min(maxWidth, Math.max(minWidth, Number(previous?.width ?? fallback.width) || fallback.width))
      return {
        key: fallback.key,
        label: fallback.label,
        width,
        visible: previous?.visible !== false,
        fixed: fallback?.fixed ?? false,
        flexible: fallback.flexible ?? false,
        minFlexibleWidth: fallback.minFlexibleWidth ?? (fallback.flexible ? 160 : undefined),
        maxWidth: fallback.maxWidth,
        idealWidth: fallback.idealWidth,
        weight: fallback.weight,
        minWidth: fallback.minWidth,
        // fill \u6765\u81ea\u4ee3\u7801\u91cc\u7684 defaults\uff08\u4e0d\u662f\u7528\u6237\u53ef\u6539\u7684\u6570\u636e\uff09\uff0c
        // \u56e0\u6b64\u59cb\u7ec8\u4ee5 defaults \u4e3a\u51c6\uff0c\u4e0d\u4ece\u6301\u4e45\u5316\u6570\u636e\u8bfb\u53d6\u3002
        fill: fallback.fill === true,
        // \u65e7\u7248\u672c\u6301\u4e45\u5316\u7684\u6570\u636e\u6ca1\u6709 manual \u5b57\u6bb5\uff0c\u4e00\u5f8b\u89c6\u4e3a\u300c\u672a\u624b\u52a8\u62d6\u62fd\u300d\uff0c
        // \u8fd9\u6837\u8001\u7528\u6237\u5347\u7ea7\u540e\u884c\u4e3a\u4e0d\u53d8\uff0c\u76f4\u5230\u4ed6\u81ea\u5df1\u62d6\u4e00\u6b21\u5217\u5bbd\u3002
        manual: previous?.manual === true
      }
    })
  }

  function load(): ListColumnConfig[] {
    try {
      const raw = localStorage.getItem(options.storageKey)
      if (!raw) return options.defaults.map((item) => ({ ...item }))
      return normalize(JSON.parse(raw) as Array<Partial<ListColumnConfig> & { key: string }>)
    } catch {
      return options.defaults.map((item) => ({ ...item }))
    }
  }
"""

NEW_NORMALIZE = """  /**
   * \u628a\u6301\u4e45\u5316\u6570\u636e\u89c4\u6574\u6210\u5b8c\u6574\u7684\u5217\u914d\u7f6e\u6570\u7ec4\u3002
   * ---------------------------------------------------------------------
   * \u3010\u5217\u987a\u5e8f = \u6570\u7ec4\u987a\u5e8f\u3011
   *   \u6301\u4e45\u5316\u6570\u636e\u672c\u8eab\u5c31\u662f\u4e00\u4e2a JSON \u6570\u7ec4\uff0c\u56e0\u6b64**\u6570\u7ec4\u987a\u5e8f\u5373\u5217\u987a\u5e8f**\uff0c
   *   \u65e0\u9700\u65b0\u589e\u5b57\u6bb5\u5373\u53ef\u5411\u540e\u517c\u5bb9\uff08\u65e7\u6570\u636e\u4ecd\u662f\u6570\u7ec4\uff0c\u76f4\u63a5\u6309\u5176\u987a\u5e8f\u89e3\u8bfb\uff09\u3002
   *
   * \u3010normalize \u7684\u987a\u5e8f\u89c4\u5219\uff08\u5173\u952e\uff09\u3011
   *   1) \u5148\u6309**\u5df2\u4fdd\u5b58\u7684\u987a\u5e8f**\u8f93\u51fa\u4fdd\u5b58\u8fc7\u7684\u5217\uff08\u7528\u6237\u624b\u52a8\u8c03\u8fc7\u7684\u987a\u5e8f\u5fc5\u987b\u4fdd\u7559\uff09\uff1b
   *   2) \u518d\u628a**\u4ee3\u7801\u91cc\u65b0\u589e\u3001\u4fdd\u5b58\u4e4b\u540e\u624d\u51fa\u73b0\u7684\u5217**\u8ffd\u52a0\u5230\u672b\u5c3e
   *      \uff08\u7528\u6237\u4e0a\u6b21\u4fdd\u5b58\u4e4b\u540e\u4ea7\u54c1\u65b0\u589e\u7684\u5217\uff0c\u4e0d\u80fd\u6324\u6389\u5df2\u6709\u5217\u7684\u987a\u5e8f\uff09\uff1b
   *   3) \u5df2\u7ecf\u6ca1\u6709\u7684\u5217\uff08\u4ee3\u7801\u91cc\u5220\u6389\u7684\uff09\u76f4\u63a5\u4e22\u5f03\u3002
   *
   * \u3010\u5411\u540e\u517c\u5bb9\u8981\u70b9\u3011
   *   \u65e7\u7248\u672c normalize() \u7528 options.defaults.map(...) \u91cd\u5efa\u6570\u7ec4\uff0c
   *   \u4f1a\u628a\u987a\u5e8f\u5f3a\u5236\u62c9\u56de\u58f0\u660e\u987a\u5e8f\u5e76\u4e22\u5f03\u7528\u6237\u4fdd\u5b58\u7684\u987a\u5e8f\u3002
   *   \u8fd9\u91cc\u6539\u4e3a\u300c\u4ee5\u4fdd\u5b58\u6570\u636e\u7684\u987a\u5e8f\u4e3a\u51c6\u300d\uff0c\u56e0\u6b64\u5347\u7ea7\u540e\u7528\u6237\u5df2\u8c03\u8fc7\u7684\u987a\u5e8f\u4e0d\u4e22\u5931\uff1b
   *   \u800c\u5bbd\u5ea6 / \u663e\u9690 / manual \u6807\u8bb0\u4f9d\u65e7\u6309 key \u7cbe\u786e\u5e94\u7528\u5230\u5bf9\u5e94\u7684\u5217\u4e0a\u3002
   */
  function normalize(saved: Array<Partial<ListColumnConfig> & { key: string }>): ListColumnConfig[] {
    const savedByKey = new Map(saved.map((item) => [item.key, item]))
    const defaultsByKey = new Map(options.defaults.map((item) => [item.key, item]))

    /** \u6309 defaults \u7684\u5143\u6570\u636e\u8865\u5168\u67d0\u4e00\u5217\uff0csaved \u63d0\u4f9b\u7528\u6237\u6570\u636e\uff08\u5bbd\u5ea6 / \u663e\u9690 / manual\uff09 */
    const build = (
      fallback: ListColumnConfig,
      previous?: Partial<ListColumnConfig> & { key: string }
    ): ListColumnConfig => {
      const width = Math.min(
        maxWidth,
        Math.max(minWidth, Number(previous?.width ?? fallback.width) || fallback.width)
      )
      return {
        key: fallback.key,
        label: fallback.label,
        width,
        visible: previous?.visible !== false,
        fixed: fallback?.fixed ?? false,
        flexible: fallback?.flexible ?? false,
        minFlexibleWidth: fallback?.minFlexibleWidth ?? (fallback?.flexible ? 160 : undefined),
        maxWidth: fallback?.maxWidth,
        idealWidth: fallback?.idealWidth,
        weight: fallback?.weight,
        minWidth: fallback?.minWidth,
        // fill \u6765\u81ea\u4ee3\u7801\u91cc\u7684 defaults\uff08\u4e0d\u662f\u7528\u6237\u53ef\u6539\u7684\u6570\u636e\uff09\uff0c
        // \u56e0\u6b64\u59cb\u7ec8\u4ee5 defaults \u4e3a\u51c6\uff0c\u4e0d\u4ece\u6301\u4e45\u5316\u6570\u636e\u8bfb\u53d6\u3002
        fill: fallback?.fill === true,
        // \u65e7\u7248\u672c\u6301\u4e45\u5316\u7684\u6570\u636e\u6ca1\u6709 manual \u5b57\u6bb5\uff0c\u4e00\u5f8b\u89c6\u4e3a\u300c\u672a\u624b\u52a8\u62d6\u62fd\u300d\uff0c
        // \u8fd9\u6837\u8001\u7528\u6237\u5347\u7ea7\u540e\u884c\u4e3a\u4e0d\u53d8\uff0c\u76f4\u5230\u4ed6\u81ea\u5df1\u62d6\u4e00\u6b21\u5217\u5bbd\u3002
        manual: previous?.manual === true
      }
    }

    const result: ListColumnConfig[] = []
    const used = new Set<string>()

    // 1) \u5df2\u4fdd\u5b58\u7684\u5217\uff1a\u4fdd\u6301\u4fdd\u5b58\u65f6\u7684\u987a\u5e8f
    for (const item of saved) {
      if (!item || typeof item.key !== 'string') continue
      const fallback = defaultsByKey.get(item.key)
      // \u4ee3\u7801\u91cc\u5df2\u5220\u9664\u7684\u5217\u76f4\u63a5\u4e22\u5f03
      if (!fallback) continue
      if (used.has(item.key)) continue
      used.add(item.key)
      result.push(build(fallback, item))
    }

    // 2) \u4ee3\u7801\u91cc\u65b0\u589e\u3001\u4fdd\u5b58\u4e4b\u540e\u624d\u51fa\u73b0\u7684\u5217\uff1a\u8ffd\u52a0\u5230\u672b\u5c3e
    for (const fallback of options.defaults) {
      if (used.has(fallback.key)) continue
      used.add(fallback.key)
      result.push(build(fallback))
    }

    return result
  }

  function load(): ListColumnConfig[] {
    try {
      const raw = localStorage.getItem(options.storageKey)
      if (!raw) return options.defaults.map((item) => ({ ...item }))
      const parsed = JSON.parse(raw)
      // \u517c\u5bb9\u5386\u53f2\u683c\u5f0f\uff1a\u65e9\u671f\u7248\u672c\u53ef\u80fd\u5b58\u6210 { key: {...} } \u7684\u5bf9\u8c61
      if (Array.isArray(parsed)) {
        return normalize(parsed as Array<Partial<ListColumnConfig> & { key: string }>)
      }
      if (parsed && typeof parsed === 'object') {
        return normalize(
          Object.entries(
            parsed as Record<string, Partial<ListColumnConfig> & { key: string }>
          ).map(([key, value]) => ({ ...value, key }))
        )
      }
      return options.defaults.map((item) => ({ ...item }))
    } catch {
      return options.defaults.map((item) => ({ ...item }))
    }
  }
"""

OLD_RESET = """  function resetColumns() {
    // \u6062\u590d\u9ed8\u8ba4\u65f6\u5fc5\u987b\u4e00\u5e76\u6e05\u6389\u300c\u624b\u52a8\u62d6\u62fd\u300d\u6807\u8bb0\uff0c
    // \u5426\u5219\u8be5\u5217\u4f1a\u4ee5\u624b\u52a8\u5bbd\u5ea6\u9489\u6b7b\uff0c\u518d\u4e5f\u56de\u4e0d\u5230\u81ea\u9002\u5e94\u586b\u5145\u3002
    columns.value = options.defaults.map((item) => ({ ...item, manual: false }))
    persist()
    syncLayout()
  }
"""

NEW_RESET = """  function resetColumns() {
    // \u6062\u590d\u9ed8\u8ba4\u65f6\u5fc5\u987b\u4e00\u5e76\u6e05\u6389\u300c\u624b\u52a8\u62d6\u62fd\u300d\u6807\u8bb0\uff0c
    // \u5426\u5219\u8be5\u5217\u4f1a\u4ee5\u624b\u52a8\u5bbd\u5ea6\u9489\u6b7b\uff0c\u518d\u4e5f\u56de\u4e0d\u5230\u81ea\u9002\u5e94\u586b\u5145\u3002
    // \u540c\u65f6\u6062\u590d\u3001**\u58f0\u660e\u987a\u5e8f**\uff08columns \u6570\u7ec4\u987a\u5e8f = \u5217\u987a\u5e8f\uff09\uff0c
    // \u5373\u300c\u6062\u590d\u9ed8\u8ba4\u300d\u4e00\u952e\u628a\u987a\u5e8f\u4e5f\u62c9\u56de\u5230\u4ee3\u7801\u58f0\u660e\u7684\u987a\u5e8f\u3002
    columns.value = options.defaults.map((item) => ({ ...item, manual: false }))
    persist()
    syncLayout()
  }

  /**
   * \u8c03\u6574\u5217\u987a\u5e8f\uff08\u4e0a\u79fb / \u4e0b\u79fb\uff09\u3002
   * ---------------------------------------------------------------------
   * \u5217\u987a\u5e8f\u5b58\u5728 **columns \u6570\u7ec4\u7684\u987a\u5e8f** \u91cc\uff0c
   * \u56e0\u6b64\u8c03\u5e8f = \u91cd\u6392\u6570\u7ec4\uff0c\u6301\u4e45\u5316\u4e5f\u81ea\u52a8\u5e26\u4e0a\u65b0\u987a\u5e8f\uff08\u65e0\u9700\u65b0\u589e\u5b57\u6bb5\uff09\u3002
   *
   * @param key      \u8981\u79fb\u52a8\u7684\u5217
   * @param direction -1 = \u4e0a\u79fb\uff08\u5411\u524d\u4e00\u5217\uff09\uff0c+1 = \u4e0b\u79fb\uff08\u5411\u540e\u4e00\u5217\uff09
   */
  function moveColumn(key: string, direction: -1 | 1) {
    const index = columns.value.findIndex((column) => column.key === key)
    if (index === -1) return
    const target = index + direction
    if (target < 0 || target >= columns.value.length) return
    const list = columns.value.slice()
    const [item] = list.splice(index, 1)
    list.splice(target, 0, item)
    columns.value = list
    persist()
    syncLayout()
  }

  /**
   * \u6309 key \u5c06\u5217\u79fb\u5230\u6307\u5b9a\u4f4d\u7f6e\uff08\u4f9b\u62d6\u62fd\u6392\u5e8f\u7b49\u540e\u7eed\u6269\u5c55\u4f7f\u7528\uff09\u3002
   */
  function moveColumnTo(key: string, toIndex: number) {
    const index = columns.value.findIndex((column) => column.key === key)
    if (index === -1) return
    const clamped = Math.max(0, Math.min(columns.value.length - 1, toIndex))
    if (clamped === index) return
    const list = columns.value.slice()
    const [item] = list.splice(index, 1)
    list.splice(clamped, 0, item)
    columns.value = list
    persist()
    syncLayout()
  }
"""

OLD_RETURN = """    isColumnVisible,
    setColumnVisible,
    resetColumns,
    persist,
    syncLayout,
    measureContainer,
    startColumnDrag,
    handleHeaderDragEnd
  }
"""

NEW_RETURN = """    isColumnVisible,
    setColumnVisible,
    resetColumns,
    moveColumn,
    moveColumnTo,
    /** \u6309\u5b58\u50a8\u987a\u5e8f\u6392\u5217\u7684\u5217 key \u5e8f\u5217\uff08\u53ea\u542b\u53ef\u89c1\u5217\uff09 */
    orderedColumnKeys,
    /** \u6309\u5b58\u50a8\u987a\u5e8f\u6392\u5217\u7684\u5217 key \u5e8f\u5217\uff08\u542b\u9690\u85cf\u5217\uff09 */
    orderedAllColumnKeys,
    persist,
    syncLayout,
    measureContainer,
    startColumnDrag,
    handleHeaderDragEnd
  }
"""

OLD_VISIBLE = """  const visibleColumns = computed(() => columns.value.filter((column) => column.visible))
  const visibleColumnCount = computed(() => visibleColumns.value.length)
"""

NEW_VISIBLE = """  const visibleColumns = computed(() => columns.value.filter((column) => column.visible))
  const visibleColumnCount = computed(() => visibleColumns.value.length)

  /**
   * \u6309\u5b58\u50a8\u987a\u5e8f\u6392\u5217\u7684\u5217 key\uff08\u4ec5\u53ef\u89c1\u5217\uff09\u3002
   * ---------------------------------------------------------------------
   * \u8868\u683c\u7684**\u6e32\u67d3\u987a\u5e8f**\u5c31\u662f\u8fd9\u4e2a\u5e8f\u5217\uff1a
   * Element Plus \u7684 el-table \u628a\u5217\u987a\u5e8f\u5b9a\u4e49\u4e3a\u300c\u9690\u85cf\u7684 <th> \u5143\u7d20\u5728 DOM \u4e2d\u7684\u4f4d\u7f6e\u300d\uff0c
   * \u56e0\u6b64\u53ea\u8981\u6309\u672c\u5e8f\u5217\u6e32\u67d3 <el-table-column>\uff0c\u8868\u5934\u7684\u5b9e\u9645\u987a\u5e8f\u5c31\u4f1a\u8ddf\u7740\u53d8\u3002
   */
  const orderedColumnKeys = computed(() => visibleColumns.value.map((column) => column.key))

  /** \u6309\u5b58\u50a8\u987a\u5e8f\u6392\u5217\u7684\u5217 key\uff08\u542b\u9690\u85cf\u5217\uff0c\u7528\u4e8e\u5217\u8bbe\u7f6e\u9762\u677f\u5c55\u793a\uff09 */
  const orderedAllColumnKeys = computed(() => columns.value.map((column) => column.key))
"""

def main():
    with io.open(PATH, 'r', encoding='utf-8') as f:
        text = f.read()
    for old, new in ((OLD_NORMALIZE, NEW_NORMALIZE),
                     (OLD_RESET, NEW_RESET),
                     (OLD_RETURN, NEW_RETURN),
                     (OLD_VISIBLE, NEW_VISIBLE)):
        n = text.count(old)
        if n != 1:
            raise SystemExit('PATTERN COUNT %d (need 1) for: %s' % (n, old[:60]))
        text = text.replace(old, new, 1)
    with io.open(PATH, 'w', encoding='utf-8', newline='') as f:
        f.write(text)
    print('ok')

main()
