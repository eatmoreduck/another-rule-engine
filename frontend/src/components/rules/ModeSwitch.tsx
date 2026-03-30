/**
 * ModeSwitch - 编辑模式切换组件
 * Plan 03-04: 在表单模式和流程图模式之间切换
 */

import { useCallback } from 'react';
import { Segmented } from 'antd';
import { EditOutlined, ApartmentOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';

interface ModeSwitchProps {
  /** 当前模式 */
  currentMode: 'form' | 'flow';
  /** 规则 key（新建时为 undefined） */
  ruleKey?: string;
}

export default function ModeSwitch({ currentMode, ruleKey }: ModeSwitchProps) {
  const navigate = useNavigate();

  const handleChange = useCallback(
    (value: string | number) => {
      const mode = value as 'form' | 'flow';
      if (mode === currentMode) return;

      // 切换前提示
      if (dirty) {
        const confirmed = window.confirm('切换模式将丢失当前未保存的修改，确认切换？');
        if (!confirmed) return;
      }

      if (ruleKey) {
        // 编辑已有规则
        if (mode === 'form') {
          navigate(`/rules/${ruleKey}/edit`);
        } else {
          navigate(`/rules/${ruleKey}/edit/flow`);
        }
      } else {
        // 新建规则
        if (mode === 'form') {
          navigate('/rules/new');
        } else {
          navigate('/rules/new/flow');
        }
      }
    },
    [currentMode, ruleKey, navigate],
  );

  // 跟踪是否有未保存修改（简化版：由外部管理，此处仅做占位逻辑）
  const dirty = false;

  return (
    <div className="mode-switch">
      <Segmented
        value={currentMode}
        onChange={handleChange}
        options={[
          {
            value: 'form',
            icon: <EditOutlined />,
            label: '表单模式',
          },
          {
            value: 'flow',
            icon: <ApartmentOutlined />,
            label: '流程图模式',
          },
        ]}
      />
    </div>
  );
}
