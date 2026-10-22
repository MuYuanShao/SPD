/** Multiplies decimal inputs with the backend's four-place HALF_UP rounding for display only. */
export function packageReferencePrice(unitPrice: string | number | null | undefined, quantity: string | number): string | null {
  const parts = [unitPrice, quantity].map(value => String(value ?? '').trim().match(/^(\d+)(?:\.(\d+))?$/))
  if (!parts[0] || !parts[1]) return null
  const left = parts[0]
  const right = parts[1]
  const scale = (left[2]?.length ?? 0) + (right[2]?.length ?? 0)
  let amount = BigInt(left[1] + (left[2] ?? '')) * BigInt(right[1] + (right[2] ?? ''))
  if (scale > 4) {
    const divisor = 10n ** BigInt(scale - 4)
    amount = amount / divisor + (amount % divisor * 2n >= divisor ? 1n : 0n)
  } else {
    amount *= 10n ** BigInt(4 - scale)
  }
  return `${amount / 10000n}.${String(amount % 10000n).padStart(4, '0')}`
}
