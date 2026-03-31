import { useState, useCallback } from 'react';
import {
  Button,
  Card,
  Space,
  Upload,
  Breadcrumb,
  Divider,
  Alert,
  Table,
  Tag,
  Statistic,
  Row,
  Col,
  Input,
  message,
  Popconfirm,
} from 'antd';
import {
  DownloadOutlined,
  UploadOutlined,
  InboxOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import type { ColumnsType } from 'antd/es/table';
import {
  exportAllRules,
  exportRule,
  exportRulesBatch,
  importRules,
} from '../api/importExport';
import type { RuleExportData, ImportRulesResponse, RuleExportRecord } from '../types/importExport';

export default function ImportExportPage() {
  const [exportLoading, setExportLoading] = useState(false);
  const [exportRuleKey, setExportRuleKey] = useState('');
  const [exportBatchKeys, setExportBatchKeys] = useState('');

  const [importFile, setImportFile] = useState<RuleExportData | null>(null);
  const [importFileName, setImportFileName] = useState('');
  const [importLoading, setImportLoading] = useState(false);
  const [importResult, setImportResult] = useState<ImportRulesResponse | null>(null);

  /** 导出所有规则 */
  const handleExportAll = useCallback(async () => {
    setExportLoading(true);
    try {
      const data = await exportAllRules();
      downloadJson(data, `rules-export-${Date.now()}.json`);
      message.success(`导出成功，共 ${data.rules.length} 条规则`);
    } catch {
      message.error('导出规则失败');
    } finally {
      setExportLoading(false);
    }
  }, []);

  /** 导出单条规则 */
  const handleExportSingle = useCallback(async () => {
    if (!exportRuleKey.trim()) {
      message.warning('请输入规则 Key');
      return;
    }
    setExportLoading(true);
    try {
      const data = await exportRule(exportRuleKey.trim());
      downloadJson(data, `rule-${exportRuleKey.trim()}-${Date.now()}.json`);
      message.success('导出成功');
    } catch {
      message.error('导出规则失败，请检查规则 Key 是否正确');
    } finally {
      setExportLoading(false);
    }
  }, [exportRuleKey]);

  /** 批量导出规则 */
  const handleExportBatch = useCallback(async () => {
    const keys = exportBatchKeys
      .split('\n')
      .map((k) => k.trim())
      .filter(Boolean);
    if (keys.length === 0) {
      message.warning('请输入至少一个规则 Key');
      return;
    }
    setExportLoading(true);
    try {
      const data = await exportRulesBatch(keys);
      downloadJson(data, `rules-batch-export-${Date.now()}.json`);
      message.success(`批量导出成功，共 ${data.rules.length} 条规则`);
    } catch {
      message.error('批量导出失败');
    } finally {
      setExportLoading(false);
    }
  }, [exportBatchKeys]);

  /** 文件上传前的解析 */
  const handleBeforeUpload = useCallback((file: UploadFile) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const content = e.target?.result as string;
        const parsed = JSON.parse(content) as RuleExportData;
        if (!parsed.rules || !Array.isArray(parsed.rules)) {
          message.error('无效的规则导出文件：缺少 rules 字段');
          return;
        }
        setImportFile(parsed);
        setImportFileName(file.name);
        setImportResult(null);
        message.success(`已解析文件，共 ${parsed.rules.length} 条规则`);
      } catch {
        message.error('文件解析失败，请确保为有效的 JSON 文件');
      }
    };
    reader.readAsText(file as unknown as File);
    return false; // 阻止自动上传
  }, []);

  /** 执行导入 */
  const handleImport = useCallback(async () => {
    if (!importFile) return;
    setImportLoading(true);
    try {
      const result = await importRules(importFile);
      setImportResult(result);
      if (result.success) {
        message.success(result.message);
      } else {
        message.warning(result.message);
      }
    } catch {
      message.error('导入规则失败');
    } finally {
      setImportLoading(false);
    }
  }, [importFile]);

  /** 清除导入预览 */
  const handleClearImport = useCallback(() => {
    setImportFile(null);
    setImportFileName('');
    setImportResult(null);
  }, []);

  /** 下载 JSON 文件 */
  const downloadJson = (data: RuleExportData, filename: string) => {
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  };

  /** 导入预览表格列定义 */
  const previewColumns: ColumnsType<RuleExportRecord> = [
    {
      title: '规则 Key',
      dataIndex: ['rule', 'ruleKey'],
      ellipsis: true,
    },
    {
      title: '规则名称',
      dataIndex: ['rule', 'ruleName'],
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: ['rule', 'status'],
      width: 100,
      render: (status: string) => {
        const colorMap: Record<string, string> = {
          DRAFT: 'default',
          ACTIVE: 'success',
          ARCHIVED: 'warning',
          DELETED: 'error',
        };
        const labelMap: Record<string, string> = {
          DRAFT: '草稿',
          ACTIVE: '已激活',
          ARCHIVED: '已归档',
          DELETED: '已删除',
        };
        return <Tag color={colorMap[status] || 'default'}>{labelMap[status] || status}</Tag>;
      },
    },
    {
      title: '版本数',
      key: 'versionCount',
      width: 80,
      align: 'center',
      render: (_, record) => record.versions?.length ?? 0,
    },
  ];

  return (
    <>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: '规则导入导出' }]} />

      {/* 导出区域 */}
      <Card title="导出规则" style={{ marginBottom: 24 }}>
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              loading={exportLoading}
              onClick={handleExportAll}
            >
              导出所有规则
            </Button>
          </div>

          <Divider style={{ margin: '8px 0' }} />

          <div>
            <Space>
              <Input
                placeholder="输入规则 Key"
                value={exportRuleKey}
                onChange={(e) => setExportRuleKey(e.target.value)}
                style={{ width: 300 }}
                onPressEnter={handleExportSingle}
              />
              <Button
                icon={<DownloadOutlined />}
                loading={exportLoading}
                onClick={handleExportSingle}
              >
                导出单条规则
              </Button>
            </Space>
          </div>

          <Divider style={{ margin: '8px 0' }} />

          <div>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Input.TextArea
                placeholder="输入多个规则 Key，每行一个"
                rows={3}
                value={exportBatchKeys}
                onChange={(e) => setExportBatchKeys(e.target.value)}
                style={{ width: 500 }}
              />
              <Button
                icon={<DownloadOutlined />}
                loading={exportLoading}
                onClick={handleExportBatch}
              >
                批量导出
              </Button>
            </Space>
          </div>
        </Space>
      </Card>

      {/* 导入区域 */}
      <Card title="导入规则">
        {!importFile ? (
          <Upload.Dragger
            accept=".json"
            maxCount={1}
            showUploadList={false}
            beforeUpload={handleBeforeUpload}
            style={{ padding: '40px 0' }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">点击或拖拽 JSON 文件到此区域</p>
            <p className="ant-upload-hint">支持规则导出文件（JSON 格式）</p>
          </Upload.Dragger>
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Alert
              message={`已加载文件: ${importFileName}，共 ${importFile.rules.length} 条规则`}
              type="info"
              showIcon
              action={
                <Button size="small" icon={<DeleteOutlined />} onClick={handleClearImport}>
                  清除
                </Button>
              }
            />

            <Table
              rowKey={(_, index) => String(index)}
              columns={previewColumns}
              dataSource={importFile.rules}
              size="small"
              pagination={{ pageSize: 10, showTotal: (t) => `共 ${t} 条` }}
            />

            <div style={{ textAlign: 'right' }}>
              <Popconfirm
                title="确认导入？已存在的规则将被跳过。"
                onConfirm={handleImport}
                okText="确认导入"
                cancelText="取消"
              >
                <Button
                  type="primary"
                  icon={<UploadOutlined />}
                  loading={importLoading}
                >
                  确认导入
                </Button>
              </Popconfirm>
            </div>
          </Space>
        )}

        {/* 导入结果 */}
        {importResult && (
          <div style={{ marginTop: 24 }}>
            <Divider>导入结果</Divider>
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={6}>
                <Statistic
                  title="成功导入"
                  value={importResult.importedCount}
                  valueStyle={{ color: '#52c41a' }}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="已跳过"
                  value={importResult.skippedCount}
                  valueStyle={{ color: '#faad14' }}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="导入失败"
                  value={importResult.failedCount}
                  valueStyle={{ color: '#ff4d4f' }}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title="总计"
                  value={importFile?.rules.length ?? 0}
                />
              </Col>
            </Row>

            {importResult.failures.length > 0 && (
              <Alert
                message="失败详情"
                type="error"
                showIcon
                description={
                  <ul style={{ margin: 0, paddingLeft: 20 }}>
                    {importResult.failures.map((f, i) => (
                      <li key={i}>{f}</li>
                    ))}
                  </ul>
                }
              />
            )}
          </div>
        )}
      </Card>
    </>
  );
}
