import { useMemo } from 'react';
import * as Diff from 'diff';

interface DiffViewerProps {
  oldText: string;
  newText: string;
  oldTitle?: string;
  newTitle?: string;
  maxLines?: number;
  /** 启用 Groovy 语法高亮 */
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

// ============ Groovy 语法高亮 ============

/** Groovy 关键词 */
const GROOVY_KEYWORDS = new Set([
  'def', 'if', 'else', 'return', 'true', 'false', 'null',
  'class', 'interface', 'extends', 'implements', 'import', 'package',
  'new', 'this', 'super', 'void', 'int', 'long', 'double', 'float',
  'String', 'boolean', 'Map', 'List', 'Object',
  'static', 'final', 'public', 'private', 'protected',
]);

/** 对 Groovy 代码行做简单语法高亮（返回 HTML 字符串） */
function highlightGroovyLine(line: string): string {
  // 转义 HTML
  let result = line
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

  // 字符串（单引号）
  result = result.replace(/'([^']*)'/g, '<span style="color:#032f62">&#39;$1&#39;</span>');

  // 字符串（双引号）
  result = result.replace(/"([^"]*)"/g, '<span style="color:#032f62">&quot;$1&quot;</span>');

  // 行注释
  result = result.replace(/(\/\/.*)$/g, '<span style="color:#6a737d">$1</span>');

  // 数字
  result = result.replace(/\b(\d+(?:\.\d+)?)\b/g, '<span style="color:#005cc5">$1</span>');

  // 关键词
  result = result.replace(/\b(def|if|else|return|true|false|null|class|interface|extends|implements|import|package|new|this|super|void|int|long|double|float|String|boolean|Map|List|Object|static|final|public|private|protected)\b/g, '<span style="color:#d73a49">$1</span>');

  return result;
}

export default function DiffViewer({ oldText, newText, oldTitle = '当前版本', newTitle = '灰度版本', maxLines = 200, groovyHighlight = false }: DiffViewerProps) {
  const diffLines = useMemo(() => {
    const changes = Diff.structuredPatch(
      oldTitle, newTitle,
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
  }, [oldText, newText, oldTitle, newTitle, maxLines]);

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

  /** 渲染行内容：可选 Groovy 高亮 */
  const renderContent = (text: string, prefix: string) => {
    const fullLine = `${prefix}${text}`;
    if (groovyHighlight && text) {
      const highlighted = highlightGroovyLine(text);
      return <span dangerouslySetInnerHTML={{ __html: `${prefix.charAt(0) === '-' ? '<span style="color:#b31d28">-</span> ' : prefix.charAt(0) === '+' ? '<span style="color:#22863a">+</span> ' : '  '}${highlighted}` }} />;
    }
    return fullLine;
  };

  if (!oldText && !newText) {
    return <div style={{ color: '#999', textAlign: 'center', padding: 16 }}>无内容可对比</div>;
  }

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <div style={{ ...styles.headerCell, borderRight: '1px solid #d9d9d9' }}>{oldTitle}</div>
        <div style={styles.headerCell}>{newTitle}</div>
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
