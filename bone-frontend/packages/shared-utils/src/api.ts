export interface ApiClient {
  get<T>(url: string): Promise<T>;
  post<T>(url: string, body?: unknown): Promise<T>;
}

export function createApiClient(): ApiClient {
  return {
    get: async () => undefined as never,
    post: async () => undefined as never,
  };
}
