import React, { useCallback, useEffect, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Form,
  Input,
  Skeleton,
} from 'antd';
import { LockOutlined, SaveOutlined } from '@ant-design/icons';
import * as api from '../services/api';
import { isForbiddenError, resolveIamErrorMessage } from '../utils/iamErrorMessages';
import { ListErrorState, ListForbiddenState } from '../components/ListStates';
import ModulePage from '../components/ModulePage';
import { formatDate } from '@bone/shared-utils';

/**
 * 个人信息 / 修改密码（详设 §2.11 能力补齐矩阵 S-7）。
 *
 * <p>后端早在 `MeController`（`GET|PUT /api/v1/iam/me`、`POST /api/v1/iam/me/change-password`）
 * 就已就绪，此前前端无页面，用户改密只能走管理员重置。本页只补前端缺口，不新增后端接口。
 *
 * <p>四态规范（§2.11 第 3 条）：加载中 `Skeleton`、加载失败 / 无权限 `Result`、成功才渲染表单。
 */
const ProfilePage: React.FC = () => {
  const { message: messageApi } = AntdApp.useApp();
  const [profileForm] = Form.useForm();
  const [passwordForm] = Form.useForm();

  const [profile, setProfile] = useState<api.MyProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadFailed, setLoadFailed] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [savingProfile, setSavingProfile] = useState(false);
  const [savingPassword, setSavingPassword] = useState(false);

  const fetchProfile = useCallback(async () => {
    setLoading(true);
    setLoadFailed(null);
    setForbidden(false);
    try {
      const response = await api.getMyProfile();
      if (response.code === 200 && response.data) {
        setProfile(response.data);
        profileForm.setFieldsValue({
          realName: response.data.realName,
          phone: response.data.phone,
          avatarUrl: response.data.avatarUrl,
        });
      } else {
        setLoadFailed(response.message || '获取个人信息失败');
      }
    } catch (err: unknown) {
      setForbidden(isForbiddenError(err));
      setLoadFailed(resolveIamErrorMessage(err) ?? '获取个人信息失败');
    } finally {
      setLoading(false);
    }
  }, [profileForm]);

  useEffect(() => {
    void fetchProfile();
  }, [fetchProfile]);

  const handleSaveProfile = async () => {
    try {
      const values = await profileForm.validateFields();
      setSavingProfile(true);
      const response = await api.updateMyProfile(values);
      if (response.code === 200) {
        messageApi.success('个人信息已保存');
        await fetchProfile();
      } else {
        messageApi.error(resolveIamErrorMessage(response) ?? '保存失败');
      }
    } catch (err: unknown) {
      messageApi.error(resolveIamErrorMessage(err) ?? '保存失败');
    } finally {
      setSavingProfile(false);
    }
  };

  const handleChangePassword = async () => {
    try {
      const values = await passwordForm.validateFields();
      if (values.newPassword !== values.confirmPassword) {
        messageApi.error('两次输入的新密码不一致');
        return;
      }
      setSavingPassword(true);
      const response = await api.changeMyPassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      if (response.code === 200) {
        messageApi.success('密码已修改，请牢记新密码');
        passwordForm.resetFields();
      } else {
        messageApi.error(resolveIamErrorMessage(response) ?? '修改密码失败');
      }
    } catch (err: unknown) {
      messageApi.error(resolveIamErrorMessage(err) ?? '修改密码失败');
    } finally {
      setSavingPassword(false);
    }
  };

  if (loading) {
    return (
      <ModulePage title="个人信息" card>
        <Skeleton active paragraph={{ rows: 6 }} />
      </ModulePage>
    );
  }

  if (loadFailed || !profile) {
    return (
      <ModulePage title="个人信息" card>
        {forbidden ? (
          <ListForbiddenState onRetry={fetchProfile} />
        ) : (
          <ListErrorState error={loadFailed ?? '接口未返回数据'} onRetry={fetchProfile} />
        )}
      </ModulePage>
    );
  }

  return (
    <ModulePage
      title="个人信息"
      description="查看当前登录账号的基本信息，并可自助修改密码。"
      card={false}
    >
      <Card title="基本信息" style={{ marginBottom: 16 }}>
        <Descriptions column={2} size="small">
          <Descriptions.Item label="用户名">{profile.username}</Descriptions.Item>
          <Descriptions.Item label="姓名">{profile.realName || '-'}</Descriptions.Item>
          <Descriptions.Item label="邮箱">{profile.email}</Descriptions.Item>
          <Descriptions.Item label="手机号">{profile.phone || '-'}</Descriptions.Item>
          <Descriptions.Item label="所属租户">{profile.tenantId}</Descriptions.Item>
          <Descriptions.Item label="管理员">{profile.isAdmin ? '是' : '否'}</Descriptions.Item>
          <Descriptions.Item label="最近登录">{formatDate(profile.lastLoginAt)}</Descriptions.Item>
          <Descriptions.Item label="密码更新时间">
            {formatDate(profile.passwordUpdatedAt)}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="编辑资料" style={{ marginBottom: 16 }}>
        <Form form={profileForm} layout="vertical" style={{ maxWidth: 520 }}>
          <Form.Item label="姓名" name="realName">
            <Input placeholder="请输入姓名" allowClear />
          </Form.Item>
          <Form.Item label="手机号" name="phone">
            <Input placeholder="请输入手机号" allowClear />
          </Form.Item>
          <Form.Item label="头像地址" name="avatarUrl">
            <Input placeholder="https://..." allowClear />
          </Form.Item>
          <Form.Item>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              loading={savingProfile}
              onClick={() => void handleSaveProfile()}
            >
              保存资料
            </Button>
          </Form.Item>
        </Form>
      </Card>

      <Card title="修改密码">
        <Form form={passwordForm} layout="vertical" style={{ maxWidth: 520 }}>
          <Form.Item
            label="当前密码"
            name="oldPassword"
            rules={[{ required: true, message: '请输入当前密码' }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            label="新密码"
            name="newPassword"
            rules={[{ required: true, message: '请输入新密码' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="确认新密码"
            name="confirmPassword"
            rules={[{ required: true, message: '请再次输入新密码' }]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item>
            <Button
              icon={<LockOutlined />}
              loading={savingPassword}
              onClick={() => void handleChangePassword()}
            >
              修改密码
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </ModulePage>
  );
};

export default ProfilePage;
