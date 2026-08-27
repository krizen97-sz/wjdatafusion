import svgIconIds from 'virtual:svg-icons-names'

export const iconNames = svgIconIds
  .map((id) => id.replace(/^icon-/, ''))
  .sort((left, right) => left.localeCompare(right))

const iconNameSet = new Set(iconNames)

export function resolveIconName(name, fallback = 'component') {
  return iconNameSet.has(name) ? name : fallback
}
