// Import the pipe-delimited SEPOMEX export normalized by IcaliaLabs/sepomex.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { gzipSync } from 'node:zlib'
import { fileURLToPath } from 'node:url'

const input = process.argv[2]
if (!input) throw new Error('Uso: node scripts/import-postal-catalog.mjs archivo.csv')
const rows = new Set()
const codes = new Set()
const states = new Set()
for (const line of readFileSync(input, 'utf8').split(/\r?\n/)) {
  if (!line.trim()) continue
  const columns = line.split('|')
  const [code, neighborhood, , municipality, state] = columns
  if (columns.length !== 15 || !/^\d{5}$/.test(code) || !neighborhood || !municipality || !state
      || [neighborhood, municipality, state].some(value => /[\t\r\n]/.test(value))) {
    throw new Error('Fila inválida; el catálogo anterior no se ha reemplazado')
  }
  rows.add([code, state, municipality, neighborhood].join('\t'))
  codes.add(code)
  states.add(state)
}
if (codes.size < 30000 || states.size !== 32) throw new Error('Catálogo incompleto; se requieren los 32 estados')
const directory = fileURLToPath(new URL('../backend/src/main/resources/postal/', import.meta.url))
mkdirSync(directory, { recursive: true })
writeFileSync(directory + 'mexico.tsv.gz', gzipSync([...rows].sort().join('\n') + '\n', { level: 9 }))
console.log(`${codes.size} códigos postales · ${rows.size} asentamientos · ${states.size} estados`)
