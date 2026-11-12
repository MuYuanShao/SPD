import { computed, onBeforeUnmount, ref } from 'vue'

export function useApprovalTableScroll() {
  const approvalTableShellRef = ref<HTMLElement | null>(null)
  const approvalScrollTrackRef = ref<HTMLElement | null>(null)
  const approvalScrollDragging = ref(false)
  const approvalScrollState = ref({ scrollLeft: 0, clientWidth: 1, scrollWidth: 1 })
  let approvalScrollStartX = 0
  let approvalScrollStartLeft = 0

  /* ------------------------------------------------------------------
   * 性能：横向滚动不应触发整表重渲染。
   * 滑块位置改为直接写 DOM style，Vue 只保存数值用于计算。
   * ------------------------------------------------------------------ */
  let scrollRafId = 0
  let pendingScrollLeft = 0

  function applyThumbTransform(trackWidth: number, thumbWidth: number, thumbLeft: number) {
    const thumb = approvalScrollTrackRef.value?.querySelector<HTMLElement>('.approval-wide-scroll-thumb')
    if (!thumb) return
    thumb.style.width = `${thumbWidth}px`
    thumb.style.transform = `translateX(${thumbLeft}px)`
  }

  /** 计算滑块几何信息并直接写入 DOM（不触发 Vue 渲染） */
  function syncThumb(shell: HTMLElement) {
    const trackWidth = approvalScrollTrackRef.value?.clientWidth ?? shell.clientWidth
    const { scrollLeft, clientWidth, scrollWidth } = approvalScrollState.value
    if (!trackWidth || scrollWidth <= clientWidth) {
      applyThumbTransform(trackWidth, trackWidth, 0)
      return
    }
    const thumbWidth = Math.max(72, Math.round((clientWidth / scrollWidth) * trackWidth))
    const maxThumbLeft = Math.max(0, trackWidth - thumbWidth)
    const maxScrollLeft = Math.max(1, scrollWidth - clientWidth)
    const thumbLeft = Math.round((scrollLeft / maxScrollLeft) * maxThumbLeft)
    applyThumbTransform(trackWidth, thumbWidth, thumbLeft)
  }

  function updateApprovalScrollState() {
    const shell = approvalTableShellRef.value
    if (!shell) return
    approvalScrollState.value = {
      scrollLeft: shell.scrollLeft,
      clientWidth: shell.clientWidth,
      scrollWidth: Math.max(shell.scrollWidth, shell.clientWidth)
    }
    syncThumb(shell)
  }

  /** 滚动事件用 rAF 节流，避免每帧多次响应式更新 */
  function handleApprovalScroll() {
    if (scrollRafId) return
    pendingScrollLeft = approvalTableShellRef.value?.scrollLeft ?? 0
    scrollRafId = requestAnimationFrame(() => {
      scrollRafId = 0
      const shell = approvalTableShellRef.value
      if (!shell) return
      approvalScrollState.value = {
        scrollLeft: pendingScrollLeft,
        clientWidth: shell.clientWidth,
        scrollWidth: Math.max(shell.scrollWidth, shell.clientWidth)
      }
      syncThumb(shell)
    })
  }

  function setApprovalTableScrollLeft(left: number) {
    const shell = approvalTableShellRef.value
    if (!shell) return
    const maxScrollLeft = Math.max(0, shell.scrollWidth - shell.clientWidth)
    shell.scrollLeft = Math.min(maxScrollLeft, Math.max(0, left))
    handleApprovalScroll()
  }

  function jumpApprovalTableScroll(event: PointerEvent) {
    const track = approvalScrollTrackRef.value
    const shell = approvalTableShellRef.value
    if (!track || !shell || event.target !== track) return
    const rect = track.getBoundingClientRect()
    const clickRatio = (event.clientX - rect.left) / Math.max(1, rect.width)
    setApprovalTableScrollLeft((shell.scrollWidth - shell.clientWidth) * clickRatio)
  }

  function startApprovalScrollDrag(event: PointerEvent) {
    const shell = approvalTableShellRef.value
    if (!shell) return
    approvalScrollDragging.value = true
    approvalScrollStartX = event.clientX
    approvalScrollStartLeft = shell.scrollLeft
    ;(event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId)
  }

  function moveApprovalScrollDrag(event: PointerEvent) {
    const shell = approvalTableShellRef.value
    const track = approvalScrollTrackRef.value
    if (!shell || !track || !approvalScrollDragging.value) return
    const maxScrollLeft = Math.max(1, shell.scrollWidth - shell.clientWidth)
    const trackWidth = Math.max(1, track.clientWidth)
    const thumbWidth = Math.max(72, Math.round((shell.clientWidth / shell.scrollWidth) * trackWidth))
    const maxThumbLeft = Math.max(1, trackWidth - thumbWidth)
    setApprovalTableScrollLeft(
      approvalScrollStartLeft + ((event.clientX - approvalScrollStartX) / maxThumbLeft) * maxScrollLeft
    )
  }

  function endApprovalScrollDrag(event: PointerEvent) {
    approvalScrollDragging.value = false
    ;(event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId)
  }

  onBeforeUnmount(() => {
    if (scrollRafId) {
      cancelAnimationFrame(scrollRafId)
      scrollRafId = 0
    }
  })

  return {
    approvalTableShellRef,
    approvalScrollTrackRef,
    approvalScrollDragging,
    approvalScrollState,
    approvalScrollThumbStyle: computed(() => ({})), // 位置由 syncThumb 直接写入 DOM
    updateApprovalScrollState,
    handleApprovalScroll,
    jumpApprovalTableScroll,
    startApprovalScrollDrag,
    moveApprovalScrollDrag,
    endApprovalScrollDrag
  }
}
