import { useEffect, useState, useCallback } from 'react';
import {
  Button,
  Card,
  Tag,
  Space,
  Modal,
  Form,
  Select,
  message,
  Breadcrumb,
  Row,
  Col,
  Descriptions,
  Spin,
} from 'antd';
import {
  CopyOutlined,
  CloudOutlined,
  DatabaseOutlined,
  RocketOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import {
  listEnvironments,
  cloneEnvironment,
} from '../api/environment';
import type {
  Environment,
  EnvironmentType,
} from '../types/environment';
import {
  EnvironmentType as EnvTypeEnum,
  ENVIRONMENT_TYPE_LABELS,
} from '../types/environment';

/** 环境类型对应的图标 */
const TYPE_ICON_MAP: Record<EnvironmentType, React.ReactNode> = {
  [EnvTypeEnum.DEV]: <DatabaseOutlined style={{ fontSize: 32 }} />,
  [EnvTypeEnum.STAGING]: <CloudOutlined style={{ fontSize: 32 }} />,
  [EnvTypeEnum.PRODUCTION]: <RocketOutlined style={{ fontSize: 32 }} />,
};

export default function EnvironmentPage() {
  const { t } = useTranslation();
  const [environments, setEnvironments] = useState<Environment[]>([]);
  const [loading, setLoading] = useState(false);

  const [cloneModalOpen, setCloneModalOpen] = useState(false);
  const [cloneLoading, setCloneLoading] = useState(false);
  const [cloneSource, setCloneSource] = useState<Environment | null>(null);
  const [cloneForm] = Form.useForm();

  /** 加载环境列表 */
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listEnvironments();
      setEnvironments(result);
    } catch {
      message.error(t('environment.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  /** 打开克隆弹窗 */
  const handleOpenClone = (env: Environment) => {
    setCloneSource(env);
    setCloneModalOpen(true);
  };

  /** 执行克隆 */
  const handleClone = async () => {
    if (!cloneSource) return;
    try {
      const values = await cloneForm.validateFields();
      setCloneLoading(true);
      const result = await cloneEnvironment(
        cloneSource.name,
        values.targetEnvironment,
        { overwrite: values.overwrite ?? false },
      );
      if (result.success) {
        message.success(result.message);
      } else {
        message.error(t('environment.cloneFailed'));
      }
      setCloneModalOpen(false);
      cloneForm.resetFields();
      setCloneSource(null);
      loadData();
    } catch (err: unknown) {
      if (err && typeof err === 'object' && 'response' in err) {
        message.error(t('environment.cloneEnvFailed'));
      }
    } finally {
      setCloneLoading(false);
    }
  };

  /** 获取可选的目标环境（排除源环境） */
  const getTargetOptions = () => {
    if (!cloneSource) return [];
    return environments
      .filter((env) => env.id !== cloneSource.id)
      .map((env) => ({
        value: env.name,
        label: (
          <Space>
            <Tag color={ENVIRONMENT_TYPE_LABELS[env.type].color}>
              {ENVIRONMENT_TYPE_LABELS[env.type].label}
            </Tag>
            {env.name}
          </Space>
        ),
      }));
  };

  return (
    <>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: t('environment.pageTitle') }]} />

      <Spin spinning={loading}>
        <Row gutter={[16, 16]}>
          {environments.map((env) => {
            const typeInfo = ENVIRONMENT_TYPE_LABELS[env.type];
            return (
              <Col xs={24} sm={12} md={8} key={env.id}>
                <Card
                  hoverable
                  style={{ borderTop: `3px solid ${typeInfo.color === 'blue' ? '#1677ff' : typeInfo.color === 'orange' ? '#fa8c16' : '#f5222d'}` }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', marginBottom: 16 }}>
                    <div style={{ marginRight: 16, color: '#666' }}>
                      {TYPE_ICON_MAP[env.type]}
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontSize: 16, fontWeight: 500 }}>{env.name}</div>
                      <Tag color={typeInfo.color} style={{ marginTop: 4 }}>
                        {typeInfo.label}
                      </Tag>
                    </div>
                  </div>

                  <Descriptions size="small" column={1} style={{ marginBottom: 16 }}>
                    <Descriptions.Item label={t('common.description')}>
                      {env.description || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label={t('common.createdAt')}>
                      {env.createdAt}
                    </Descriptions.Item>
                  </Descriptions>

                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <Button
                      size="small"
                      icon={<CopyOutlined />}
                      onClick={() => handleOpenClone(env)}
                    >
                      {t('environment.cloneTo')}
                    </Button>
                  </div>
                </Card>
              </Col>
            );
          })}

          {environments.length === 0 && !loading && (
            <Col span={24}>
              <Card>
                <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>
                  {t('environment.noEnvironments')}
                </div>
              </Card>
            </Col>
          )}
        </Row>
      </Spin>

      {/* 克隆环境弹窗 */}
      <Modal
        title={t('environment.cloneTitle', { name: cloneSource?.name ?? '' })}
        open={cloneModalOpen}
        onOk={handleClone}
        onCancel={() => {
          setCloneModalOpen(false);
          cloneForm.resetFields();
          setCloneSource(null);
        }}
        confirmLoading={cloneLoading}
        okText={t('environment.startClone')}
        cancelText={t('common.cancel')}
        width={480}
      >
        <Form form={cloneForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="targetEnvironment"
            label={t('environment.targetEnv')}
            rules={[{ required: true, message: t('environment.targetEnvRequired') }]}
          >
            <Select
              placeholder={t('environment.targetEnvPlaceholder')}
              options={getTargetOptions()}
            />
          </Form.Item>
          <Form.Item
            name="overwrite"
            label={t('environment.overwriteMode')}
            initialValue={false}
            extra={t('environment.overwriteHint')}
          >
            <Select
              options={[
                { value: false, label: t('environment.overwriteFalse') },
                { value: true, label: t('environment.overwriteTrue') },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
