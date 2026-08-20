import { computed, ref } from 'vue'

export function useApprovalTableScroll(options: {
  fieldColumnIndex: Record<string, number>
  fieldGroupTargets: Record<string, string>
  fieldToGroup: Record<string, string>
}) {
  const approvalTableShellRef = ref<HTMLElement | null>(null)
  const approvalScrollTrackRef = ref<HTMLElement | null>(null)
  const approvalScrollDragging = ref(false)
  const approvalScrollState = ref({ scrollLeft: 0, clientWidth: 1, scrollWidth: 1 })
  const activeApprovalField = ref('')
  const activeApprovalGroup = ref('all')
  let approvalScrollStartX = 0
  let approvalScrollStartLeft = 0

  const approvalScrollThumbStyle = computed(() => {
    const trackWidth = approvalScrollTrackRef.value?.clientWidth ?? approvalScrollState.value.clientWidth
    const { scrollLeft, clientWidth, scrollWidth } = approvalScrollState.value
    if (!trackWidth || scrollWidth <= clientWidth) {
      return { width: '100%', transform: 'translateX(0px)' }
    }

    const thumbWidth = Math.max(72, Math.round((clientWidth / scrollWidth) * trackWidth))
    const maxThumbLeft = Math.max(0, trackWidth - thumbWidth)
    const maxScrollLeft = Math.max(1, scrollWidth - clientWidth)
    const thumbLeft = Math.round((scrollLeft / maxScrollLeft) * maxThumbLeft)
    return { width: `${thumbWidth}px`, transform: `translateX(${thumbLeft}px)` }
  })

  function scrollApprovalTableToField(fieldName: string) {
    activeApprovalField.value = fieldName
    activeApprovalGroup.value = options.fieldToGroup[fieldName] || 'all'
    const shell = approvalTableShellRef.value
    if (!shell) return
    const tr = shell.querySelector('thead tr')
    if (!tr) return
    const index = options.fieldColumnIndex[fieldName]
    if (index === undefined) return
    const th = tr.children[index] as HTMLElement | null
    if (!th) return

    const shellRect = shell.getBoundingClientRect()
    const thRect = th.getBoundingClientRect()
    const thLeft = thRect.left - shellRect.left + shell.scrollLeft
    const thWidth = thRect.width
    const viewportWidth = shell.clientWidth
    const scrollTarget = Math.max(0, thLeft - Math.max(24, (viewportWidth - thWidth) / 2))
    setApprovalTableScrollLeft(scrollTarget)
  }

  function scrollApprovalToGroup(groupKey: string) {
    activeApprovalGroup.value = groupKey
    if (groupKey === 'all') {
      activeApprovalField.value = ''
      setApprovalTableScrollLeft(0)
      return
    }
    const field = options.fieldGroupTargets[groupKey]
    if (field) scrollApprovalTableToField(field)
  }

  function updateApprovalScrollState() {
    const shell = approvalTableShellRef.value
    if (!shell) return
    approvalScrollState.value = {
      scrollLeft: shell.scrollLeft,
      clientWidth: shell.clientWidth,
      scrollWidth: Math.max(shell.scrollWidth, shell.clientWidth)
    }
  }

  function setApprovalTableScrollLeft(left: number) {
    const shell = approvalTableShellRef.value
    if (!shell) return
    const maxScrollLeft = Math.max(0, shell.scrollWidth - shell.clientWidth)
    shell.scrollLeft = Math.min(maxScrollLeft, Math.max(0, left))
    updateApprovalScrollState()
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

  return {
    approvalTableShellRef,
    approvalScrollTrackRef,
    approvalScrollDragging,
    approvalScrollState,
    activeApprovalField,
    activeApprovalGroup,
    approvalScrollThumbStyle,
    scrollApprovalTableToField,
    scrollApprovalToGroup,
    updateApprovalScrollState,
    jumpApprovalTableScroll,
    startApprovalScrollDrag,
    moveApprovalScrollDrag,
    endApprovalScrollDrag
  }
}
