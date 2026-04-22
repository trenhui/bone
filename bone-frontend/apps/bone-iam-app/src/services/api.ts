import axios, { AxiosInstance } from 'axios';
import {
  User,
  CreateUserRequest,
  UpdateUserRequest,
  ChangePasswordRequest,
  Role,
  CreateRoleRequest,
  UpdateRoleRequest,
  Permission,
  CreatePermissionRequest,
  UpdatePermissionRequest,
  AuditLog,
  LoginRequest,
  LoginResponse,
  PageResult,
  ApiResponse
} from '../types';

class ApiService {
  private http: AxiosInstance;

  constructor() {
    this.http = axios.create({
      baseURL: '/api',
      timeout: 10000,
      headers: {
        'Content-Type': 'application/json'
      }
    });

    // 请求拦截器，添加token
    this.http.interceptors.request.use(
      (config) => {
        const token = localStorage.getItem('token');
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
      },
      (error) => {
        return Promise.reject(error);
      }
    );

    // 响应拦截器，处理错误
    this.http.interceptors.response.use(
      (response) => {
        return response.data;
      },
      (error) => {
        // 处理401错误，跳转到登录页
        if (error.response && error.response.status === 401) {
          localStorage.removeItem('token');
          window.location.href = '/login';
        }
        return Promise.reject(error);
      }
    );
  }

  // 登录相关
  async login(data: LoginRequest): Promise<ApiResponse<LoginResponse>> {
    return this.http.post('/auth/login', data);
  }

  async logout(): Promise<ApiResponse<void>> {
    return this.http.post('/auth/logout');
  }

  // 用户相关
  async getUsers(page: number, pageSize: number, keyword?: string): Promise<ApiResponse<PageResult<User>>> {
    return this.http.get('/users', {
      params: { page, pageSize, keyword }
    });
  }

  async getUserById(id: string): Promise<ApiResponse<User>> {
    return this.http.get(`/users/${id}`);
  }

  async createUser(data: CreateUserRequest): Promise<ApiResponse<User>> {
    return this.http.post('/users', data);
  }

  async updateUser(id: string, data: UpdateUserRequest): Promise<ApiResponse<User>> {
    return this.http.put(`/users/${id}`, data);
  }

  async deleteUser(id: string): Promise<ApiResponse<void>> {
    return this.http.delete(`/users/${id}`);
  }

  async changePassword(data: ChangePasswordRequest): Promise<ApiResponse<void>> {
    return this.http.post('/users/change-password', data);
  }

  // 角色相关
  async getRoles(page: number, pageSize: number, keyword?: string): Promise<ApiResponse<PageResult<Role>>> {
    return this.http.get('/roles', {
      params: { page, pageSize, keyword }
    });
  }

  async getRoleById(id: string): Promise<ApiResponse<Role>> {
    return this.http.get(`/roles/${id}`);
  }

  async createRole(data: CreateRoleRequest): Promise<ApiResponse<Role>> {
    return this.http.post('/roles', data);
  }

  async updateRole(id: string, data: UpdateRoleRequest): Promise<ApiResponse<Role>> {
    return this.http.put(`/roles/${id}`, data);
  }

  async deleteRole(id: string): Promise<ApiResponse<void>> {
    return this.http.delete(`/roles/${id}`);
  }

  // 权限相关
  async getPermissions(page: number, pageSize: number, keyword?: string): Promise<ApiResponse<PageResult<Permission>>> {
    return this.http.get('/permissions', {
      params: { page, pageSize, keyword }
    });
  }

  async getPermissionById(id: string): Promise<ApiResponse<Permission>> {
    return this.http.get(`/permissions/${id}`);
  }

  async createPermission(data: CreatePermissionRequest): Promise<ApiResponse<Permission>> {
    return this.http.post('/permissions', data);
  }

  async updatePermission(id: string, data: UpdatePermissionRequest): Promise<ApiResponse<Permission>> {
    return this.http.put(`/permissions/${id}`, data);
  }

  async deletePermission(id: string): Promise<ApiResponse<void>> {
    return this.http.delete(`/permissions/${id}`);
  }

  // 审计日志相关
  async getAuditLogs(page: number, pageSize: number, keyword?: string, startDate?: string, endDate?: string): Promise<ApiResponse<PageResult<AuditLog>>> {
    return this.http.get('/audit-logs', {
      params: { page, pageSize, keyword, startDate, endDate }
    });
  }
}

export const apiService = new ApiService();