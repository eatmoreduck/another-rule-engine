import { useState } from 'react';
import { Modal, Form, Input, message } from 'antd';
import { createRule } from '../../api/rules';
import type { CreateRuleRequest } from '../../types/rule';

const DEFAULT_SCRIPT = `def evaluate(Map features) {\n  // 在此编写规则逻辑\n  return [decision: 'PASS', reason: '默认通过']\n}`;

interface CreateRuleModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export default function CreateRuleModal({ open, onClose, onSuccess }: CreateRuleModalProps) {
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
      message.success('规则创建成功');
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
      title="新建规则"
      open={open}
      onOk={handleOk}
      onCancel={onClose}
      confirmLoading={confirmLoading}
      okText="创建"
      cancelText="取消"
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="ruleKey"
          label="规则 Key"
          rules={[{ required: true, message: '请输入规则 Key' }]}
        >
          <Input placeholder="如: risk-check-v1" />
        </Form.Item>
        <Form.Item
          name="ruleName"
          label="规则名称"
          rules={[{ required: true, message: '请输入规则名称' }]}
        >
          <Input placeholder="如: 风险检查规则" />
        </Form.Item>
        <Form.Item name="ruleDescription" label="规则描述">
          <Input.TextArea rows={3} placeholder="可选：描述规则用途" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
