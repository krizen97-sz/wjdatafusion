const iconModules = import.meta.glob('../assets/icons/svg/*.svg')

export const iconNames = Object.keys(iconModules)
  .map((path) => path.split('/').pop().replace('.svg', ''))
  .sort((left, right) => left.localeCompare(right))

const iconNameSet = new Set(iconNames)

export function resolveIconName(name, fallback = 'component') {
  return iconNameSet.has(name) ? name : fallback
}
