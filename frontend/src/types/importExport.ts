import type { Rule } from './rule';

/** 规则版本记录 */
export interface RuleVersionRecord {
  id: number;
  ruleId: number;
  ruleKey: string;
  version: number;
  groovyScript: string;
  changeReason: string | null;
  changedBy: string;
  changedAt: string;
  isRollback: boolean;
  rollbackFromVersion: number | null;
}

/** 规则导出记录 */
export interface RuleExportRecord {
  /** 规则基本信息 */
  rule: Rule;
  /** 版本历史 */
  versions: RuleVersionRecord[];
}

/** 规则导出数据结构 */
export interface RuleExportData {
  /** 导出格式版本 */
  formatVersion: string;
  /** 导出时间戳 */
  exportedAt: string;
  /** 导出人 */
  exportedBy: string;
  /** 导出的规则列表 */
  rules: RuleExportRecord[];
}

/** 规则导入响应 */
export interface ImportRulesResponse {
  /** 导入是否成功 */
  success: boolean;
  /** 成功导入的规则数量 */
  importedCount: number;
  /** 跳过的规则数量（已存在） */
  skippedCount: number;
  /** 失败的规则数量 */
  failedCount: number;
  /** 失败详情 */
  failures: string[];
  /** 消息 */
  message: string;
}
