import request from "@/utils/request";

export const getOssConfig = () => {
  return request<any, IOssConfig>({
    url: "/data-api/tpa/oss/config",
    method: "get",
  });
};

export interface IOssConfig {
  accessKeyId: string;
  accessKeySecret: string;
  bucketName: string;
  endpoint: string;
  expiration: string;
  imageEndpoint: string;
  securityToken: string;
}
