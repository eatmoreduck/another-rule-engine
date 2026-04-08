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
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();
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
      message.success(t('importExport.exportSuccess', { count: data.rules.length }));
    } catch {
      message.error(t('importExport.exportFailed'));
    } finally {
      setExportLoading(false);
    }
  }, []);

  /** 导出单条规则 */
  const handleExportSingle = useCallback(async () => {
    if (!exportRuleKey.trim()) {
      message.warning(t('importExport.ruleKeyRequired'));
      return;
    }
    setExportLoading(true);
    try {
      const data = await exportRule(exportRuleKey.trim());
      downloadJson(data, `rule-${exportRuleKey.trim()}-${Date.now()}.json`);
      message.success(t('importExport.exportSuccess', { count: 1 }));
    } catch {
      message.error(t('importExport.exportSingleFailed'));
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
      message.warning(t('importExport.batchKeyRequired'));
      return;
    }
    setExportLoading(true);
    try {
      const data = await exportRulesBatch(keys);
      downloadJson(data, `rules-batch-export-${Date.now()}.json`);
      message.success(t('importExport.exportSuccess', { count: data.rules.length }));
    } catch {
      message.error(t('importExport.exportBatchFailed'));
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
          message.error(t('importExport.invalidFile'));
          return;
        }
        setImportFile(parsed);
        setImportFileName(file.name);
        setImportResult(null);
        message.success(t('importExport.fileLoaded', { name: file.name, count: parsed.rules.length }));
      } catch {
        message.error(t('importExport.parseFailed'));
      }
    };
    reader.readAsText(file as unknown as File);
    return false;
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
      message.error(t('importExport.importFailed'));
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
      title: t('importExport.previewColumns.ruleKey'),
      dataIndex: ['rule', 'ruleKey'],
      ellipsis: true,
    },
    {
      title: t('importExport.previewColumns.ruleName'),
      dataIndex: ['rule', 'ruleName'],
      ellipsis: true,
    },
    {
      title: t('importExport.previewColumns.status'),
      dataIndex: ['rule', 'status'],
      width: 100,
      render: (status: string) => {
        const colorMap: Record<string, string> = {
          DRAFT: 'default',
          ACTIVE: 'success',
          ARCHIVED: 'warning',
          DELETED: 'error',
        };
        return <Tag color={colorMap[status] || 'default'}>{t(`importExport.statusLabels.${status}`) || status}</Tag>;
      },
    },
    {
      title: t('importExport.previewColumns.versionCount'),
      key: 'versionCount',
      width: 80,
      align: 'center',
      render: (_, record) => record.versions?.length ?? 0,
    },
  ];

  return (
    <>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: t('importExport.pageTitle') }]} />

      {/* 导出区域 */}
      <Card title={t('importExport.exportTitle')} style={{ marginBottom: 24 }}>
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              loading={exportLoading}
              onClick={handleExportAll}
            >
              {t('importExport.exportAll')}
            </Button>
          </div>

          <Divider style={{ margin: '8px 0' }} />

          <div>
            <Space>
              <Input
                placeholder={t('importExport.ruleKeyInput')}
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
                {t('importExport.exportSingle')}
              </Button>
            </Space>
          </div>

          <Divider style={{ margin: '8px 0' }} />

          <div>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Input.TextArea
                placeholder={t('importExport.batchKeysInput')}
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
                {t('importExport.exportBatch')}
              </Button>
            </Space>
          </div>
        </Space>
      </Card>

      {/* 导入区域 */}
      <Card title={t('importExport.importTitle')}>
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
            <p className="ant-upload-text">{t('importExport.uploadHint')}</p>
            <p className="ant-upload-hint">{t('importExport.uploadSubHint')}</p>
          </Upload.Dragger>
        ) : (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Alert
              message={t('importExport.fileLoaded', { name: importFileName, count: importFile.rules.length })}
              type="info"
              showIcon
              action={
                <Button size="small" icon={<DeleteOutlined />} onClick={handleClearImport}>
                  {t('common.clear')}
                </Button>
              }
            />

            <Table
              rowKey={(_, index) => String(index)}
              columns={previewColumns}
              dataSource={importFile.rules}
              size="small"
              pagination={{ pageSize: 10, showTotal: (t_val) => t('common.totalShort', { count: t_val }) }}
            />

            <div style={{ textAlign: 'right' }}>
              <Popconfirm
                title={t('importExport.confirmImport')}
                onConfirm={handleImport}
                okText={t('importExport.importButton')}
                cancelText={t('common.cancel')}
              >
                <Button
                  type="primary"
                  icon={<UploadOutlined />}
                  loading={importLoading}
                >
                  {t('importExport.importButton')}
                </Button>
              </Popconfirm>
            </div>
          </Space>
        )}

        {/* 导入结果 */}
        {importResult && (
          <div style={{ marginTop: 24 }}>
            <Divider>{t('importExport.importResult.title')}</Divider>
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={6}>
                <Statistic
                  title={t('importExport.importResult.imported')}
                  value={importResult.importedCount}
                  valueStyle={{ color: '#52c41a' }}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title={t('importExport.importResult.skipped')}
                  value={importResult.skippedCount}
                  valueStyle={{ color: '#faad14' }}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title={t('importExport.importResult.failed')}
                  value={importResult.failedCount}
                  valueStyle={{ color: '#ff4d4f' }}
                />
              </Col>
              <Col span={6}>
                <Statistic
                  title={t('importExport.importResult.total')}
                  value={importFile?.rules.length ?? 0}
                />
              </Col>
            </Row>

            {importResult.failures.length > 0 && (
              <Alert
                message={t('importExport.importResult.failureDetail')}
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
