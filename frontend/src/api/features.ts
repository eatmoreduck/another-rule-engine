import apiClient from './client';

export interface FeatureFlags {
  multiEnvironment: boolean;
  importExport: boolean;
}

let cachedFlags: FeatureFlags | null = null;

/**
 * 获取功能开关状态（带缓存）
 */
export async function getFeatureFlags(): Promise<FeatureFlags> {
  if (cachedFlags) return cachedFlags;
  try {
    const response = await apiClient.get<FeatureFlags>('/api/v1/features');
    cachedFlags = response.data;
    return cachedFlags;
  } catch {
    // 后端不可达时默认全部关闭
    return { multiEnvironment: false, importExport: false };
  }
}

/** 清除缓存（用于重新加载） */
export function clearFeatureFlagsCache() {
  cachedFlags = null;
}
