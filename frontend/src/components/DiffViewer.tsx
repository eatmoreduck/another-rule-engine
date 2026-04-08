import React, { useMemo } from 'react';
import * as Diff from 'diff';
import { useTranslation } from 'react-i18next';

interface DiffViewerProps {
  oldText: string;
  newText: string;
  oldTitle?: string;
  newTitle?: string;
  maxLines?: number;
  groovyHighlight?: boolean;
}

interface DiffLine {
  type: 'context' | 'added' | 'removed';
  oldLineNo?: number;
  newLineNo?: number;
  content: string;
}

const styles = {
  container: {
    border: '1px solid #d9d9d9',
    borderRadius: 6,
    overflow: 'hidden',
    fontSize: 12,
    fontFamily: "'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace" as const,
  },
  header: {
    display: 'flex' as const,
    borderBottom: '1px solid #d9d9d9',
  },
  headerCell: {
    flex: 1,
    padding: '6px 12px',
    fontWeight: 600 as const,
    fontSize: 12,
    background: '#fafafa',
  },
  body: {
    maxHeight: 400,
    overflowY: 'auto' as const,
  },
  row: {
    display: 'flex' as const,
    minHeight: 20,
    lineHeight: '20px',
  },
  side: {
    flex: 1,
    display: 'flex' as const,
    overflow: 'hidden',
  },
  lineNo: {
    minWidth: 40,
    padding: '0 8px',
    textAlign: 'right' as const,
    color: '#999',
    background: '#fafafa',
    userSelect: 'none' as const,
    borderRight: '1px solid #eee',
    flexShrink: 0,
  },
  lineContent: {
    flex: 1,
    padding: '0 8px',
    whiteSpace: 'pre-wrap' as const,
    wordBreak: 'break-all' as const,
  },
  separator: {
    width: 1,
    background: '#d9d9d9',
    flexShrink: 0,
  },
};

const bgColors = {
  context: { lineNo: '#fafafa', content: '#fff' },
  removed: { lineNo: '#ffdce0', content: '#ffeef0' },
  added: { lineNo: '#cdffd8', content: '#e6ffed' },
};

const textColors = {
  context: '#333',
  removed: '#b31d28',
  added: '#22863a',
};

/**
 * 将 Groovy 代码行转换为 React 节点，避免使用 dangerouslySetInnerHTML
 * 步骤：
 * 1. HTML 转义
 * 2. 使用正则拆分为 token 段
 * 3. 返回带颜色的 span 元素数组
 */
function highlightGroovyLine(line: string): React.ReactNode {
  // Step 1: HTML 转义
  const escaped = line
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

  // Step 2: 定义 token 正则（匹配注释、字符串、关键字、数字）
  const tokenRegex = /(\/\/.*$)|('(?:[^'&]|&(?:amp|lt|gt);)*')|("(?:[^"&]|&(?:amp|lt|gt);)*")|(\b(?:def|if|else|return|true|false|null|class|interface|extends|implements|import|package|new|this|super|void|int|long|double|float|String|boolean|Map|List|Object|static|final|public|private|protected)\b)|(\b\d+(?:\.\d+)?\b)/gm;

  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let key = 0;
  let match;

  // Step 3: 遍历匹配结果，生成 span 元素
  while ((match = tokenRegex.exec(escaped)) !== null) {
    // 添加匹配前的普通文本
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

  // 添加剩余的普通文本
  if (lastIndex < escaped.length) {
    parts.push(<span key={key++}>{escaped.slice(lastIndex)}</span>);
  }

  return parts.length > 0 ? <>{parts}</> : escaped;
}

export default function DiffViewer({ oldText, newText, oldTitle, newTitle, maxLines = 200, groovyHighlight = false }: DiffViewerProps) {
  const { t } = useTranslation();
  const resolvedOldTitle = oldTitle ?? t('grayscale.currentVersionTitle');
  const resolvedNewTitle = newTitle ?? t('grayscale.grayscaleVersionTitle');

  const diffLines = useMemo(() => {
    const changes = Diff.structuredPatch(
      resolvedOldTitle, resolvedNewTitle,
      oldText, newText,
      '', '', { context: 3 },
    );

    const lines: DiffLine[] = [];
    let oldLine = 0;
    let newLine = 0;

    for (const hunk of changes.hunks) {
      while (oldLine < hunk.oldStart - 1) {
        oldLine++;
        newLine++;
        lines.push({ type: 'context', oldLineNo: oldLine, newLineNo: newLine, content: '' });
      }

      for (const line of hunk.lines) {
        if (line.startsWith('+')) {
          newLine++;
          lines.push({ type: 'added', newLineNo: newLine, content: line.slice(1) });
        } else if (line.startsWith('-')) {
          oldLine++;
          lines.push({ type: 'removed', oldLineNo: oldLine, content: line.slice(1) });
        } else {
          oldLine++;
          newLine++;
          lines.push({ type: 'context', oldLineNo: oldLine, newLineNo: newLine, content: line.slice(1) });
        }
      }
    }

    return lines.slice(0, maxLines);
  }, [oldText, newText, resolvedOldTitle, resolvedNewTitle, maxLines]);

  const { leftLines, rightLines } = useMemo(() => {
    const left: (DiffLine & { placeholder?: boolean })[] = [];
    const right: (DiffLine & { placeholder?: boolean })[] = [];

    let i = 0;
    while (i < diffLines.length) {
      const line = diffLines[i];
      if (line.type === 'removed') {
        const removedBlock: DiffLine[] = [];
        while (i < diffLines.length && diffLines[i].type === 'removed') {
          removedBlock.push(diffLines[i]);
          i++;
        }
        const addedBlock: DiffLine[] = [];
        while (i < diffLines.length && diffLines[i].type === 'added') {
          addedBlock.push(diffLines[i]);
          i++;
        }
        const maxLen = Math.max(removedBlock.length, addedBlock.length);
        for (let j = 0; j < maxLen; j++) {
          if (j < removedBlock.length) {
            left.push(removedBlock[j]);
          } else {
            left.push({ type: 'context', placeholder: true, content: '' });
          }
          if (j < addedBlock.length) {
            right.push(addedBlock[j]);
          } else {
            right.push({ type: 'context', placeholder: true, content: '' });
          }
        }
      } else if (line.type === 'added') {
        right.push(line);
        left.push({ type: 'context', placeholder: true, content: '' });
        i++;
      } else {
        left.push(line);
        right.push(line);
        i++;
      }
    }

    return { leftLines: left, rightLines: right };
  }, [diffLines]);

  const renderContent = (text: string, prefix: string): React.ReactNode => {
    const prefixChar = prefix.charAt(0);
    if (groovyHighlight && text) {
      const prefixNode = prefixChar === '-'
        ? <span style={{ color: '#b31d28' }}>-</span>
        : prefixChar === '+'
          ? <span style={{ color: '#22863a' }}>+</span>
          : null;
      return <>{prefixNode} {highlightGroovyLine(text)}</>;
    }
    return `${prefix}${text}`;
  };

  if (!oldText && !newText) {
    return <div style={{ color: '#999', textAlign: 'center', padding: 16 }}>{t('grayscale.noContentToCompare')}</div>;
  }

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <div style={{ ...styles.headerCell, borderRight: '1px solid #d9d9d9' }}>{resolvedOldTitle}</div>
        <div style={styles.headerCell}>{resolvedNewTitle}</div>
      </div>
      <div style={styles.body}>
        {leftLines.map((leftLine, idx) => {
          const rightLine = rightLines[idx];
          const leftBg = bgColors[leftLine.type];
          const rightBg = bgColors[rightLine.type];

          return (
            <div key={idx} style={styles.row}>
              <div style={styles.side}>
                <div style={{
                  ...styles.lineNo,
                  background: leftLine.placeholder ? '#fafafa' : leftBg.lineNo,
                  color: leftLine.placeholder ? '#ccc' : '#999',
                }}>
                  {leftLine.placeholder ? '' : (leftLine.oldLineNo ?? leftLine.newLineNo ?? '')}
                </div>
                <div style={{
                  ...styles.lineContent,
                  background: leftLine.placeholder ? '#f5f5f5' : leftBg.content,
                  color: leftLine.placeholder ? '#ccc' : textColors[leftLine.type],
                }}>
                  {leftLine.placeholder ? '' : renderContent(leftLine.content, leftLine.type === 'removed' ? '- ' : '  ')}
                </div>
              </div>

              <div style={styles.separator} />

              <div style={styles.side}>
                <div style={{
                  ...styles.lineNo,
                  background: rightLine.placeholder ? '#fafafa' : rightBg.lineNo,
                  color: rightLine.placeholder ? '#ccc' : '#999',
                }}>
                  {rightLine.placeholder ? '' : (rightLine.newLineNo ?? rightLine.oldLineNo ?? '')}
                </div>
                <div style={{
                  ...styles.lineContent,
                  background: rightLine.placeholder ? '#f5f5f5' : rightBg.content,
                  color: rightLine.placeholder ? '#ccc' : textColors[rightLine.type],
                }}>
                  {rightLine.placeholder ? '' : renderContent(rightLine.content, rightLine.type === 'added' ? '+ ' : '  ')}
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
