import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

const ROOT = new URL('../frontend/src/', import.meta.url).pathname.replace(/^\//, '').replace(/\/$/, '')

function walk(dir, out = []) {
  for (const e of readdirSync(dir)) {
    const p = join(dir, e)
    const st = statSync(p)
    if (st.isDirectory()) walk(p, out)
    else if (p.endsWith('.vue')) out.push(p)
  }
  return out
}

const files = walk(ROOT)
const usage = new Map() // label -> Map(key -> [{file,line}])

function attr(tag, name) {
  const re = new RegExp(`\\b${name}\\s*=\\s*"([^"]*)"`)
  const m = tag.match(re)
  return m ? m[1] : null
}

for (const f of files) {
  const src = readFileSync(f, 'utf8')
  const lines = src.split(/\r?\n/)
  lines.forEach((line, i) => {
    if (!line.includes('<SpdButton')) return
    const tag = line
    const label = attr(tag, 'label')
    if (label === null) {
      // slot-based button: record separately
      return
    }
    const tone = attr(tag, 'tone')
    const variant = attr(tag, 'variant')
    const size = attr(tag, 'size')
    const key = `${tone ?? 'AUTO'}|${variant ?? 'solid'}|${size ?? 'default'}`
    if (!usage.has(label)) usage.set(label, new Map())
    const m = usage.get(label)
    if (!m.has(key)) m.set(key, [])
    m.get(key).push(`${f.replace(ROOT, '')}:${i + 1}`)
  })
}

const conflicts = []
for (const [label, m] of [...usage.entries()].sort()) {
  if (m.size > 1) conflicts.push({ label, m })
}

console.log(`labels: ${usage.size}`)
if (!conflicts.length) {
  console.log('NO CONFLICTS')
} else {
  console.log(`CONFLICTS: ${conflicts.length}`)
  for (const c of conflicts) {
    console.log(`\n[${c.label}]`)
    for (const [k, locs] of c.m) console.log(`   ${k}  x${locs.length}  ${locs.slice(0, 6).join(', ')}`)
  }
}
