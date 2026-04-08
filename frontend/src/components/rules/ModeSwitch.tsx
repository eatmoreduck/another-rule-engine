/**
 * ModeSwitch - 编辑模式切换组件
 * Plan 03-04: 在表单模式和流程图模式之间切换
 */

import { useCallback } from 'react';
import { Segmented } from 'antd';
import { EditOutlined, ApartmentOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

interface ModeSwitchProps {
  /** 当前模式 */
  currentMode: 'form' | 'flow';
  /** 规则 key（新建时为 undefined） */
  ruleKey?: string;
  /** 是否有未保存的修改（由父组件传入） */
  dirty?: boolean;
}

export default function ModeSwitch({ currentMode, ruleKey, dirty = false }: ModeSwitchProps) {
  const navigate = useNavigate();
  const { t } = useTranslation();

  const handleChange = useCallback(
    (value: string | number) => {
      const mode = value as 'form' | 'flow';
      if (mode === currentMode) return;

      // 切换前提示
      if (dirty) {
        const confirmed = window.confirm(t('rules.modeSwitchConfirm'));
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
    [currentMode, ruleKey, navigate, dirty],
  );

  return (
    <div className="mode-switch">
      <Segmented
        value={currentMode}
        onChange={handleChange}
        options={[
          {
            value: 'form',
            icon: <EditOutlined />,
            label: t('rules.formMode'),
          },
          {
            value: 'flow',
            icon: <ApartmentOutlined />,
            label: t('rules.flowMode'),
          },
        ]}
      />
    </div>
  );
}
