import { useState, useCallback } from 'react';
import {
  Button,
  Card,
  Table,
  Space,
  Modal,
  Form,
  Input,
  Select,
  message,
  Breadcrumb,
  Tag,
  Alert,
  Popconfirm,
  Tooltip,
  Empty,
} from 'antd';
import {
  PlusOutlined,
  DeleteOutlined,
  CodeOutlined,
  CheckCircleOutlined,
  MinusCircleOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { convertToGroovy, validateDecisionTable } from '../api/decisionTable';
import type {
  DecisionTableRequest,
  DecisionTableResponse,
  DecisionTableValidateResponse,
  DecisionRow,
} from '../types/decisionTable';

/** 条件/动作列类型选项 */
const COLUMN_TYPE_OPTIONS = [
  { value: 'STRING', label: '字符串' },
  { value: 'NUMBER', label: '数值' },
  { value: 'BOOLEAN', label: '布尔' },
];

/** 内部行数据结构，带唯一 key */
interface TableRowData extends DecisionRow {
  _key: string;
}

let rowKeyCounter = 0;
const newRowKey = () => `row_${++rowKeyCounter}`;

export default function DecisionTablePage() {
  /** 条件列定义 name -> type */
  const [conditionColumns, setConditionColumns] = useState<Record<string, string>>({
    '用户等级': 'STRING',
  });
  /** 动作列定义 name -> type */
  const [actionColumns, setActionColumns] = useState<Record<string, string>>({
    '结果': 'STRING',
  });
  /** 行数据 */
  const [rows, setRows] = useState<TableRowData[]>([
    { _key: newRowKey(), conditions: { '用户等级': '*' }, actions: { '结果': 'PASS' } },
  ]);

  const [ruleName, setRuleName] = useState('');

  const [convertLoading, setConvertLoading] = useState(false);
  const [validateLoading, setValidateLoading] = useState(false);

  const [resultModalOpen, setResultModalOpen] = useState(false);
  const [convertResult, setConvertResult] = useState<DecisionTableResponse | null>(null);

  const [validateResult, setValidateResult] = useState<DecisionTableValidateResponse | null>(null);

  /** 新增条件列 */
  const [addConditionOpen, setAddConditionOpen] = useState(false);
  const [addActionOpen, setAddActionOpen] = useState(false);
  const [addColForm] = Form.useForm();

  /** 构造请求体 */
  const buildRequest = useCallback((): DecisionTableRequest => {
    const cleanRows = rows.map(({ _key, ...rest }) => rest);
    return {
      conditionColumns,
      actionColumns,
      rows: cleanRows,
      ruleName: ruleName || undefined,
    };
  }, [conditionColumns, actionColumns, rows, ruleName]);

  /** 转换为 Groovy */
  const handleConvert = async () => {
    setConvertLoading(true);
    setValidateResult(null);
    try {
      const result = await convertToGroovy(buildRequest());
      setConvertResult(result);
      setResultModalOpen(true);
      if (!result.success) {
        message.warning('转换失败，请检查决策表配置');
      }
    } catch {
      message.error('转换请求失败');
    } finally {
      setConvertLoading(false);
    }
  };

  /** 验证决策表 */
  const handleValidate = async () => {
    setValidateLoading(true);
    try {
      const result = await validateDecisionTable(buildRequest());
      setValidateResult(result);
      if (result.valid) {
        message.success('验证通过');
      } else {
        message.error(`验证失败: ${result.errors.length} 个错误`);
      }
    } catch {
      message.error('验证请求失败');
    } finally {
      setValidateLoading(false);
    }
  };

  /** 添加条件列 */
  const handleAddCondition = () => {
    addColForm.validateFields().then((values) => {
      const { name, type } = values;
      if (conditionColumns[name] !== undefined) {
        message.warning('条件列名称已存在');
        return;
      }
      setConditionColumns((prev) => ({ ...prev, [name]: type }));
      // 为已有行添加默认值
      setRows((prev) =>
        prev.map((row) => ({
          ...row,
          conditions: { ...row.conditions, [name]: '*' },
        })),
      );
      setAddConditionOpen(false);
      addColForm.resetFields();
    });
  };

  /** 添加动作列 */
  const handleAddAction = () => {
    addColForm.validateFields().then((values) => {
      const { name, type } = values;
      if (actionColumns[name] !== undefined) {
        message.warning('动作列名称已存在');
        return;
      }
      setActionColumns((prev) => ({ ...prev, [name]: type }));
      setRows((prev) =>
        prev.map((row) => ({
          ...row,
          actions: { ...row.actions, [name]: '' },
        })),
      );
      setAddActionOpen(false);
      addColForm.resetFields();
    });
  };

  /** 删除条件列 */
  const handleRemoveCondition = (colName: string) => {
    setConditionColumns((prev) => {
      const next = { ...prev };
      delete next[colName];
      return next;
    });
    setRows((prev) =>
      prev.map((row) => {
        const conds = { ...row.conditions };
        delete conds[colName];
        return { ...row, conditions: conds };
      }),
    );
  };

  /** 删除动作列 */
  const handleRemoveAction = (colName: string) => {
    setActionColumns((prev) => {
      const next = { ...prev };
      delete next[colName];
      return next;
    });
    setRows((prev) =>
      prev.map((row) => {
        const acts = { ...row.actions };
        delete acts[colName];
        return { ...row, actions: acts };
      }),
    );
  };

  /** 添加行 */
  const handleAddRow = () => {
    const conditions: Record<string, unknown> = {};
    Object.keys(conditionColumns).forEach((k) => {
      conditions[k] = '*';
    });
    const actions: Record<string, unknown> = {};
    Object.keys(actionColumns).forEach((k) => {
      actions[k] = '';
    });
    setRows((prev) => [...prev, { _key: newRowKey(), conditions, actions }]);
  };

  /** 删除行 */
  const handleRemoveRow = (key: string) => {
    setRows((prev) => prev.filter((r) => r._key !== key));
  };

  /** 更新单元格 */
  const handleCellChange = (rowKey: string, field: 'conditions' | 'actions', colName: string, value: string) => {
    setRows((prev) =>
      prev.map((r) => {
        if (r._key !== rowKey) return r;
        return {
          ...r,
          [field]: { ...r[field], [colName]: value },
        };
      }),
    );
  };

  /** 构造表格列 */
  const tableColumns: ColumnsType<TableRowData> = [
    {
      title: '#',
      width: 50,
      render: (_, __, index) => index + 1,
    },
    ...Object.entries(conditionColumns).map(([name, type]) => ({
      title: () => (
        <Space size={4}>
          <Tag color="blue">{name}</Tag>
          <span style={{ fontSize: 12, color: '#999' }}>({type})</span>
          <Tooltip title="删除此列">
            <Button
              type="text"
              size="small"
              danger
              icon={<MinusCircleOutlined />}
              onClick={() => handleRemoveCondition(name)}
            />
          </Tooltip>
        </Space>
      ),
      dataIndex: ['conditions', name],
      width: 140,
      render: (_: unknown, record: TableRowData) => (
        <Input
          size="small"
          value={String(record.conditions[name] ?? '')}
          onChange={(e) => handleCellChange(record._key, 'conditions', name, e.target.value)}
          placeholder="*"
        />
      ),
    })),
    ...Object.entries(actionColumns).map(([name, type]) => ({
      title: () => (
        <Space size={4}>
          <Tag color="green">{name}</Tag>
          <span style={{ fontSize: 12, color: '#999' }}>({type})</span>
          <Tooltip title="删除此列">
            <Button
              type="text"
              size="small"
              danger
              icon={<MinusCircleOutlined />}
              onClick={() => handleRemoveAction(name)}
            />
          </Tooltip>
        </Space>
      ),
      dataIndex: ['actions', name],
      width: 140,
      render: (_: unknown, record: TableRowData) => (
        <Input
          size="small"
          value={String(record.actions[name] ?? '')}
          onChange={(e) => handleCellChange(record._key, 'actions', name, e.target.value)}
          placeholder="值"
        />
      ),
    })),
    {
      title: '操作',
      width: 60,
      render: (_, record) => (
        <Popconfirm
          title="确认删除此行？"
          onConfirm={() => handleRemoveRow(record._key)}
          okText="确认"
          cancelText="取消"
        >
          <Button type="text" danger size="small" icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ];

  return (
    <>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: '决策表管理' }]} />

      <Card>
        {/* 顶部：规则名和操作按钮 */}
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
          <Space>
            <Input
              placeholder="规则名称（可选）"
              value={ruleName}
              onChange={(e) => setRuleName(e.target.value)}
              style={{ width: 240 }}
            />
          </Space>
          <Space>
            <Button icon={<PlusOutlined />} onClick={() => setAddConditionOpen(true)}>
              添加条件列
            </Button>
            <Button icon={<PlusOutlined />} onClick={() => setAddActionOpen(true)}>
              添加动作列
            </Button>
          </Space>
        </div>

        {/* 决策表编辑器 */}
        {Object.keys(conditionColumns).length === 0 && Object.keys(actionColumns).length === 0 ? (
          <Empty description="请先添加条件列和动作列" />
        ) : (
          <Table
            rowKey="_key"
            columns={tableColumns}
            dataSource={rows}
            pagination={false}
            size="small"
            bordered
            scroll={{ x: 'max-content' }}
            footer={() => (
              <Button type="dashed" block icon={<PlusOutlined />} onClick={handleAddRow}>
                添加规则行
              </Button>
            )}
          />
        )}

        {/* 验证结果 */}
        {validateResult && (
          <div style={{ marginTop: 16 }}>
            {validateResult.valid ? (
              <Alert
                type="success"
                message="验证通过"
                description={
                  validateResult.warnings.length > 0 ? (
                    <ul style={{ margin: 0, paddingLeft: 20 }}>
                      {validateResult.warnings.map((w, i) => (
                        <li key={i}>{w}</li>
                      ))}
                    </ul>
                  ) : undefined
                }
                showIcon
                icon={<CheckCircleOutlined />}
              />
            ) : (
              <Alert
                type="error"
                message={`验证失败 (${validateResult.errors.length} 个错误)`}
                description={
                  <ul style={{ margin: 0, paddingLeft: 20 }}>
                    {validateResult.errors.map((e, i) => (
                      <li key={i}>{e}</li>
                    ))}
                  </ul>
                }
                showIcon
              />
            )}
          </div>
        )}

        {/* 底部操作 */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
          <Space>
            <Button
              icon={<CheckCircleOutlined />}
              loading={validateLoading}
              onClick={handleValidate}
            >
              验证决策表
            </Button>
            <Button
              type="primary"
              icon={<CodeOutlined />}
              loading={convertLoading}
              onClick={handleConvert}
            >
              转换为 Groovy
            </Button>
          </Space>
        </div>
      </Card>

      {/* Groovy 脚本预览弹窗 */}
      <Modal
        title="生成的 Groovy 脚本"
        open={resultModalOpen}
        onCancel={() => {
          setResultModalOpen(false);
          setConvertResult(null);
        }}
        footer={null}
        width={720}
      >
        {convertResult ? (
          convertResult.success ? (
            <>
              <Alert
                type="success"
                message={`转换成功，共 ${convertResult.rowCount} 行规则`}
                showIcon
                style={{ marginBottom: 12 }}
              />
              <Input.TextArea
                value={convertResult.groovyScript ?? ''}
                readOnly
                rows={16}
                style={{ fontFamily: 'monospace' }}
              />
            </>
          ) : (
            <Alert
              type="error"
              message="转换失败"
              description={convertResult.errorMessage ?? '未知错误'}
              showIcon
            />
          )
        ) : null}
      </Modal>

      {/* 添加条件列弹窗 */}
      <Modal
        title="添加条件列"
        open={addConditionOpen}
        onOk={handleAddCondition}
        onCancel={() => {
          setAddConditionOpen(false);
          addColForm.resetFields();
        }}
        okText="添加"
        cancelText="取消"
        width={400}
      >
        <Form form={addColForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="列名称"
            rules={[{ required: true, message: '请输入条件列名称' }]}
          >
            <Input placeholder="如：用户等级" />
          </Form.Item>
          <Form.Item
            name="type"
            label="列类型"
            rules={[{ required: true, message: '请选择类型' }]}
            initialValue="STRING"
          >
            <Select options={COLUMN_TYPE_OPTIONS} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 添加动作列弹窗 */}
      <Modal
        title="添加动作列"
        open={addActionOpen}
        onOk={handleAddAction}
        onCancel={() => {
          setAddActionOpen(false);
          addColForm.resetFields();
        }}
        okText="添加"
        cancelText="取消"
        width={400}
      >
        <Form form={addColForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="列名称"
            rules={[{ required: true, message: '请输入动作列名称' }]}
          >
            <Input placeholder="如：结果" />
          </Form.Item>
          <Form.Item
            name="type"
            label="列类型"
            rules={[{ required: true, message: '请选择类型' }]}
            initialValue="STRING"
          >
            <Select options={COLUMN_TYPE_OPTIONS} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
