import { useState } from 'react';
import { Modal, Form, Input, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { createRule } from '../../api/rules';
import type { CreateRuleRequest } from '../../types/rule';

const DEFAULT_SCRIPT = `def evaluate(Map features) {\n  // 在此编写规则逻辑\n  return [decision: 'PASS', reason: '默认通过']\n}`;

interface CreateRuleModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export default function CreateRuleModal({ open, onClose, onSuccess }: CreateRuleModalProps) {
  const { t } = useTranslation();
  const [form] = Form.useForm<CreateRuleRequest>();
  const [confirmLoading, setConfirmLoading] = useState(false);

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      setConfirmLoading(true);
      await createRule({
        ...values,
        groovyScript: DEFAULT_SCRIPT,
      });
      message.success(t('rules.createSuccess'));
      form.resetFields();
      onSuccess();
      onClose();
    } catch (err) {
      if (err instanceof Error) {
        message.error(err.message);
      }
    } finally {
      setConfirmLoading(false);
    }
  };

  return (
    <Modal
      title={t('rules.createRule')}
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={confirmLoading}
      okText={t('common.create')}
      cancelText={t('common.cancel')}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="ruleKey"
          label={t('rules.ruleKey')}
          rules={[{ required: true, message: t('rules.createRuleKeyRequired') }]}
        >
          <Input placeholder="如: risk-check-v1" />
        </Form.Item>
        <Form.Item
          name="ruleName"
          label={t('rules.ruleName')}
          rules={[{ required: true, message: t('rules.createRuleNameRequired') }]}
        >
          <Input placeholder="如: 风险检查规则" />
        </Form.Item>
        <Form.Item name="ruleDescription" label={t('rules.ruleDescription')}>
          <Input.TextArea rows={3} placeholder={t('rules.createRuleDescOptional')} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
