/**
 * 按钮样式注册表（唯一来源）
 * ---------------------------------------------------------------------
 * 产品硬约束：**相同文本的按钮，全站必须像素级一致**。
 * 因此「显示什么」与「长什么样」由同一张表决定：
 *
 *   label ──▶ { tone, variant, size }
 *
 * 任何页面都**不允许**再自行指定同文本按钮的 tone / variant / size，
 * 否则就会出现「保存」一个是绿色实心、一个是蓝色描边。
 *
 * 命名与取值：
 *   tone    —— 语义色（primary / success / danger / warning / info / neutral）
 *   variant —— 视觉形态（solid 浅底描边 / ghost 透明底描边 / text 纯文字 / link 下划线）
 *   size    —— 尺寸档（default / small）。当前两档几何完全一致，
 *               保留 size 只是为了让调用方能表达意图，未来调整只改一处。
 *
 * 兼容性：
 *   - `BUTTON_TONE_BY_LABEL` 保留导出（旧代码可能引用），由本表派生；
 *   - `toneForLabel()` 保留导出，行为不变。
 */

/** 按钮语义色调 */
export type SpdButtonTone = 'primary' | 'success' | 'danger' | 'warning' | 'info' | 'neutral'

/** 按钮视觉形态 */
export type SpdButtonVariant = 'solid' | 'ghost' | 'text' | 'link'

/** 按钮尺寸档 */
export type SpdButtonSize = 'default' | 'small'

/** 按钮完整样式描述 */
export interface SpdButtonStyle {
  /** 语义色调 */
  tone: SpdButtonTone
  /** 视觉形态 */
  variant: SpdButtonVariant
  /** 尺寸档 */
  size: SpdButtonSize
}

/** 未命中映射表时的默认样式（主操作 · 实心 · 默认尺寸） */
export const DEFAULT_BUTTON_STYLE: SpdButtonStyle = {
  tone: 'primary',
  variant: 'solid',
  size: 'default'
}

/** 未命中映射表时的默认色调（主操作） */
export const DEFAULT_BUTTON_TONE: SpdButtonTone = DEFAULT_BUTTON_STYLE.tone

/**
 * 规范样式表：label → { tone, variant, size }
 *
 * 归类原则：
 *   - primary  ：主操作 —— 新增 / 保存 / 提交 / 确定 / 查询 / 编辑 / 修改
 *   - success  ：正向结果 —— 启用 / 通过 / 恢复 / 上架 / 生效 / 导入（写入落库）
 *   - danger   ：破坏性 —— 删除 / 停用 / 驳回 / 作废 / 撤回 / 清空
 *   - warning  ：需要注意 —— 提醒 / 预警 / 待处理 / 退回 / 列设置（辅助配置）
 *   - info     ：信息性 —— 查看 / 详情 / 追踪 / 核对 / 导出（读取出数）
 *   - neutral  ：中性/次要 —— 取消 / 关闭 / 返回 / 重置 / 下载 / 打印 / 刷新
 *
 * 形态原则：
 *   - solid ：页面级 / 弹窗级 / 工具栏级的主要动作
 *   - ghost ：次一级动作，与主动作并列但不抢焦点
 *   - text  ：表格外 / 页面内嵌的轻量动作
 *   - link  ：纯导航 / 查看型动作（不产生业务写入）
 *
 * 【四类数据操作必须彼此可区分】新增 / 导入 / 导出 / 列设置是
 * **四种不同的功能**，不能共用同一个色调，否则用户无法一眼分辨：
 *   - 新增     → primary 实心   （页面主操作，最高优先级）
 *   - 导入     → success 实心   （写入落库，正向结果）
 *   - 导出     → info 实心      （读取出数，信息性）
 *   - 列设置   → warning 描边   （辅助配置类工具，不与数据操作混淆；
 *                                且用 ghost 形态与三个实心按钮区分）
 */
export const BUTTON_STYLE_BY_LABEL: Readonly<Record<string, SpdButtonStyle>> = {
  /* ---------- 主操作 ---------- */
  新增: { tone: 'primary', variant: 'solid', size: 'default' },
  新增供应商: { tone: 'primary', variant: 'solid', size: 'default' },
  新增厂家: { tone: 'primary', variant: 'solid', size: 'default' },
  新增院区: { tone: 'primary', variant: 'solid', size: 'default' },
  新增科室: { tone: 'primary', variant: 'solid', size: 'default' },
  新增商品: { tone: 'primary', variant: 'solid', size: 'default' },
  新增产品: { tone: 'primary', variant: 'solid', size: 'default' },
  新增套餐: { tone: 'primary', variant: 'solid', size: 'default' },
  新增模板: { tone: 'primary', variant: 'solid', size: 'default' },
  添加: { tone: 'primary', variant: 'solid', size: 'default' },
  创建: { tone: 'primary', variant: 'solid', size: 'default' },
  保存: { tone: 'success', variant: 'solid', size: 'default' },
  保存并继续: { tone: 'success', variant: 'solid', size: 'default' },
  提交: { tone: 'primary', variant: 'solid', size: 'default' },
  提交审核: { tone: 'primary', variant: 'solid', size: 'default' },
  确认: { tone: 'primary', variant: 'solid', size: 'default' },
  确定: { tone: 'primary', variant: 'solid', size: 'default' },
  确认保存: { tone: 'success', variant: 'solid', size: 'default' },
  查询: { tone: 'primary', variant: 'solid', size: 'default' },
  搜索: { tone: 'primary', variant: 'solid', size: 'default' },
  筛选: { tone: 'primary', variant: 'solid', size: 'default' },
  过滤: { tone: 'primary', variant: 'solid', size: 'default' },
  导入: { tone: 'success', variant: 'solid', size: 'default' },
  编辑: { tone: 'primary', variant: 'solid', size: 'default' },
  修改: { tone: 'primary', variant: 'solid', size: 'default' },
  变更: { tone: 'primary', variant: 'solid', size: 'default' },
  调整: { tone: 'primary', variant: 'solid', size: 'default' },
  关联: { tone: 'primary', variant: 'solid', size: 'default' },
  关联库房: { tone: 'primary', variant: 'solid', size: 'default' },
  生成: { tone: 'primary', variant: 'solid', size: 'default' },
  生成盘点单: { tone: 'primary', variant: 'solid', size: 'default' },
  发布: { tone: 'primary', variant: 'solid', size: 'default' },
  提交订单: { tone: 'primary', variant: 'solid', size: 'default' },
  申请: { tone: 'primary', variant: 'solid', size: 'default' },
  领取: { tone: 'primary', variant: 'solid', size: 'default' },
  申领: { tone: 'primary', variant: 'solid', size: 'default' },

  /* ---------- 正向结果 ---------- */
  启用: { tone: 'success', variant: 'solid', size: 'default' },
  通过: { tone: 'success', variant: 'solid', size: 'default' },
  审批通过: { tone: 'success', variant: 'solid', size: 'default' },
  成功: { tone: 'success', variant: 'solid', size: 'default' },
  恢复: { tone: 'success', variant: 'solid', size: 'default' },
  恢复启用: { tone: 'success', variant: 'solid', size: 'default' },
  上架: { tone: 'success', variant: 'solid', size: 'default' },
  生效: { tone: 'success', variant: 'solid', size: 'default' },
  确认收货: { tone: 'success', variant: 'solid', size: 'default' },
  验收通过: { tone: 'success', variant: 'solid', size: 'default' },
  核销: { tone: 'success', variant: 'solid', size: 'default' },
  完成: { tone: 'success', variant: 'solid', size: 'default' },
  已完成: { tone: 'success', variant: 'solid', size: 'default' },

  /* ---------- 破坏性 ---------- */
  删除: { tone: 'danger', variant: 'solid', size: 'default' },
  移除: { tone: 'danger', variant: 'solid', size: 'default' },
  停用: { tone: 'danger', variant: 'solid', size: 'default' },
  禁用: { tone: 'danger', variant: 'solid', size: 'default' },
  驳回: { tone: 'danger', variant: 'solid', size: 'default' },
  作废: { tone: 'danger', variant: 'solid', size: 'default' },
  撤回: { tone: 'danger', variant: 'solid', size: 'default' },
  取消订单: { tone: 'danger', variant: 'solid', size: 'default' },
  终止: { tone: 'danger', variant: 'solid', size: 'default' },
  终止订单: { tone: 'danger', variant: 'solid', size: 'default' },
  清空: { tone: 'danger', variant: 'solid', size: 'default' },
  批量删除: { tone: 'danger', variant: 'solid', size: 'default' },
  批量停用: { tone: 'danger', variant: 'solid', size: 'default' },
  不通过: { tone: 'danger', variant: 'solid', size: 'default' },
  拒绝: { tone: 'danger', variant: 'solid', size: 'default' },
  退回: { tone: 'warning', variant: 'solid', size: 'default' },

  /* ---------- 提醒 / 待办 ---------- */
  提醒: { tone: 'warning', variant: 'solid', size: 'default' },
  预警: { tone: 'warning', variant: 'solid', size: 'default' },
  待处理: { tone: 'warning', variant: 'solid', size: 'default' },
  待办: { tone: 'warning', variant: 'solid', size: 'default' },
  暂缓: { tone: 'warning', variant: 'solid', size: 'default' },
  挂起: { tone: 'warning', variant: 'solid', size: 'default' },

  /* ---------- 信息性（查看 / 导航 → link 形态） ---------- */
  查看: { tone: 'info', variant: 'link', size: 'default' },
  查看详情: { tone: 'info', variant: 'link', size: 'default' },
  查看明细: { tone: 'info', variant: 'link', size: 'default' },
  详情: { tone: 'info', variant: 'link', size: 'default' },
  追踪: { tone: 'info', variant: 'link', size: 'default' },
  追溯: { tone: 'info', variant: 'link', size: 'default' },
  核对: { tone: 'info', variant: 'link', size: 'default' },
  明细: { tone: 'info', variant: 'link', size: 'default' },
  对账: { tone: 'info', variant: 'link', size: 'default' },
  打印预览: { tone: 'info', variant: 'link', size: 'default' },
  预览: { tone: 'info', variant: 'link', size: 'default' },

  /* ---------- 中性 / 次要 ---------- */
  取消: { tone: 'neutral', variant: 'solid', size: 'default' },
  关闭: { tone: 'neutral', variant: 'solid', size: 'default' },
  关闭弹窗: { tone: 'neutral', variant: 'solid', size: 'default' },
  返回: { tone: 'neutral', variant: 'solid', size: 'default' },
  返回上一页: { tone: 'neutral', variant: 'solid', size: 'default' },
  返回列表: { tone: 'neutral', variant: 'solid', size: 'default' },
  上一页: { tone: 'neutral', variant: 'solid', size: 'default' },
  上一条: { tone: 'neutral', variant: 'solid', size: 'default' },
  下一页: { tone: 'neutral', variant: 'solid', size: 'default' },
  下一条: { tone: 'neutral', variant: 'solid', size: 'default' },
  重置: { tone: 'neutral', variant: 'solid', size: 'default' },
  导出: { tone: 'info', variant: 'solid', size: 'default' },
  导入模板: { tone: 'info', variant: 'solid', size: 'default' },
  下载: { tone: 'neutral', variant: 'solid', size: 'default' },
  下载导入模板: { tone: 'info', variant: 'solid', size: 'default' },
  下载模板: { tone: 'info', variant: 'solid', size: 'default' },
  模板导出: { tone: 'info', variant: 'solid', size: 'default' },
  按模板导入: { tone: 'success', variant: 'solid', size: 'default' },
  打印: { tone: 'neutral', variant: 'solid', size: 'default' },
  刷新: { tone: 'neutral', variant: 'solid', size: 'default' },
  更多: { tone: 'neutral', variant: 'solid', size: 'default' },
  收起: { tone: 'neutral', variant: 'solid', size: 'default' },
  展开: { tone: 'neutral', variant: 'solid', size: 'default' },
  全部: { tone: 'neutral', variant: 'solid', size: 'default' },
  列设置: { tone: 'warning', variant: 'ghost', size: 'default' },
  高级筛选: { tone: 'neutral', variant: 'solid', size: 'default' },
  收起筛选: { tone: 'neutral', variant: 'solid', size: 'default' },
  上传: { tone: 'neutral', variant: 'solid', size: 'default' },
  上传文件: { tone: 'neutral', variant: 'solid', size: 'default' },
  '上传 CSV 文件': { tone: 'neutral', variant: 'solid', size: 'default' },
  备份: { tone: 'neutral', variant: 'solid', size: 'default' },
  帮助: { tone: 'neutral', variant: 'solid', size: 'default' },
  复制: { tone: 'neutral', variant: 'solid', size: 'default' },
  全选: { tone: 'neutral', variant: 'solid', size: 'default' },
  全选当前结果: { tone: 'neutral', variant: 'solid', size: 'default' },
  取消当前结果: { tone: 'neutral', variant: 'solid', size: 'default' },
  确认选择: { tone: 'neutral', variant: 'solid', size: 'default' },
  选择: { tone: 'neutral', variant: 'solid', size: 'default' },
  批量导入: { tone: 'success', variant: 'solid', size: 'default' },
  批量编辑: { tone: 'primary', variant: 'solid', size: 'default' },
  批量维护: { tone: 'primary', variant: 'solid', size: 'default' },
  批量停用商品: { tone: 'danger', variant: 'solid', size: 'default' },
  批量停用供应商: { tone: 'danger', variant: 'solid', size: 'default' },
  批量停用厂家: { tone: 'danger', variant: 'solid', size: 'default' },
  提交审批: { tone: 'primary', variant: 'solid', size: 'default' },
  审核: { tone: 'primary', variant: 'solid', size: 'default' },
  复核: { tone: 'primary', variant: 'solid', size: 'default' },
  确认出库: { tone: 'primary', variant: 'solid', size: 'default' },
  确认入库: { tone: 'primary', variant: 'solid', size: 'default' },
  发起补货: { tone: 'primary', variant: 'solid', size: 'default' },
  立即补货: { tone: 'primary', variant: 'solid', size: 'default' },
  生成订单: { tone: 'primary', variant: 'solid', size: 'default' },
  拆分: { tone: 'neutral', variant: 'solid', size: 'default' },
  合并: { tone: 'neutral', variant: 'solid', size: 'default' },
  挂起审批: { tone: 'warning', variant: 'solid', size: 'default' },
  重新发起: { tone: 'primary', variant: 'solid', size: 'default' },
  重新提交: { tone: 'primary', variant: 'solid', size: 'default' },
  作废申请: { tone: 'danger', variant: 'solid', size: 'default' },
  停用商品: { tone: 'danger', variant: 'solid', size: 'default' },
  停用供应商: { tone: 'danger', variant: 'solid', size: 'default' },
  停用厂家: { tone: 'danger', variant: 'solid', size: 'default' },
  停用科室: { tone: 'danger', variant: 'solid', size: 'default' }
}

/**
 * 仅色调映射表（由完整样式表派生）。
 * 保留该导出以兼容既有引用；**新增代码请用 BUTTON_STYLE_BY_LABEL**。
 */
export const BUTTON_TONE_BY_LABEL: Readonly<Record<string, SpdButtonTone>> = Object.freeze(
  Object.fromEntries(
    Object.entries(BUTTON_STYLE_BY_LABEL).map(([label, style]) => [label, style.tone])
  ) as Record<string, SpdButtonTone>
)

function normalize(label: string | null | undefined): string {
  return typeof label === 'string' ? label.trim() : ''
}

/**
 * 按按钮文本解析**完整样式**（tone + variant + size）。
 *
 * @param label 按钮文本（内部会 trim）
 * @returns 完整样式描述；未命中映射表时返回 DEFAULT_BUTTON_STYLE
 */
export function styleForLabel(label: string | null | undefined): SpdButtonStyle {
  const key = normalize(label)
  if (!key) return DEFAULT_BUTTON_STYLE
  return BUTTON_STYLE_BY_LABEL[key] ?? DEFAULT_BUTTON_STYLE
}

/**
 * 按按钮文本解析语义色调（兼容入口，行为与 styleForLabel(label).tone 一致）。
 *
 * @param label 按钮文本（内部会 trim）
 * @returns 语义色调；未命中映射表时返回 DEFAULT_BUTTON_TONE
 */
export function toneForLabel(label: string | null | undefined): SpdButtonTone {
  return styleForLabel(label).tone
}

/**
 * 按按钮文本解析视觉形态。
 *
 * @param label 按钮文本（内部会 trim）
 * @returns solid / ghost / text / link；未命中映射表时返回 solid
 */
export function variantForLabel(label: string | null | undefined): SpdButtonVariant {
  return styleForLabel(label).variant
}

/**
 * 按按钮文本解析尺寸档。
 *
 * @param label 按钮文本（内部会 trim）
 * @returns default / small；未命中映射表时返回 default
 */
export function sizeForLabel(label: string | null | undefined): SpdButtonSize {
  return styleForLabel(label).size
}
