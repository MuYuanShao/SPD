import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'
import {
  featureCatalogRedirects,
  featureCodesByViewFamily,
  featureRouteTarget,
  type FeatureViewFamily
} from '../config/featureCatalog'
import { useAuthStore } from '../stores/auth'

const DashboardView = () => import('../views/DashboardRealtimeView.vue')
const FeatureView = () => import('../views/FeatureView.vue')
const LoginView = () => import('../views/LoginView.vue')
const AccessDeniedView = () => import('../views/AccessDeniedView.vue')
const PendingProductCatalogView = () => import('../views/catalog/PendingProductCatalogView.vue')
const PendingProductApprovalDetailView = () => import('../views/catalog/PendingProductApprovalDetailView.vue')
const MasterDataListView = () => import('../views/master-data/MasterDataListView.vue')
const ProductDetailView = () => import('../views/master-data/ProductDetailView.vue')
const ProductBatchEditView = () => import('../views/master-data/ProductBatchEditView.vue')
const ProductFormView = () => import('../views/master-data/ProductFormView.vue')
const DepartmentRequisitionView = () => import('../views/supply-chain/DepartmentRequisitionView.vue')
const PurchaseManagementView = () => import('../views/supply-chain/PurchaseManagementView.vue')
const ReceivingAcceptanceView = () => import('../views/supply-chain/ReceivingAcceptanceView.vue')
const InventoryWorkbenchView = () => import('../views/supply-chain/InventoryWorkbenchView.vue')
const QuotaPackageView = () => import('../views/supply-chain/QuotaPackageView.vue')
const OperationalClosureView = () => import('../views/supply-chain/OperationalClosureView.vue')
const InvoiceManagementView = () => import('../views/supply-chain/InvoiceManagementView.vue')
const UdiTraceabilityView = () => import('../views/supply-chain/UdiTraceabilityView.vue')
const HighValueChargeDetailView = () => import('../views/supply-chain/HighValueChargeDetailView.vue')
const ColdChainMonitoringView = () => import('../views/supply-chain/ColdChainMonitoringView.vue')
const ConfigHitExplanationView = () => import('../views/system/ConfigHitExplanationView.vue')
const SystemConfigView = () => import('../views/system/SystemConfigView.vue')
const FieldOptionManagementView = () => import('../views/system/FieldOptionManagementView.vue')
const ApprovalFlowSettingsView = () => import('../views/system/ApprovalFlowSettingsView.vue')
const UserRoleManagementView = () => import('../views/system/UserRoleManagementView.vue')

function featureCodePattern(viewFamily: FeatureViewFamily): string {
  return featureCodesByViewFamily(viewFamily).join('|')
}

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: DashboardView,
      meta: { requiresAuth: true }
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/forbidden',
      name: 'forbidden',
      component: AccessDeniedView,
      meta: { requiresAuth: true }
    },
    {
      path: featureRouteTarget('pending-product-catalog'),
      name: 'pending-product-catalog',
      component: PendingProductCatalogView,
      meta: { requiresAuth: true }
    },
    {
      path: '/features/pending-product-catalog/:applicationNo',
      name: 'pending-product-approval-detail',
      component: PendingProductApprovalDetailView,
      meta: { requiresAuth: true }
    },
    {
      path: '/features/hospital-product-catalog/new',
      name: 'hospital-product-new',
      component: ProductFormView,
      meta: { requiresAuth: true }
    },
    {
      path: '/features/hospital-product-catalog/:productCode/edit',
      name: 'hospital-product-edit',
      component: ProductFormView,
      meta: { requiresAuth: true }
    },
    {
      path: '/features/hospital-product-catalog/batch-edit',
      name: 'hospital-product-batch-edit',
      component: ProductBatchEditView,
      meta: { requiresAuth: true }
    },
    {
      path: '/features/hospital-product-catalog/:productCode',
      name: 'hospital-product-detail',
      component: ProductDetailView,
      meta: { requiresAuth: true }
    },
    ...featureCatalogRedirects.map(({ from, to }) => ({
      path: `/features/${from}`,
      redirect: to.startsWith('/') ? to : featureRouteTarget(to)
    })),
    // 供应商/厂家管理已合并为“供应商厂家管理”，旧地址重定向到合并页对应页签
    {
      path: '/features/supplier-management',
      redirect: { path: '/features/supplier-manufacturer-management', query: { tab: 'supplier' } }
    },
    {
      path: '/features/manufacturer-management',
      redirect: { path: '/features/supplier-manufacturer-management', query: { tab: 'manufacturer' } }
    },
    {
      path: '/features/department-requisition/high-value',
      name: 'high-value-department-requisition',
      component: OperationalClosureView,
      meta: { requiresAuth: true, operationalType: 'department-requisition' }
    },
    {
      path: featureRouteTarget('department-requisition'),
      name: 'department-requisition',
      component: DepartmentRequisitionView,
      meta: { requiresAuth: true }
    },
    {
      path: featureRouteTarget('purchase-management'),
      name: 'purchase-management',
      component: PurchaseManagementView,
      meta: { requiresAuth: true }
    },
    {
      path: featureRouteTarget('receiving-acceptance'),
      name: 'receiving-acceptance',
      component: ReceivingAcceptanceView,
      meta: { requiresAuth: true }
    },
    {
      path: `/features/:code(${featureCodePattern('inventory-workbench')})`,
      name: 'inventory-workbench',
      component: InventoryWorkbenchView,
      meta: { requiresAuth: true }
    },
    {
      path: `/features/:code(${featureCodePattern('quota-package')})`,
      name: 'quota-package',
      component: QuotaPackageView,
      meta: { requiresAuth: true }
    },
    {
      path: '/features/high-value-consumables/operations',
      name: 'high-value-consumable-operations',
      component: OperationalClosureView,
      meta: { requiresAuth: true, operationalType: 'high-value-consumables' }
    },
    {
      path: featureRouteTarget('high-value-consumables'),
      name: 'high-value-charge-detail',
      component: HighValueChargeDetailView,
      meta: { requiresAuth: true }
    },
    {
      path: featureRouteTarget('cold-chain-monitoring'),
      name: 'cold-chain-monitoring',
      component: ColdChainMonitoringView,
      meta: { requiresAuth: true }
    },
    {
      path: featureRouteTarget('invoice-management'),
      name: 'invoice-management',
      component: InvoiceManagementView,
      meta: { requiresAuth: true }
    },
    {
      path: `/features/:code(${featureCodePattern('operational-closure')})`,
      name: 'operational-closure',
      component: OperationalClosureView,
      meta: { requiresAuth: true }
    },
    {
      path: featureRouteTarget('udi-traceability'),
      name: 'udi-traceability',
      component: UdiTraceabilityView,
      meta: { requiresAuth: true }
    },
    {
      path: featureRouteTarget('system-config'),
      name: 'system-config',
      component: SystemConfigView,
      meta: { requiresAuth: true }
    },
    {
      path: featureRouteTarget('field-option-management'),
      name: 'field-option-management',
      component: FieldOptionManagementView,
      meta: { requiresAuth: true }
    },
    {
      path: featureRouteTarget('config-hit-explanation'),
      name: 'config-hit-explanation',
      component: ConfigHitExplanationView,
      meta: { requiresAuth: true }
    },
    {
      path: featureRouteTarget('approval-flow-settings'),
      name: 'approval-flow-settings',
      component: ApprovalFlowSettingsView,
      meta: { requiresAuth: true }
    },
    {
      path: `/features/:code(${featureCodePattern('system-user-role-management')})`,
      name: 'user-role-management',
      component: UserRoleManagementView,
      meta: { requiresAuth: true }
    },
    {
      path: `/features/:code(${featureCodePattern('master-data-list')})`,
      name: 'master-data-list',
      component: MasterDataListView,
      meta: { requiresAuth: true }
    },
    {
      path: '/features/:code',
      name: 'feature',
      component: FeatureView,
      meta: { requiresAuth: true }
    }
  ]
})

function permissionCodeForRoute(to: RouteLocationNormalized) {
  if (to.name === 'forbidden' || to.name === 'login') return null
  if (to.name === 'dashboard') return 'dashboard'
  if (String(to.name).startsWith('hospital-product-')) return 'hospital-product-catalog'
  if (to.name === 'pending-product-approval-detail') return 'pending-product-catalog'
  if (to.params.code) return String(to.params.code)
  const featureMatch = to.path.match(/^\/features\/([^/]+)/)
  return featureMatch?.[1] ?? null
}

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth) {
    if (!authStore.isAuthenticated) {
      // 尝试从 localStorage 恢复（页面刷新场景）
      await authStore.init()
      if (!authStore.isAuthenticated) {
        return { name: 'login', query: { redirect: to.fullPath } }
      }
    }
  }

  if (to.name === 'login' && authStore.isAuthenticated) {
    return { name: 'dashboard' }
  }

  const requiredPermission = permissionCodeForRoute(to)
  if (requiredPermission === 'supplier-manufacturer-management') {
    if (!authStore.canAccessSupplierManufacturerManagement()) {
      return { name: 'forbidden' }
    }
    return true
  }
  if (requiredPermission && !authStore.canAccessMenu(requiredPermission)) {
    return { name: 'forbidden' }
  }

  return true
})
