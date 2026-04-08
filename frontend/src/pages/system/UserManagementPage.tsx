import { useEffect, useState } from 'react';
import {
  Table, Button, Space, Card, Breadcrumb, Modal, Form, Input, Select,
  Switch, App, Tag, Typography,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import * as systemApi from '../../api/system';
import type { UserDTO, RoleDTO } from '../../api/system';

const { Text } = Typography;

export default function UserManagementPage() {
  const { message, modal } = App.useApp();
  const { t } = useTranslation();
  const [users, setUsers] = useState<UserDTO[]>([]);
  const [roles, setRoles] = useState<RoleDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserDTO | null>(null);
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();

  const loadUsers = async () => {
    setLoading(true);
    try {
      const data = await systemApi.listUsers();
      setUsers(data);
    } catch (e: any) {
      message.error(t('users.loadFailed') + ': ' + (e.response?.data?.message || e.message));
    } finally {
      setLoading(false);
    }
  };

  const loadRoles = async () => {
    try {
      const data = await systemApi.listRoles();
      setRoles(data);
    } catch {
      // Ignore role loading errors
    }
  };

  useEffect(() => {
    loadUsers();
    loadRoles();
  }, []);

  const handleCreate = async (values: any) => {
    try {
      await systemApi.createUser({
        username: values.username,
        password: values.password,
        nickname: values.nickname,
        email: values.email,
        phone: values.phone,
        roleIds: values.roleIds,
      });
      message.success(t('users.createSuccess'));
      setCreateModalOpen(false);
      createForm.resetFields();
      loadUsers();
    } catch (e: any) {
      message.error(t('users.createFailed') + ': ' + (e.response?.data?.message || e.message));
    }
  };

  const handleEdit = async (values: any) => {
    if (!editingUser) return;
    try {
      await systemApi.updateUser(editingUser.id, {
        nickname: values.nickname,
        email: values.email,
        phone: values.phone,
        roleIds: values.roleIds,
        status: values.status,
      });
      message.success(t('users.updateSuccess'));
      setEditModalOpen(false);
      setEditingUser(null);
      editForm.resetFields();
      loadUsers();
    } catch (e: any) {
      message.error(t('users.updateFailed') + ': ' + (e.response?.data?.message || e.message));
    }
  };

  const handleStatusChange = async (user: UserDTO, checked: boolean) => {
    const newStatus = checked ? 'ACTIVE' : 'DISABLED';
    try {
      await systemApi.updateUserStatus(user.id, newStatus);
      message.success(t('users.statusChanged', { status: checked ? t('users.enableLabel') : t('users.disableLabel') }));
      loadUsers();
    } catch (e: any) {
      message.error(t('users.operationFailed') + ': ' + (e.response?.data?.message || e.message));
    }
  };

  const handleResetPassword = (user: UserDTO) => {
    modal.confirm({
      title: t('users.resetPasswordTitle'),
      content: t('users.resetPasswordContent', { name: user.nickname || user.username }),
      onOk: async () => {
        try {
          await systemApi.resetPassword(user.id);
          message.success(t('users.resetPasswordSuccess'));
        } catch (e: any) {
          message.error(t('users.resetPasswordFailed') + ': ' + (e.response?.data?.message || e.message));
        }
      },
    });
  };

  const openEditModal = (user: UserDTO) => {
    setEditingUser(user);
    editForm.setFieldsValue({
      nickname: user.nickname,
      email: user.email,
      phone: user.phone,
      roleIds: user.roles.map((r) => r.id),
      status: user.status,
    });
    setEditModalOpen(true);
  };

  const columns: ColumnsType<UserDTO> = [
    {
      title: t('users.username'),
      dataIndex: 'username',
      key: 'username',
      width: 120,
    },
    {
      title: t('users.nickname'),
      dataIndex: 'nickname',
      key: 'nickname',
      width: 120,
      render: (v: string | null) => v || '-',
    },
    {
      title: t('users.roles'),
      dataIndex: 'roles',
      key: 'roles',
      width: 200,
      render: (roles: RoleDTO[]) =>
        roles.map((r) => (
          <Tag key={r.id} color="blue">
            {r.roleName}
          </Tag>
        )),
    },
    {
      title: t('users.email'),
      dataIndex: 'email',
      key: 'email',
      width: 180,
      render: (v: string | null) => v || '-',
    },
    {
      title: t('common.status'),
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string, record: UserDTO) => (
        <Switch
          checked={status === 'ACTIVE'}
          onChange={(checked) => handleStatusChange(record, checked)}
          checkedChildren={t('users.enableLabel')}
          unCheckedChildren={t('users.disableLabel')}
        />
      ),
    },
    {
      title: t('common.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (v: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 180,
      render: (_: any, record: UserDTO) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEditModal(record)}>
            {t('common.edit')}
          </Button>
          <Button type="link" size="small" danger onClick={() => handleResetPassword(record)}>
            {t('users.resetPassword')}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: t('system.systemManagement') }, { title: t('users.pageTitle') }]} />

      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
          <Text strong style={{ fontSize: 16 }}>
            {t('users.pageTitle')}
          </Text>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadUsers}>
              {t('common.refresh')}
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
              {t('users.createUser')}
            </Button>
          </Space>
        </div>

        <Table
          columns={columns}
          dataSource={users}
          rowKey="id"
          loading={loading}
          pagination={false}
          size="middle"
        />
      </Card>

      {/* 创建用户 Modal */}
      <Modal
        title={t('users.createUser')}
        open={createModalOpen}
        onCancel={() => {
          setCreateModalOpen(false);
          createForm.resetFields();
        }}
        onOk={() => createForm.submit()}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" onFinish={handleCreate}>
          <Form.Item name="username" label={t('users.username')} rules={[{ required: true, message: t('users.usernameRequired') }, { min: 3, message: t('users.usernameMinLength') }]}>
            <Input placeholder={t('users.usernamePlaceholder')} />
          </Form.Item>
          <Form.Item name="password" label={t('users.password')} rules={[{ required: true, message: t('users.passwordRequired') }, { min: 6, message: t('users.passwordMinLength') }]}>
            <Input.Password placeholder={t('users.passwordPlaceholder')} />
          </Form.Item>
          <Form.Item name="nickname" label={t('users.nickname')}>
            <Input placeholder={t('users.nicknamePlaceholder')} />
          </Form.Item>
          <Form.Item name="email" label={t('users.email')}>
            <Input placeholder={t('users.emailPlaceholder')} />
          </Form.Item>
          <Form.Item name="phone" label={t('users.phone')}>
            <Input placeholder={t('users.phonePlaceholder')} />
          </Form.Item>
          <Form.Item name="roleIds" label={t('users.roles')}>
            <Select
              mode="multiple"
              placeholder={t('users.rolePlaceholder')}
              options={roles.map((r) => ({ label: r.roleName, value: r.id }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 编辑用户 Modal */}
      <Modal
        title={t('users.editUser')}
        open={editModalOpen}
        onCancel={() => {
          setEditModalOpen(false);
          setEditingUser(null);
          editForm.resetFields();
        }}
        onOk={() => editForm.submit()}
        destroyOnClose
      >
        <Form form={editForm} layout="vertical" onFinish={handleEdit}>
          <Form.Item name="nickname" label={t('users.nickname')}>
            <Input placeholder={t('users.nicknamePlaceholder')} />
          </Form.Item>
          <Form.Item name="email" label={t('users.email')}>
            <Input placeholder={t('users.emailPlaceholder')} />
          </Form.Item>
          <Form.Item name="phone" label={t('users.phone')}>
            <Input placeholder={t('users.phonePlaceholder')} />
          </Form.Item>
          <Form.Item name="roleIds" label={t('users.roles')}>
            <Select
              mode="multiple"
              placeholder={t('users.rolePlaceholder')}
              options={roles.map((r) => ({ label: r.roleName, value: r.id }))}
            />
          </Form.Item>
          <Form.Item name="status" label={t('common.status')}>
            <Select
              options={[
                { label: t('users.enableLabel'), value: 'ACTIVE' },
                { label: t('users.disableLabel'), value: 'DISABLED' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
