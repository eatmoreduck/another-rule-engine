/**
 * ScriptPreview 组件 - Groovy 脚本预览（侧边 Drawer）
 * Plan 03-04: 使用 Drawer 展示脚本，支持语法高亮、验证、复制
 */

import { useState, useCallback } from 'react';
import { Drawer, Button, message, Space, Typography, Alert } from 'antd';
import { CopyOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { validateScript } from '../../api/rules';
import type { ValidateScriptResponse } from '../../types/rule';
import '../../styles/editor.css';

const { Text } = Typography;

interface ScriptPreviewProps {
  open: boolean;
  onClose: () => void;
  script: string;
}

/** 简单 Groovy 语法高亮：将脚本转为带 span 标签的 HTML */
function highlightGroovy(code: string): string {
  // 先转义 HTML 特殊字符
  let html = code
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

  // 按优先级替换（注释 > 字符串 > 关键字 > 数字）
  // 1. 单行注释
  html = html.replace(/(\/\/.*$)/gm, '<span class="script-comment">$1</span>');
  // 2. 字符串（单引号）
  html = html.replace(/('(?:[^'\\]|\\.)*')/g, '<span class="script-string">$1</span>');
  // 3. 关键字
  html = html.replace(
    /\b(def|if|else|return|switch|case|break|default|for|while|new|true|false|null)\b/g,
    '<span class="script-keyword">$1</span>',
  );
  // 4. 数字
  html = html.replace(/\b(\d+(?:\.\d+)?)\b/g, '<span class="script-number">$1</span>');

  return html;
}

export default function ScriptPreview({ open, onClose, script }: ScriptPreviewProps) {
  const [validating, setValidating] = useState(false);
  const [validateResult, setValidateResult] = useState<ValidateScriptResponse | null>(null);

  const handleValidate = useCallback(async () => {
    setValidating(true);
    setValidateResult(null);
    try {
      const result = await validateScript(script);
      setValidateResult(result);
      if (result.valid) {
        message.success('脚本验证通过');
      } else {
        message.error('脚本验证失败');
      }
    } catch {
      message.error('验证请求失败');
    } finally {
      setValidating(false);
    }
  }, [script]);

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(script).then(
      () => message.success('已复制到剪贴板'),
      () => message.error('复制失败'),
    );
  }, [script]);

  const handleClose = useCallback(() => {
    setValidateResult(null);
    onClose();
  }, [onClose]);

  return (
    <Drawer
      title="Groovy 脚本预览"
      placement="right"
      width={600}
      open={open}
      onClose={handleClose}
      styles={{ body: { padding: '16px' } }}
    >
      <div className="script-preview-actions">
        <Space>
          <Button
            type="primary"
            size="small"
            loading={validating}
            onClick={handleValidate}
          >
            验证脚本
          </Button>
          <Button
            size="small"
            icon={<CopyOutlined />}
            onClick={handleCopy}
          >
            复制
          </Button>
        </Space>
      </div>

      {validateResult && (
        <Alert
          type={validateResult.valid ? 'success' : 'error'}
          showIcon
          icon={validateResult.valid ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
          message={validateResult.valid ? '脚本验证通过' : '脚本验证失败'}
          description={
            validateResult.valid
              ? '该 Groovy 脚本语法正确，可以安全执行。'
              : validateResult.errorMessage || '脚本存在语法错误'
          }
          style={{ marginBottom: 12 }}
        />
      )}

      {validateResult?.errorDetails && (
        <Text
          type="secondary"
          style={{ display: 'block', marginBottom: 12, fontSize: 12, whiteSpace: 'pre-wrap' }}
        >
          {validateResult.errorDetails}
        </Text>
      )}

      <div
        className="script-preview-code"
        dangerouslySetInnerHTML={{ __html: highlightGroovy(script) }}
      />
    </Drawer>
  );
}
