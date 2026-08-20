import type { RouteLocationNormalizedLoaded, Router } from 'vue-router'

export function usePendingProductCatalogNavigation(options: {
  route: RouteLocationNormalizedLoaded
  router: Router
}) {
  function selectType(typeKey: string) {
    void options.router.push({
      name: 'pending-product-catalog',
      query: { ...options.route.query, scope: 'todo', type: typeKey }
    })
  }

  function selectScope(scope: string) {
    void options.router.push({
      name: 'pending-product-catalog',
      query:
        scope === 'mine'
          ? { ...options.route.query, scope, mineStatus: 'pending' }
          : { ...options.route.query, scope }
    })
  }

  function selectMineStatus(mineStatus: string) {
    void options.router.push({
      name: 'pending-product-catalog',
      query: { ...options.route.query, scope: 'mine', mineStatus }
    })
  }

  return {
    selectType,
    selectScope,
    selectMineStatus
  }
}
