import {
  AlertTriangle,
  ArchiveRestore,
  BarChart3,
  BadgeCheck,
  Banknote,
  BellRing,
  BookOpenText,
  Boxes,
  Building2,
  ClipboardCheck,
  ClipboardClock,
  ClipboardList,
  ClipboardPen,
  ClipboardPlus,
  ClipboardType,
  Coins,
  Database,
  Factory,
  FileClock,
  FileCheck2,
  FileMinus,
  FilePlus2,
  FileSearch2,
  FileSpreadsheet,
  FileText,
  FileWarning,
  FlaskConical,
  GitBranch,
  Grid2X2,
  HeartPulse,
  History,
  Home,
  KeyRound,
  Landmark,
  Layers2,
  Layers3,
  LayoutDashboard,
  ListChecks,
  PackageCheck,
  PackageOpen,
  PackagePlus,
  PackageSearch,
  Printer,
  RadioTower,
  Receipt,
  ReceiptText,
  RefreshCw,
  ScanLine,
  ScrollText,
  Settings,
  ShieldAlert,
  ShieldCheck,
  Stethoscope,
  Tags,
  ThermometerSnowflake,
  TrendingUp,
  Truck,
  UserCog,
  UserRoundCheck,
  Users,
  Warehouse,
  Wrench,
  Zap
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
  warehouse: Warehouse,
  'book-open-text': BookOpenText,
  'badge-check': BadgeCheck,
  banknote: Banknote,
  'bell-ring': BellRing,
  'boxes-stacked': Layers2,
  'building-2': Building2,
  'clipboard-clock': ClipboardClock,
  'clipboard-pen': ClipboardPen,
  'clipboard-plus': ClipboardPlus,
  'clipboard-type': ClipboardType,
  coins: Coins,
  'file-clock': FileClock,
  'file-check-2': FileCheck2,
  'file-minus': FileMinus,
  'file-plus-2': FilePlus2,
  'file-search-2': FileSearch2,
  'file-spreadsheet': FileSpreadsheet,
  'file-text': FileText,
  'flask-conical': FlaskConical,
  'git-branch': GitBranch,
  'grid-2x2': Grid2X2,
  'heart-pulse': HeartPulse,
  'key-round': KeyRound,
  'layers-3': Layers3,
  'list-checks': ListChecks,
  'package-open': PackageOpen,
  'package-plus': PackagePlus,
  printer: Printer,
  'radio-tower': RadioTower,
  receipt: Receipt,
  'refresh-cw': RefreshCw,
  'scroll-text': ScrollText,
  'shield-alert': ShieldAlert,
  stethoscope: Stethoscope,
  tags: Tags,
  'thermometer-snowflake': ThermometerSnowflake,
  'trending-up': TrendingUp,
  'user-cog': UserCog,
  'user-round-check': UserRoundCheck,
  wrench: Wrench,
  zap: Zap
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
