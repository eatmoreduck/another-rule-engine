import { Tag } from 'antd';
import { RuleStatus } from '../../types/rule';

const statusConfig: Record<RuleStatus, { color: string; label: string }> = {
  [RuleStatus.DRAFT]: { color: 'blue', label: '草稿' },
  [RuleStatus.ACTIVE]: { color: 'green', label: '生效中' },
  [RuleStatus.ARCHIVED]: { color: 'default', label: '已归档' },
  [RuleStatus.DELETED]: { color: 'red', label: '已删除' },
};

interface RuleStatusBadgeProps {
  status: RuleStatus;
}

export default function RuleStatusBadge({ status }: RuleStatusBadgeProps) {
  const config = statusConfig[status] || { color: 'default', label: status };
  return <Tag color={config.color}>{config.label}</Tag>;
}
