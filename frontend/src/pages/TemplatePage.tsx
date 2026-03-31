import { useEffect, useState, useCallback, useMemo } from 'react';
import {
  Button,
  Card,
  Table,
  Tag,
  Space,
  Modal,
  Form,
  Input,
  Select,
  message,
  Breadcrumb,
  Tabs,
  Row,
  Col,
  Empty,
  Popconfirm,
  Spin,
} from 'antd';
import {
  AppstoreOutlined,
  UserOutlined,
  DeleteOutlined,
  CopyOutlined,
  SaveOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import {
  getTemplates,
  getCustomTemplates,
  instantiateFromTemplate,
  instantiateFromCustomTemplate,
  saveCustomTemplate,
  deleteCustomTemplate,
} from '../api/template';
import type {
  RuleTemplate,
  CustomTemplate,
  InstantiateTemplateRequest,
  CreateCustomTemplateRequest,
} from '../types/template';
import { getRules } from '../api/rules';
import type { Rule } from '../types/rule';

/** 模板分类标签 */
const CATEGORY_LABELS: Record<string, string> = {
  RISK_CONTROL: '风控规则',
  TRANSACTION: '交易规则',
  ANTI_FRAUD: '反欺诈',
  MARKETING: '营销规则',
  OPERATION: '运营规则',
  OTHER: '其他',
};

export default function TemplatePage() {
  const [systemTemplates, setSystemTemplates] = useState<RuleTemplate[]>([]);
  const [systemLoading, setSystemLoading] = useState(false);

  const [customTemplates, setCustomTemplates] = useState<CustomTemplate[]>([]);
  const [customLoading, setCustomLoading] = useState(false);

  // 实例化弹窗
  const [instantiateModalOpen, setInstantiateModalOpen] = useState(false);
  const [instantiateLoading, setInstantiateLoading] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<{
    id: number;
    name: string;
    parameters: string | null;
    isCustom: boolean;
  } | null>(null);
  const [instantiateForm] = Form.useForm();

  // 保存为模板弹窗
  const [saveModalOpen, setSaveModalOpen] = useState(false);
  const [saveLoading, setSaveLoading] = useState(false);
  const [rules, setRules] = useState<Rule[]>([]);
  const [saveForm] = Form.useForm();

  /** 按分类分组系统模板 */
  const groupedTemplates = useMemo(() => {
    const groups: Record<string, RuleTemplate[]> = {};
    for (const t of systemTemplates) {
      const key = t.category || 'OTHER';
      if (!groups[key]) groups[key] = [];
      groups[key].push(t);
    }
    return groups;
  }, [systemTemplates]);

  /** 加载系统模板 */
  const loadSystemTemplates = useCallback(async () => {
    setSystemLoading(true);
    try {
      const data = await getTemplates();
      setSystemTemplates(data);
    } catch {
      message.error('加载系统模板失败');
    } finally {
      setSystemLoading(false);
    }
  }, []);

  /** 加载个人模板 */
  const loadCustomTemplates = useCallback(async () => {
    setCustomLoading(true);
    try {
      const data = await getCustomTemplates();
      setCustomTemplates(data);
    } catch {
      message.error('加载个人模板失败');
    } finally {
      setCustomLoading(false);
    }
  }, []);

  /** 加载规则列表（用于保存为模板） */
  const loadRules = useCallback(async () => {
    try {
      const result = await getRules({ page: 0, size: 200 });
      setRules(result.content);
    } catch {
      message.error('加载规则列表失败');
    }
  }, []);

  useEffect(() => {
    loadSystemTemplates();
    loadCustomTemplates();
  }, [loadSystemTemplates, loadCustomTemplates]);

  /** 打开实例化弹窗 */
  const handleOpenInstantiate = (
    id: number,
    name: string,
    parameters: string | null,
    isCustom: boolean,
  ) => {
    setSelectedTemplate({ id, name, parameters, isCustom });
    instantiateForm.resetFields();
    instantiateForm.setFieldsValue({ ruleName: name });
    setInstantiateModalOpen(true);
  };

  /** 执行实例化 */
  const handleInstantiate = async () => {
    if (!selectedTemplate) return;
    try {
      const values = await instantiateForm.validateFields();
      setInstantiateLoading(true);
      const request: InstantiateTemplateRequest = {
        ruleKey: values.ruleKey,
        ruleName: values.ruleName,
        ruleDescription: values.ruleDescription,
        parameters: values.parameters ? JSON.parse(values.parameters) : undefined,
      };

      if (selectedTemplate.isCustom) {
        await instantiateFromCustomTemplate(selectedTemplate.id, request);
      } else {
        await instantiateFromTemplate(selectedTemplate.id, request);
      }

      message.success('规则创建成功');
      setInstantiateModalOpen(false);
      instantiateForm.resetFields();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        message.error('创建规则失败');
      } else if (err instanceof SyntaxError) {
        message.error('参数格式错误，请输入有效的 JSON');
      }
    } finally {
      setInstantiateLoading(false);
    }
  };

  /** 打开保存为模板弹窗 */
  const handleOpenSaveModal = () => {
    saveForm.resetFields();
    loadRules();
    setSaveModalOpen(true);
  };

  /** 执行保存为模板 */
  const handleSaveTemplate = async () => {
    try {
      const values = await saveForm.validateFields();
      setSaveLoading(true);
      const selectedRule = rules.find((r) => r.id === values.ruleId);
      if (!selectedRule) {
        message.error('请选择一条规则');
        return;
      }
      const request: CreateCustomTemplateRequest = {
        name: values.name,
        description: values.description,
        groovyTemplate: selectedRule.groovyScript,
      };
      await saveCustomTemplate(request);
      message.success('模板保存成功');
      setSaveModalOpen(false);
      saveForm.resetFields();
      loadCustomTemplates();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        message.error('保存模板失败');
      }
    } finally {
      setSaveLoading(false);
    }
  };

  /** 删除个人模板 */
  const handleDeleteCustom = async (id: number) => {
    try {
      await deleteCustomTemplate(id);
      message.success('模板已删除');
      loadCustomTemplates();
    } catch {
      message.error('删除模板失败');
    }
  };

  /** 个人模板表格列 */
  const customColumns: ColumnsType<CustomTemplate> = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60,
    },
    {
      title: '模板名称',
      dataIndex: 'name',
      ellipsis: true,
    },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
      render: (text: string | null) => text || '-',
    },
    {
      title: '创建人',
      dataIndex: 'createdBy',
      width: 120,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<CopyOutlined />}
            onClick={() =>
              handleOpenInstantiate(record.id, record.name, record.parameters, true)
            }
          >
            使用
          </Button>
          <Popconfirm
            title="确认删除此模板？"
            onConfirm={() => handleDeleteCustom(record.id)}
            okText="确认"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  /** 渲染系统模板卡片网格 */
  const renderSystemTemplates = () => {
    if (systemLoading) {
      return (
        <div style={{ textAlign: 'center', padding: '60px 0' }}>
          <Spin />
        </div>
      );
    }

    if (systemTemplates.length === 0) {
      return <Empty description="暂无系统模板" />;
    }

    const categoryEntries = Object.entries(groupedTemplates);

    return categoryEntries.map(([category, templates]) => (
      <div key={category} style={{ marginBottom: 24 }}>
        <div style={{ marginBottom: 12, fontSize: 16, fontWeight: 500 }}>
          <Tag color="blue">{CATEGORY_LABELS[category] || category}</Tag>
          <span style={{ color: '#999', fontSize: 13 }}>({templates.length})</span>
        </div>
        <Row gutter={[16, 16]}>
          {templates.map((t) => (
            <Col key={t.id} xs={24} sm={12} md={8} lg={6}>
              <Card
                size="small"
                title={t.name}
                hoverable
                extra={
                  <Button
                    type="link"
                    size="small"
                    icon={<CopyOutlined />}
                    onClick={() =>
                      handleOpenInstantiate(t.id, t.name, t.parameters, false)
                    }
                  >
                    使用
                  </Button>
                }
              >
                <div
                  style={{
                    color: '#666',
                    fontSize: 13,
                    height: 40,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    display: '-webkit-box',
                    WebkitLineClamp: 2,
                    WebkitBoxOrient: 'vertical',
                  }}
                >
                  {t.description || '暂无描述'}
                </div>
              </Card>
            </Col>
          ))}
        </Row>
      </div>
    ));
  };

  return (
    <>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: '规则模板库' }]} />

      <Card>
        <Tabs
          defaultActiveKey="system"
          items={[
            {
              key: 'system',
              label: (
                <span>
                  <AppstoreOutlined /> 系统模板
                </span>
              ),
              children: (
                <div style={{ minHeight: 200 }}>
                  <div style={{ textAlign: 'right', marginBottom: 16 }}>
                    <Button icon={<ReloadOutlined />} onClick={loadSystemTemplates}>
                      刷新
                    </Button>
                  </div>
                  {renderSystemTemplates()}
                </div>
              ),
            },
            {
              key: 'custom',
              label: (
                <span>
                  <UserOutlined /> 我的模板
                </span>
              ),
              children: (
                <div>
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'flex-end',
                      marginBottom: 16,
                    }}
                  >
                    <Space>
                      <Button icon={<ReloadOutlined />} onClick={loadCustomTemplates}>
                        刷新
                      </Button>
                      <Button
                        type="primary"
                        icon={<SaveOutlined />}
                        onClick={handleOpenSaveModal}
                      >
                        保存为模板
                      </Button>
                    </Space>
                  </div>
                  <Table
                    rowKey="id"
                    columns={customColumns}
                    dataSource={customTemplates}
                    loading={customLoading}
                    pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 条` }}
                  />
                </div>
              ),
            },
          ]}
        />
      </Card>

      {/* 实例化弹窗 */}
      <Modal
        title={`使用模板: ${selectedTemplate?.name || ''}`}
        open={instantiateModalOpen}
        onOk={handleInstantiate}
        onCancel={() => {
          setInstantiateModalOpen(false);
          instantiateForm.resetFields();
        }}
        confirmLoading={instantiateLoading}
        okText="创建规则"
        cancelText="取消"
        width={520}
      >
        <Form form={instantiateForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="ruleKey"
            label="规则 Key"
            rules={[{ required: true, message: '请输入规则 Key' }]}
          >
            <Input placeholder="请输入新规则的 Key（唯一标识）" />
          </Form.Item>
          <Form.Item
            name="ruleName"
            label="规则名称"
            rules={[{ required: true, message: '请输入规则名称' }]}
          >
            <Input placeholder="请输入规则名称" />
          </Form.Item>
          <Form.Item name="ruleDescription" label="规则描述">
            <Input.TextArea rows={2} placeholder="请输入规则描述（可选）" />
          </Form.Item>
          {selectedTemplate?.parameters && (
            <Form.Item
              name="parameters"
              label="模板参数"
              extra="请输入 JSON 格式的参数值"
              rules={[
                {
                  validator: (_, value) => {
                    if (!value) return Promise.resolve();
                    try {
                      JSON.parse(value);
                      return Promise.resolve();
                    } catch {
                      return Promise.reject(new Error('请输入有效的 JSON'));
                    }
                  },
                },
              ]}
            >
              <Input.TextArea
                rows={3}
                placeholder={`例如: ${selectedTemplate.parameters}`}
              />
            </Form.Item>
          )}
        </Form>
      </Modal>

      {/* 保存为模板弹窗 */}
      <Modal
        title="保存为模板"
        open={saveModalOpen}
        onOk={handleSaveTemplate}
        onCancel={() => {
          setSaveModalOpen(false);
          saveForm.resetFields();
        }}
        confirmLoading={saveLoading}
        okText="保存"
        cancelText="取消"
        width={520}
      >
        <Form form={saveForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="ruleId"
            label="选择规则"
            rules={[{ required: true, message: '请选择一条规则' }]}
          >
            <Select
              placeholder="请选择要保存为模板的规则"
              showSearch
              optionFilterProp="label"
              options={rules.map((r) => ({
                value: r.id,
                label: `${r.ruleName} (${r.ruleKey})`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="name"
            label="模板名称"
            rules={[{ required: true, message: '请输入模板名称' }]}
          >
            <Input placeholder="请输入模板名称" />
          </Form.Item>
          <Form.Item name="description" label="模板描述">
            <Input.TextArea rows={2} placeholder="请输入模板描述（可选）" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
