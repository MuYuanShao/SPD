import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type ComputedRef, type Ref } from 'vue'

type ColumnConfig = {
  prop: string
  label: string
  width?: number | string
  visible?: boolean
}

type ColumnGroup = {
  key: string
  fields: string[]
}

type ReadonlyRef<T> = Ref<T> | ComputedRef<T>

export function useHospitalCatalogScrollSync(options: {
  isHospitalCatalog: ReadonlyRef<boolean>
  loading: ReadonlyRef<boolean>
  error: ReadonlyRef<unknown>
  sortedColumns: ReadonlyRef<ColumnConfig[]>
  activeHospitalColumn: Ref<string>
  activeColumnGroup: Ref<string>
  hospitalColumnGroups: ColumnGroup[]
  hospitalColumnConfigs: Ref<ColumnConfig[]>
}) {
  const columnNavTrackRef = ref<HTMLElement | null>(null)
  const columnNavDragging = ref(false)
  const hospitalTableWrapRef = ref<HTMLElement | null>(null)
  const tableScrollTrackRef = ref<HTMLElement | null>(null)
  const hospitalTableScrollState = ref({ scrollLeft: 0, clientWidth: 0, scrollWidth: 1 })
  const hospitalTableThumbDragging = ref(false)
  let columnNavDragStartX = 0
  let columnNavDragStartLeft = 0
  let hospitalTableScrollWrap: HTMLElement | null = null
  let syncingHospitalTableScroll = false

  const fallbackHospitalTableScrollWidth = computed(
    () => 42 + 210 + options.sortedColumns.value.reduce((sum, column) => sum + Number(column.width || 120), 0)
  )

  const hospitalTableScrollThumbStyle = computed(() => {
    const trackWidth = tableScrollTrackRef.value?.clientWidth ?? hospitalTableScrollState.value.clientWidth
    const { scrollLeft, clientWidth, scrollWidth } = hospitalTableScrollState.value
    if (!trackWidth || scrollWidth <= clientWidth) {
      return { width: '100%', transform: 'translateX(0px)' }
    }

    const thumbWidth = Math.max(64, Math.round((clientWidth / scrollWidth) * trackWidth))
    const maxThumbLeft = Math.max(0, trackWidth - thumbWidth)
    const maxScrollLeft = Math.max(1, scrollWidth - clientWidth)
    const thumbLeft = Math.round((scrollLeft / maxScrollLeft) * maxThumbLeft)
    return { width: `${thumbWidth}px`, transform: `translateX(${thumbLeft}px)` }
  })

  function startColumnNavDrag(event: PointerEvent) {
    const target = columnNavTrackRef.value
    if (!target) return
    columnNavDragging.value = true
    columnNavDragStartX = event.clientX
    columnNavDragStartLeft = target.scrollLeft
    target.setPointerCapture?.(event.pointerId)
  }

  function moveColumnNavDrag(event: PointerEvent) {
    const target = columnNavTrackRef.value
    if (!target || !columnNavDragging.value) return
    target.scrollLeft = columnNavDragStartLeft - (event.clientX - columnNavDragStartX)
    syncHospitalTableFromColumnNav()
  }

  function endColumnNavDrag(event: PointerEvent) {
    const target = columnNavTrackRef.value
    columnNavDragging.value = false
    target?.releasePointerCapture?.(event.pointerId)
  }

  function getHospitalTableScrollWrap() {
    return hospitalTableWrapRef.value
  }

  function refreshHospitalTableScrollRail() {
    const wrap = getHospitalTableScrollWrap()
    if (!wrap) {
      hospitalTableScrollState.value = {
        scrollLeft: 0,
        clientWidth: tableScrollTrackRef.value?.clientWidth ?? 0,
        scrollWidth: fallbackHospitalTableScrollWidth.value
      }
      return
    }
    hospitalTableScrollState.value = {
      scrollLeft: wrap.scrollLeft,
      clientWidth: wrap.clientWidth,
      scrollWidth: Math.max(fallbackHospitalTableScrollWidth.value, wrap.scrollWidth, wrap.clientWidth)
    }
  }

  function syncHospitalRailFromTable() {
    const wrap = hospitalTableScrollWrap
    if (!wrap || syncingHospitalTableScroll) return
    syncingHospitalTableScroll = true
    hospitalTableScrollState.value = {
      scrollLeft: wrap.scrollLeft,
      clientWidth: wrap.clientWidth,
      scrollWidth: Math.max(wrap.scrollWidth, wrap.clientWidth)
    }
    syncColumnNavFromHospitalTable()
    syncingHospitalTableScroll = false
  }

  function setHospitalTableScrollLeft(left: number) {
    const wrap = hospitalTableScrollWrap ?? getHospitalTableScrollWrap()
    if (!wrap) return
    const maxScrollLeft = Math.max(
      0,
      wrap.scrollWidth - wrap.clientWidth,
      fallbackHospitalTableScrollWidth.value - wrap.clientWidth
    )
    const nextLeft = Math.min(maxScrollLeft, Math.max(0, left))
    wrap.scrollLeft = nextLeft
    wrap.dispatchEvent(new Event('scroll'))
    hospitalTableScrollState.value = {
      scrollLeft: nextLeft,
      clientWidth: wrap.clientWidth,
      scrollWidth: Math.max(fallbackHospitalTableScrollWidth.value, wrap.scrollWidth, wrap.clientWidth)
    }
    syncColumnNavFromHospitalTable()
  }

  function scrollToHospitalColumnInTable(prop: string) {
    const orderedColumns = options.sortedColumns.value
    const targetIndex = orderedColumns.findIndex((column) => column.prop === prop)
    if (targetIndex < 0) return

    options.activeHospitalColumn.value = prop
    const leadingWidth = orderedColumns
      .slice(0, targetIndex)
      .reduce((sum, column) => sum + Number(column.width || 132), 42)
    const wrap = getHospitalTableScrollWrap()
    const targetWidth = Number(orderedColumns[targetIndex]?.width || 132)
    const viewportWidth = wrap?.clientWidth ?? 0
    setHospitalTableScrollLeft(Math.max(0, leadingWidth - Math.max(24, (viewportWidth - targetWidth) / 2)))
  }

  function applyColumnGroup(groupKey: string) {
    options.activeColumnGroup.value = groupKey
    const group = options.hospitalColumnGroups.find((item) => item.key === groupKey)
    options.hospitalColumnConfigs.value = options.hospitalColumnConfigs.value.map((column) => ({ ...column, visible: true }))
    if (!group || group.key === 'all') {
      options.activeHospitalColumn.value = ''
      setHospitalTableScrollLeft(0)
      return
    }
    const target = options.hospitalColumnConfigs.value.find((column) => group.fields.includes(column.label))
    if (target) scrollToHospitalColumnInTable(target.prop)
  }

  function syncHospitalTableFromColumnNav() {
    const nav = columnNavTrackRef.value
    const wrap = hospitalTableScrollWrap ?? getHospitalTableScrollWrap()
    if (!nav || !wrap) return
    const navMaxLeft = Math.max(1, nav.scrollWidth - nav.clientWidth)
    const tableMaxLeft = Math.max(
      0,
      wrap.scrollWidth - wrap.clientWidth,
      fallbackHospitalTableScrollWidth.value - wrap.clientWidth
    )
    setHospitalTableScrollLeft((nav.scrollLeft / navMaxLeft) * tableMaxLeft)
  }

  function syncColumnNavFromHospitalTable() {
    const nav = columnNavTrackRef.value
    const wrap = hospitalTableScrollWrap
    if (!nav || !wrap || columnNavDragging.value) return
    const tableMaxLeft = Math.max(1, wrap.scrollWidth - wrap.clientWidth)
    const navMaxLeft = Math.max(0, nav.scrollWidth - nav.clientWidth)
    nav.scrollLeft = (wrap.scrollLeft / tableMaxLeft) * navMaxLeft
  }

  function setHospitalTableScrollFromTrack(event: PointerEvent) {
    const track = tableScrollTrackRef.value
    const wrap = hospitalTableScrollWrap ?? getHospitalTableScrollWrap()
    if (!track || !wrap) return
    const rect = track.getBoundingClientRect()
    const clickRatio = Math.min(1, Math.max(0, (event.clientX - rect.left) / Math.max(1, rect.width)))
    const tableMaxLeft = Math.max(
      0,
      wrap.scrollWidth - wrap.clientWidth,
      fallbackHospitalTableScrollWidth.value - wrap.clientWidth
    )
    setHospitalTableScrollLeft(tableMaxLeft * clickRatio)
  }

  function startHospitalTableTrackDrag(event: PointerEvent) {
    hospitalTableThumbDragging.value = true
    ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
    setHospitalTableScrollFromTrack(event)
  }

  function moveHospitalTableTrackDrag(event: PointerEvent) {
    if (!hospitalTableThumbDragging.value) return
    setHospitalTableScrollFromTrack(event)
  }

  function endHospitalTableTrackDrag(event: PointerEvent) {
    hospitalTableThumbDragging.value = false
    ;(event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId)
  }

  async function attachHospitalTableScrollSync() {
    await nextTick()
    const nextWrap = getHospitalTableScrollWrap()
    if (hospitalTableScrollWrap === nextWrap) {
      refreshHospitalTableScrollRail()
      return
    }

    hospitalTableScrollWrap?.removeEventListener('scroll', syncHospitalRailFromTable)
    hospitalTableScrollWrap = nextWrap
    hospitalTableScrollWrap?.addEventListener('scroll', syncHospitalRailFromTable, { passive: true })
    refreshHospitalTableScrollRail()
  }

  watch(
    [options.isHospitalCatalog, options.loading, options.error, options.sortedColumns],
    attachHospitalTableScrollSync,
    { flush: 'post' }
  )

  onMounted(() => {
    attachHospitalTableScrollSync()
    window.addEventListener('resize', refreshHospitalTableScrollRail)
  })

  onBeforeUnmount(() => {
    hospitalTableScrollWrap?.removeEventListener('scroll', syncHospitalRailFromTable)
    window.removeEventListener('resize', refreshHospitalTableScrollRail)
  })

  return {
    columnNavTrackRef,
    columnNavDragging,
    hospitalTableWrapRef,
    tableScrollTrackRef,
    hospitalTableScrollState,
    hospitalTableThumbDragging,
    fallbackHospitalTableScrollWidth,
    hospitalTableScrollThumbStyle,
    startColumnNavDrag,
    moveColumnNavDrag,
    endColumnNavDrag,
    syncHospitalRailFromTable,
    scrollToHospitalColumnInTable,
    applyColumnGroup,
    startHospitalTableTrackDrag,
    moveHospitalTableTrackDrag,
    endHospitalTableTrackDrag
  }
}
