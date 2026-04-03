/**
 * 通用格式化工具函数
 */

/**
 * 将 ISO 时间字符串格式化为人类可读形式
 * '2026-04-03T22:19:08.24024' → '2026-04-03 22:19:08'
 */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-';
  return iso.replace('T', ' ').substring(0, 19);
}
