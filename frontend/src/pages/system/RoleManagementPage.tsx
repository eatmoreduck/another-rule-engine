import { useEffect, useState } from 'react';
import {
  Card, Breadcrumb, List, Typography, Checkbox, Button, Spin, App, Tag,
} from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import * as systemApi from '../../api/system';
import type { RoleDTO, PermissionDTO } from '../../api/system';

const { Text } = Typography;

/**
 * 按模块分组的权限树结构
 */
interface PermissionGroup {
  parentCode: string;
  parentName: string;
  permissions: PermissionDTO[];
}

export default function RoleManagementPage() {
  const { message } = App.useApp();
  const { t } = useTranslation();
  const [roles, setRoles] = useState<RoleDTO[]>([]);
  const [permissions, setPermissions] = useState<PermissionDTO[]>([]);
  const [selectedRoleId, setSelectedRoleId] = useState<number | null>(null);
  const [selectedPermissionIds, setSelectedPermissionIds] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const loadData = async () => {
    setLoading(true);
    try {
      const [roleData, permData] = await Promise.all([
        systemApi.listRoles(),
        systemApi.listPermissions(),
      ]);
      setRoles(roleData);
      setPermissions(permData);
      if (roleData.length > 0 && !selectedRoleId) {
        handleSelectRole(roleData[0]);
      }
    } catch (e: any) {
      message.error(t('system.loadFailed') + ': ' + (e.response?.data?.message || e.message));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleSelectRole = (role: RoleDTO) => {
    setSelectedRoleId(role.id);
    const permIds = new Set<number>();
    role.permissionCodes.forEach((code) => {
      const perm = permissions.find((p) => p.permissionCode === code);
      if (perm) permIds.add(perm.id);
    });
    setSelectedPermissionIds(permIds);
  };

  const handlePermissionChange = (permId: number, checked: boolean) => {
    setSelectedPermissionIds((prev) => {
      const next = new Set(prev);
      if (checked) {
        next.add(permId);
      } else {
        next.delete(permId);
      }
      return next;
    });
  };

  const handleGroupCheckAll = (group: PermissionGroup, checked: boolean) => {
    setSelectedPermissionIds((prev) => {
      const next = new Set(prev);
      group.permissions.forEach((p) => {
        if (checked) {
          next.add(p.id);
        } else {
          next.delete(p.id);
        }
      });
      return next;
    });
  };

  const handleSave = async () => {
    if (!selectedRoleId) return;
    setSaving(true);
    try {
      const updated = await systemApi.updateRolePermissions(selectedRoleId, {
        permissionIds: Array.from(selectedPermissionIds),
      });
      setRoles((prev) => prev.map((r) => (r.id === selectedRoleId ? updated : r)));
      message.success(t('roles.saveSuccess'));
    } catch (e: any) {
      message.error(t('system.saveFailed') + ': ' + (e.response?.data?.message || e.message));
    } finally {
      setSaving(false);
    }
  };

  const menuPermissions = permissions.filter((p) => p.resourceType === 'MENU');
  const apiPermissions = permissions.filter((p) => p.resourceType === 'API');

  const permissionGroups: PermissionGroup[] = menuPermissions
    .map((menu) => {
      const children = apiPermissions
        .filter((p) => p.parentId === menu.id)
        .sort((a, b) => a.sortOrder - b.sortOrder);
      return {
        parentCode: menu.permissionCode,
        parentName: menu.permissionName,
        permissions: children,
      };
    })
    .filter((g) => g.permissions.length > 0);

  const orphanPermissions = apiPermissions.filter((p) => p.parentId === null);

  const selectedRole = roles.find((r) => r.id === selectedRoleId);

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 400 }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <>
      <Breadcrumb style={{ marginBottom: 16 }} items={[{ title: t('system.systemManagement') }, { title: t('roles.pageTitle') }]} />

      <div style={{ display: 'flex', gap: 16 }}>
        {/* 左侧：角色列表 */}
        <Card style={{ width: 280, flexShrink: 0 }}>
          <Text strong style={{ fontSize: 14, display: 'block', marginBottom: 12 }}>
            {t('roles.roleList')}
          </Text>
          <List
            dataSource={roles}
            renderItem={(role) => (
              <List.Item
                onClick={() => handleSelectRole(role)}
                style={{
                  cursor: 'pointer',
                  padding: '8px 12px',
                  borderRadius: 6,
                  background: selectedRoleId === role.id ? '#e6f4ff' : 'transparent',
                  border: selectedRoleId === role.id ? '1px solid #1677ff' : '1px solid transparent',
                }}
              >
                <div>
                  <div>{role.roleName}</div>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {role.roleCode}
                  </Text>
                  <div>
                    <Tag color="green" style={{ fontSize: 11 }}>
                      {t('roles.permissionCount', { count: role.permissionCodes.length })}
                    </Tag>
                  </div>
                </div>
              </List.Item>
            )}
          />
        </Card>

        {/* 右侧：权限矩阵 */}
        <Card style={{ flex: 1 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <div>
              <Text strong style={{ fontSize: 16 }}>
                {t('roles.permissionConfig')}
              </Text>
              {selectedRole && (
                <Text type="secondary" style={{ marginLeft: 12 }}>
                  {t('roles.currentRole', { name: selectedRole.roleName, code: selectedRole.roleCode })}
                </Text>
              )}
            </div>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              onClick={handleSave}
              loading={saving}
              disabled={!selectedRoleId}
            >
              {t('roles.saveConfig')}
            </Button>
          </div>

          {!selectedRoleId ? (
            <div style={{ textAlign: 'center', padding: 60, color: '#999' }}>
              {t('roles.selectRole')}
            </div>
          ) : (
            <div>
              {permissionGroups.map((group) => {
                const allChecked = group.permissions.every((p) => selectedPermissionIds.has(p.id));
                const someChecked = group.permissions.some((p) => selectedPermissionIds.has(p.id));

                return (
                  <Card
                    key={group.parentCode}
                    size="small"
                    style={{ marginBottom: 12 }}
                    title={
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <Checkbox
                          checked={allChecked}
                          indeterminate={someChecked && !allChecked}
                          onChange={(e) => handleGroupCheckAll(group, e.target.checked)}
                        />
                        <Text strong>{group.parentName}</Text>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          ({group.parentCode})
                        </Text>
                      </div>
                    }
                  >
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px 24px' }}>
                      {group.permissions.map((perm) => (
                        <Checkbox
                          key={perm.id}
                          checked={selectedPermissionIds.has(perm.id)}
                          onChange={(e) => handlePermissionChange(perm.id, e.target.checked)}
                        >
                          <span>{perm.permissionName}</span>
                          <Text type="secondary" style={{ fontSize: 11, marginLeft: 4 }}>
                            {perm.permissionCode}
                          </Text>
                        </Checkbox>
                      ))}
                    </div>
                  </Card>
                );
              })}

              {/* 未归属菜单的 API 权限 */}
              {orphanPermissions.length > 0 && (
                <Card
                  size="small"
                  style={{ marginBottom: 12 }}
                  title={
                    <Text strong>{t('roles.otherPermissions')}</Text>
                  }
                >
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '12px 24px' }}>
                    {orphanPermissions.map((perm) => (
                      <Checkbox
                        key={perm.id}
                        checked={selectedPermissionIds.has(perm.id)}
                        onChange={(e) => handlePermissionChange(perm.id, e.target.checked)}
                      >
                        <span>{perm.permissionName}</span>
                        <Text type="secondary" style={{ fontSize: 11, marginLeft: 4 }}>
                          {perm.permissionCode}
                        </Text>
                      </Checkbox>
                    ))}
                  </div>
                </Card>
              )}
            </div>
          )}
        </Card>
      </div>
    </>
  );
}
