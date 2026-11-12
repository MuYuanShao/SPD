/**
 * 统一日期时间格式化：所有列表时间列统一输出 YYYY-MM-DD HH:MM:SS。
 */

const DATE_TIME_PATTERN = /^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(:\d{2})?/

function pad(value: number) {
  return String(value).padStart(2, '0')
}

/**
 * 将后端返回的时间值格式化为 YYYY-MM-DD HH:MM:SS。
 * 兼容：
 *   - "2026-11-12 09:13:37" / "2026-11-12T09:13:37"
 *   - "2026-11-12 09:13"（补秒）
 *   - ISO 字符串 / Date 对象 / 时间戳
 */
export function formatDateTime(value: unknown): string {
  if (value == null || value === '') return '-'

  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) return '-'
    return (
      `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ` +
      `${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`
    )
  }

  const text = String(value).trim()
  if (!text) return '-'

  const matched = DATE_TIME_PATTERN.exec(text)
  if (matched) {
    const datePart = matched[0].slice(0, 10)
    const timePart = matched[0].slice(11)
    const [hour, minute, second] = timePart.split(':')
    return `${datePart} ${hour}:${minute}:${second ?? '00'}`
  }

  const parsed = new Date(text)
  if (!Number.isNaN(parsed.getTime())) return formatDateTime(parsed)
  return text
}
