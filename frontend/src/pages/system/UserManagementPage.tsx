import { useEffect, useState } from 'react';
import {
  Table, Button, Space, Card, Breadcrumb, Modal, Form, Input, Select,
  Switch, App, Tag, Typography,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import * as systemApi from '../../api/system';
import type { UserDTO, RoleDTO } from '../../api/system';

const { Text } = Typography;

export default function UserManagementPage() {
  const { message, modal } = App.useApp();
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
      message.error('加载用户列表失败: ' + (e.response?.data?.message || e.message));
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
      message.success('创建用户成功');
      setCreateModalOpen(false);
      createForm.resetFields();
      loadUsers();
    } catch (e: any) {
      message.error('创建用户失败: ' + (e.response?.data?.message || e.message));
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
      message.success('更新用户成功');
      setEditModalOpen(false);
      setEditingUser(null);
      editForm.resetFields();
      loadUsers();
    } catch (e: any) {
      message.error('更新用户失败: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleStatusChange = async (user: UserDTO, checked: boolean) => {
    const newStatus = checked ? 'ACTIVE' : 'DISABLED';
    try {
      await systemApi.updateUserStatus(user.id, newStatus);
      message.success(`用户已${checked ? '启用' : '禁用'}`);
      loadUsers();
    } catch (e: any) {
      message.error('操作失败: ' + (e.response?.data?.message || e.message));
    }
  };

  const handleResetPassword = (user: UserDTO) => {
    modal.confirm({
      title: '确认重置密码',
      content: `确定要重置用户 "${user.nickname || user.username}" 的密码吗？密码将重置为默认密码。`,
      onOk: async () => {
        try {
          await systemApi.resetPassword(user.id);
          message.success('密码已重置');
        } catch (e: any) {
          message.error('重置密码失败: ' + (e.response?.data?.message || e.message));
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
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      width: 120,
    },
    {
      title: '姓名',
      dataIndex: 'nickname',
      key: 'nickname',
      width: 120,
      render: (v: string | null) => v || '-',
    },
    {
      title: '角色',
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
      title: '邮箱',
      dataIndex: 'email',
      key: 'email',
      width: 180,
      render: (v: string | null) => v || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string, record: UserDTO) => (
        <Switch
          checked={status === 'ACTIVE'}
          onChange={(checked) => handleStatusChange(record, checked)}
          checkedChildren="启用"
          unCheckedChildren="禁用"
        />
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (v: string) => (v ? new Date(v).toLocaleString() : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_: any, record: UserDTO) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEditModal(record)}>
            编辑
          </Button>
          <Button type="link" size="small" danger onClick={() => handleResetPassword(record)}>
            重置密码
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: '系统管理' }, { title: '用户管理' }]} />

      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
          <Text strong style={{ fontSize: 16 }}>
            用户管理
          </Text>
          <Space>
            <Button icon={<ReloadOutlined />} onClick={loadUsers}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalOpen(true)}>
              新建用户
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
        title="新建用户"
        open={createModalOpen}
        onCancel={() => {
          setCreateModalOpen(false);
          createForm.resetFields();
        }}
        onOk={() => createForm.submit()}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical" onFinish={handleCreate}>
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }, { min: 3, message: '至少3个字符' }]}>
            <Input placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }, { min: 6, message: '至少6个字符' }]}>
            <Input.Password placeholder="请输入密码" />
          </Form.Item>
          <Form.Item name="nickname" label="姓名">
            <Input placeholder="请输入姓名" />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item name="phone" label="手机号">
            <Input placeholder="请输入手机号" />
          </Form.Item>
          <Form.Item name="roleIds" label="角色">
            <Select
              mode="multiple"
              placeholder="请选择角色"
              options={roles.map((r) => ({ label: r.roleName, value: r.id }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 编辑用户 Modal */}
      <Modal
        title="编辑用户"
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
          <Form.Item name="nickname" label="姓名">
            <Input placeholder="请输入姓名" />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input placeholder="请输入邮箱" />
          </Form.Item>
          <Form.Item name="phone" label="手机号">
            <Input placeholder="请输入手机号" />
          </Form.Item>
          <Form.Item name="roleIds" label="角色">
            <Select
              mode="multiple"
              placeholder="请选择角色"
              options={roles.map((r) => ({ label: r.roleName, value: r.id }))}
            />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { label: '启用', value: 'ACTIVE' },
                { label: '禁用', value: 'DISABLED' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
