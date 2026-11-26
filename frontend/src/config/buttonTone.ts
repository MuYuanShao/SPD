/**
 * 按钮语义色调（兼容层）
 * ---------------------------------------------------------------------
 * ⚠️ **唯一来源是 `config/buttonStyle.ts`**。
 *
 * 早期版本把「label → tone」单独放在本文件，后来扩展为
 * 「label → { tone, variant, size }」并迁到了 buttonStyle.ts，
 * 本文件因此退化为**再导出**，避免出现两个互相漂移的映射表。
 *
 * 新增代码请直接使用：
 *   import { styleForLabel, toneForLabel } from '../../config/buttonStyle'
 *
 * 保留本文件只为兼容既有引用路径，行为与 buttonStyle.ts 完全一致。
 */

export {
  BUTTON_TONE_BY_LABEL,
  DEFAULT_BUTTON_TONE,
  toneForLabel,
  type SpdButtonTone
} from './buttonStyle'
