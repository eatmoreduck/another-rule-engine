/**
 * ScriptPreview 组件 - Groovy 脚本预览（侧边 Drawer）
 * Plan 03-04: 使用 Drawer 展示脚本，支持语法高亮、验证、复制
 */

import React, { useState, useCallback } from 'react';
import { Drawer, Button, message, Space, Typography, Alert } from 'antd';
import { CopyOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { validateScript } from '../../api/rules';
import type { ValidateScriptResponse } from '../../types/rule';
import '../../styles/editor.css';

const { Text } = Typography;

interface ScriptPreviewProps {
  open: boolean;
  onClose: () => void;
  script: string;
}

/**
 * 将 Groovy 代码行转换为 React 节点（单行处理）
 * 步骤：HTML 转义 -> 正则拆分 token -> 返回带颜色的 span 数组
 */
function highlightGroovyLineToNodes(line: string): React.ReactNode {
  // Step 1: HTML 转义
  const escaped = line
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

  // Step 2: 定义 token 正则（匹配注释、字符串、关键字、数字）
  const tokenRegex = /(\/\/.*$)|('(?:[^'&]|&(?:amp|lt|gt);)*')|("(?:[^"&]|&(?:amp|lt|gt);)*")|(\b(?:def|if|else|return|switch|case|break|default|for|while|new|true|false|null)\b)|(\b\d+(?:\.\d+)?\b)/gm;

  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let key = 0;
  let match;

  // Step 3: 遍历匹配结果，生成 span 元素
  while ((match = tokenRegex.exec(escaped)) !== null) {
    if (match.index > lastIndex) {
      parts.push(<span key={key++}>{escaped.slice(lastIndex, match.index)}</span>);
    }
    const text = match[0];
    if (match[1]) {
      // 注释
      parts.push(<span key={key++} style={{ color: '#6a737d' }}>{text}</span>);
    } else if (match[2] || match[3]) {
      // 字符串
      parts.push(<span key={key++} style={{ color: '#032f62' }}>{text}</span>);
    } else if (match[4]) {
      // 关键字
      parts.push(<span key={key++} style={{ color: '#d73a49' }}>{text}</span>);
    } else if (match[5]) {
      // 数字
      parts.push(<span key={key++} style={{ color: '#005cc5' }}>{text}</span>);
    }
    lastIndex = match.index + text.length;
  }

  if (lastIndex < escaped.length) {
    parts.push(<span key={key++}>{escaped.slice(lastIndex)}</span>);
  }

  return parts.length > 0 ? <>{parts}</> : escaped;
}

/** 将完整 Groovy 脚本转换为 React 节点（按行处理） */
function highlightGroovy(code: string): React.ReactNode {
  const lines = code.split('\n');
  return (
    <>
      {lines.map((line, i) => (
        <React.Fragment key={i}>
          {i > 0 && '\n'}
          {highlightGroovyLineToNodes(line)}
        </React.Fragment>
      ))}
    </>
  );
}

export default function ScriptPreview({ open, onClose, script }: ScriptPreviewProps) {
  const { t } = useTranslation();
  const [validating, setValidating] = useState(false);
  const [validateResult, setValidateResult] = useState<ValidateScriptResponse | null>(null);

  const handleValidate = useCallback(async () => {
    setValidating(true);
    setValidateResult(null);
    try {
      const result = await validateScript(script);
      setValidateResult(result);
      if (result.valid) {
        message.success(t('scriptPreview.validatePassed'));
      } else {
        message.error(t('scriptPreview.validateFailed'));
      }
    } catch {
      message.error(t('scriptPreview.validateRequestFailed'));
    } finally {
      setValidating(false);
    }
  }, [script]);

  const handleCopy = useCallback(() => {
    navigator.clipboard.writeText(script).then(
      () => message.success(t('common.copySuccess')),
      () => message.error(t('common.copyFailed')),
    );
  }, [script]);

  const handleClose = useCallback(() => {
    setValidateResult(null);
    onClose();
  }, [onClose]);

  return (
    <Drawer
      title={t('scriptPreview.title')}
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
            {t('scriptPreview.validateScript')}
          </Button>
          <Button
            size="small"
            icon={<CopyOutlined />}
            onClick={handleCopy}
          >
            {t('common.copy')}
          </Button>
        </Space>
      </div>

      {validateResult && (
        <Alert
          type={validateResult.valid ? 'success' : 'error'}
          showIcon
          icon={validateResult.valid ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
          message={validateResult.valid ? t('scriptPreview.validatePassed') : t('scriptPreview.validateFailed')}
          description={
            validateResult.valid
              ? t('scriptPreview.validatePassedDesc')
              : validateResult.errorMessage || t('scriptPreview.validateFailedDesc')
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

      <div className="script-preview-code">{highlightGroovy(script)}</div>
    </Drawer>
  );
}
