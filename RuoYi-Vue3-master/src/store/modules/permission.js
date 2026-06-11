import auth from '@/plugins/auth'
import router, { constantRoutes, dynamicRoutes } from '@/router'
import { getRouters } from '@/api/menu'
import Layout from '@/layout/index'
import ParentView from '@/components/ParentView'
import InnerLink from '@/layout/components/InnerLink'

// 匹配views里面所有的.vue文件
const modules = import.meta.glob('./../../views/**/*.vue')

const usePermissionStore = defineStore(
  'permission',
  {
    state: () => ({
      routes: [],
      addRoutes: [],
      defaultRoutes: [],
      topbarRouters: [],
      sidebarRouters: []
    }),
    actions: {
      setRoutes(routes) {
        this.addRoutes = routes
        this.routes = constantRoutes.concat(routes)
      },
      setDefaultRoutes(routes) {
        this.defaultRoutes = constantRoutes.concat(routes)
      },
      setTopbarRoutes(routes) {
        this.topbarRouters = routes
      },
      setSidebarRouters(routes) {
        this.sidebarRouters = routes
      },
      generateRoutes(roles) {
        return new Promise(resolve => {
          // 向后端请求路由数据
          getRouters().then(res => {
            const sdata = JSON.parse(JSON.stringify(res.data))
            const rdata = JSON.parse(JSON.stringify(res.data))
            const defaultData = JSON.parse(JSON.stringify(res.data))
            const sidebarRoutes = filterAsyncRouter(sdata)
            const rewriteRoutes = filterAsyncRouter(rdata, false, true)
            const defaultRoutes = filterAsyncRouter(defaultData)
            ensureUniqueRouteNames(sidebarRoutes)
            ensureUniqueRouteNames(rewriteRoutes)
            ensureUniqueRouteNames(defaultRoutes)
            const asyncRoutes = filterDynamicRoutes(dynamicRoutes)
            asyncRoutes.forEach(route => { router.addRoute(route) })
            this.setRoutes(rewriteRoutes)
            this.setSidebarRouters(constantRoutes.concat(sidebarRoutes))
            this.setDefaultRoutes(sidebarRoutes)
            this.setTopbarRoutes(defaultRoutes)
            resolve(rewriteRoutes)
          })
        })
      }
    }
  })

// 遍历后台传来的路由字符串，转换为组件对象
function filterAsyncRouter(asyncRouterMap, lastRouter = false, type = false) {
  return asyncRouterMap.filter(route => {
    if (type && route.children) {
      route.children = filterChildren(route.children)
    }
    if (!route.component && route.children && route.children.length) {
      route.component = 'Layout'
    }
    if (route.component) {
      // Layout ParentView 组件特殊处理
      if (route.component === 'Layout') {
        route.component = Layout
      } else if (route.component === 'ParentView') {
        route.component = ParentView
      } else if (route.component === 'InnerLink') {
        route.component = InnerLink
      } else {
        route.component = loadView(route.component)
      }
    }
    if (route.children != null && route.children && route.children.length) {
      route.children = filterAsyncRouter(route.children, route, type)
    } else {
      delete route['children']
      delete route['redirect']
    }
    return true
  })
}

function filterChildren(childrenMap, lastRouter = false) {
  var children = []
  childrenMap.forEach(el => {
    el.path = lastRouter ? lastRouter.path + '/' + el.path : el.path
    if (el.children && el.children.length && el.component === 'ParentView') {
      children = children.concat(filterChildren(el.children, el))
    } else {
      children.push(el)
    }
  })
  return children
}

function ensureUniqueRouteNames(routes, usedNames = new Set(), ancestors = new Set(), parentPath = '') {
  routes.forEach(route => {
    const fullPath = joinRoutePath(parentPath, route.path)
    if (route.name) {
      let nextName = route.name
      if (ancestors.has(nextName) || usedNames.has(nextName)) {
        nextName = buildUniqueName(nextName, fullPath, usedNames, ancestors)
      }
      route.name = nextName
      usedNames.add(nextName)
    }
    if (route.children && route.children.length) {
      const nextAncestors = new Set(ancestors)
      if (route.name) {
        nextAncestors.add(route.name)
      }
      ensureUniqueRouteNames(route.children, usedNames, nextAncestors, fullPath)
    }
  })
}

function buildUniqueName(baseName, fullPath, usedNames, ancestors) {
  const pathToken = fullPath
    .replace(/[/:]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_+|_+$/g, '')
  const prefix = pathToken ? `${baseName}_${pathToken}` : `${baseName}_route`
  let candidate = prefix
  let index = 1
  while (usedNames.has(candidate) || ancestors.has(candidate)) {
    candidate = `${prefix}_${index}`
    index += 1
  }
  return candidate
}

function joinRoutePath(parentPath, currentPath = '') {
  if (!parentPath) {
    return currentPath || ''
  }
  if (!currentPath || currentPath === '/') {
    return parentPath
  }
  if (currentPath.startsWith('/')) {
    return currentPath
  }
  return `${parentPath}/${currentPath}`.replace(/\/+/g, '/')
}

// 动态路由遍历，验证是否具备权限
export function filterDynamicRoutes(routes) {
  const res = []
  routes.forEach(route => {
    if (route.permissions) {
      if (auth.hasPermiOr(route.permissions)) {
        res.push(route)
      }
    } else if (route.roles) {
      if (auth.hasRoleOr(route.roles)) {
        res.push(route)
      }
    }
  })
  return res
}

export const loadView = (view) => {
  let res
  for (const path in modules) {
    const dir = path.split('views/')[1].split('.vue')[0]
    if (dir === view) {
      res = () => modules[path]()
    }
  }
  return res
}

export default usePermissionStore
