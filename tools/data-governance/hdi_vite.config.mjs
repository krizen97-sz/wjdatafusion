import baseConfig from '../../RuoYi-Vue3-master/vite.config.js'

// Module-scoped local entry; original bundles require the same-origin /detl prefix.
// Run from RuoYi-Vue3-master: npx vite --config ../tools/data-governance/hdi_vite.config.mjs
export default async environment => {
  const base = await baseConfig(environment)
  return {
    ...base,
    server: {
      ...base.server,
      host: '127.0.0.1',
      open: false,
      proxy: {
        ...base.server.proxy,
        '/detl': { target: base.server.proxy['/dev-api'].target, changeOrigin: true }
      }
    }
  }
}
