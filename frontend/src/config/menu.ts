import {
  AlertTriangle,
  ArchiveRestore,
  BarChart3,
  Boxes,
  ClipboardCheck,
  ClipboardList,
  Database,
  Factory,
  FileWarning,
  Home,
  History,
  Landmark,
  LayoutDashboard,
  PackageCheck,
  PackageSearch,
  ReceiptText,
  ScanLine,
  Settings,
  ShieldCheck,
  Truck,
  Users,
  Warehouse
} from '@lucide/vue'
import type { Component } from 'vue'
import {
  featureCatalog,
  menuGroupFeatureCodes,
  primaryFeatureCodes,
  type FeatureCatalogEntry,
  type FeatureIcon
} from './featureCatalog'

export interface MenuItem {
  code: string
  title: string
  icon?: Component
  children?: MenuItem[]
}

const iconByName: Record<FeatureIcon, Component> = {
  'alert-triangle': AlertTriangle,
  'archive-restore': ArchiveRestore,
  'bar-chart-3': BarChart3,
  boxes: Boxes,
  'clipboard-check': ClipboardCheck,
  'clipboard-list': ClipboardList,
  database: Database,
  factory: Factory,
  'file-warning': FileWarning,
  history: History,
  home: Home,
  landmark: Landmark,
  'layout-dashboard': LayoutDashboard,
  'package-check': PackageCheck,
  'package-search': PackageSearch,
  'receipt-text': ReceiptText,
  'scan-line': ScanLine,
  settings: Settings,
  'shield-check': ShieldCheck,
  truck: Truck,
  users: Users,
  warehouse: Warehouse
}

const featureByCode: Map<string, FeatureCatalogEntry> = new Map(featureCatalog.map((feature) => [feature.code, feature]))

function buildMenuItem(code: string): MenuItem {
  const feature = featureByCode.get(code)

  if (!feature) {
    return { code, title: code }
  }

  const children = feature.children?.map(buildMenuItem)

  return {
    code: feature.code,
    title: feature.title,
    icon: iconByName[feature.icon],
    ...(children?.length ? { children } : {})
  }
}

export const primaryLinks: MenuItem[] = primaryFeatureCodes.map(buildMenuItem)
export const menuGroups: MenuItem[] = menuGroupFeatureCodes.map(buildMenuItem)
export const allMenuRoots = [...primaryLinks, ...menuGroups]

export function flattenMenu(items: MenuItem[], parents: MenuItem[] = []): Array<MenuItem & { parents: MenuItem[] }> {
  return items.flatMap((item) => {
    const current = { ...item, parents }
    const children = item.children ? flattenMenu(item.children, [...parents, item]) : []
    return [current, ...children]
  })
}

export const flatMenus = flattenMenu(allMenuRoots)
