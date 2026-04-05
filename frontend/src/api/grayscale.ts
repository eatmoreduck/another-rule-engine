import apiClient from './client';
import type {
  GrayscaleConfig,
  GrayscaleRecord,
  GrayscaleReport,
  GrayscaleQueryParams,
} from '../types/grayscale';
import type { PageResponse } from '../types/rule';

import type { CanaryExecutionLog } from '../types/grayscale';

import type { GrayscaleTargetType } from '../types/grayscale';

/** 创建灰度配置（支持规则和决策流） */
export async function createGrayscale(config: GrayscaleConfig): Promise<GrayscaleRecord> {
  // 根据 targetType 独立构建请求体
  const requestBody: any = {};
  if (config.targetType === 'DECISION_FLOW') {
    requestBody = {
      ruleKey: config.targetKey,
      targetType: config.targetType,
      targetKey: config.targetKey,
      grayscaleVersion: config.grayscaleVersion,
      percentage: config.percentage,
      strategyType: config.strategyType,
      featureRules: config.featureRules,
      whitelistIds: config.whitelistIds,
      dualRunEnabled: config.dualRunEnabled,
    };
  } else {
    requestBody = {
      ruleKey: config.ruleKey,
      grayscaleVersion: config.grayscaleVersion,
      percentage: config.percentage,
      strategyType: config.strategyType,
      featureRules: config.featureRules,
      whitelistIds: config.whitelistIds,
      dualRunEnabled: config.dualRunEnabled,
    };
  }

  const response = await apiClient.post<GrayscaleRecord>('/api/v1/grayscale', requestBody);
  return response.data;
}

