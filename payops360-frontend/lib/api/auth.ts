import { apiClient } from "./client";
import {
  LoginRequest,
  RegisterRequest,
  AuthResponse,
  User,
  ApiResponse,
} from "@/types";

export const authApi = {
  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>("/auth/login", data);
    // Backend returns { success: true, data: { accessToken, refreshToken, user } }
    const authData = response.data;
    // Store tokens
    apiClient.storeTokens(authData.accessToken, authData.refreshToken);
    return authData;
  },

  register: async (data: RegisterRequest): Promise<User> => {
    const response = await apiClient.post<User>("/auth/register", data);
    return response.data;
  },

  logout: async (): Promise<void> => {
    try {
      await apiClient.post("/auth/logout");
    } finally {
      apiClient.logout();
    }
  },

  getCurrentUser: async (): Promise<User> => {
    const response = await apiClient.get<User>("/auth/me");
    return response.data;
  },

  changePassword: async (currentPassword: string, newPassword: string): Promise<void> => {
    await apiClient.post("/auth/change-password", {
      currentPassword,
      newPassword,
    });
  },
};

