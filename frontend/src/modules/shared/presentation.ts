/** Display formatting only. Never use these values to persist or compare identities. */
const connectors = new Set(['de', 'del', 'la', 'las', 'los', 'el', 'y', 'e'])
const acronyms = new Set(['cdmx', 'unam', 'imss', 'issste'])
export function displayText(raw: string | null | undefined): string {
  if (!raw) return ''
  let word = 0
  return raw.replace(/[\p{L}\p{M}]+/gu, token => {
    const lower = token.toLocaleLowerCase('es-MX')
    const first = word++ === 0
    if (acronyms.has(lower)) return lower.toLocaleUpperCase('es-MX')
    if (!first && connectors.has(lower)) return lower
    return lower[0]!.toLocaleUpperCase('es-MX') + lower.slice(1)
  })
}
export function displayIdentifier(raw: string | null | undefined): string {
  return raw?.toLocaleUpperCase('es-MX') ?? ''
}
export function displayBirthDate(raw: string | null | undefined): string {
  const match = raw && /^([0-9]{4})-([0-9]{2})-([0-9]{2})$/.exec(raw)
  return match ? `${match[3]}/${match[2]}/${match[1]}` : raw ?? ''
}
export function displayPhone(raw: string | null | undefined): string {
  if (!raw || !/^[0-9]{10}$/.test(raw)) return raw ?? ''
  return /^(?:33|55|81)/.test(raw)
    ? raw.replace(/^([0-9]{2})([0-9]{4})([0-9]{4})$/, '$1 $2 $3')
    : raw.replace(/^([0-9]{3})([0-9]{3})([0-9]{4})$/, '$1 $2 $3')
}
