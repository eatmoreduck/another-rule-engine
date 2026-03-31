/** 系统规则模板 */
export interface RuleTemplate {
  id: number;
  name: string;
  category: string;
  description: string | null;
  groovyTemplate: string;
  /** 模板参数定义 (JSON 字符串) */
  parameters: string | null;
  /** 是否系统模板 */
  isSystem: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

/** 用户自定义模板 */
export interface CustomTemplate {
  id: number;
  name: string;
  description: string | null;
  groovyTemplate: string;
  /** 模板参数定义 (JSON 字符串) */
  parameters: string | null;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

/** 从模板实例化规则请求 */
export interface InstantiateTemplateRequest {
  /** 新规则的 ruleKey */
  ruleKey: string;
  /** 新规则名称（可选，默认使用模板名称） */
  ruleName?: string;
  /** 新规则描述（可选） */
  ruleDescription?: string;
  /** 模板参数值映射 */
  parameters?: Record<string, unknown>;
  /** 操作人 */
  operator?: string;
}

/** 保存个人模板请求 */
export interface CreateCustomTemplateRequest {
  /** 模板名称 */
  name: string;
  /** 模板描述 */
  description?: string;
  /** Groovy 模板脚本 */
  groovyTemplate: string;
  /** 模板参数定义 (JSON 格式) */
  parameters?: string;
  /** 创建人 */
  createdBy?: string;
}
