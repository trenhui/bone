import oss from "ali-oss";
import { getOssConfig, IOssConfig } from "@/api/common";

let OSS_CONFIG: IOssConfig | null = null;
const OSS_KEY: string = "tpasaas/";
let ossClient: oss | null = null;

// 刷新 OSS 配置和客户端
const refreshOssConfig = async (): Promise<IOssConfig> => {
  try {
    const res = await getOssConfig();
    OSS_CONFIG = res as IOssConfig;

    // 创建新的 OSS 客户端实例，支持临时 token
    ossClient = new oss({
      region: OSS_CONFIG.endpoint.split(".")[0],
      accessKeyId: OSS_CONFIG.accessKeyId,
      accessKeySecret: OSS_CONFIG.accessKeySecret,
      bucket: OSS_CONFIG.bucketName,
      stsToken: OSS_CONFIG.securityToken, // 使用临时 token
      endpoint: OSS_CONFIG.endpoint,
    });

    return OSS_CONFIG;
  } catch (error) {
    console.error("获取 OSS 配置失败:", error);
    throw new Error("获取 OSS 配置失败");
  }
};

// 获取 OSS 客户端，如果没有配置则先获取
const getOssClient = async (): Promise<oss> => {
  if (!OSS_CONFIG || !ossClient) {
    await refreshOssConfig();
  }

  // 检查 token 是否过期
  if (OSS_CONFIG && OSS_CONFIG.expiration) {
    const expirationTime = new Date(OSS_CONFIG.expiration).getTime();
    const currentTime = new Date().getTime();

    // 如果 token 在 5 分钟内过期，则刷新配置
    if (expirationTime - currentTime < 5 * 60 * 1000) {
      console.log("OSS token 即将过期，正在刷新...");
      await refreshOssConfig();
    }
  }

  if (!ossClient) {
    throw new Error("OSS 客户端初始化失败");
  }

  return ossClient;
};

// 文件大小验证 (默认5MB)
const isValidFileSize = (file: File, maxSizeMB: number = 5): boolean => {
  return file.size <= maxSizeMB * 1024 * 1024;
};

// 生成基于时间戳目录的文件路径（保持原始文件名）
const generateFilePathWithTimestamp = (originalName: string): string => {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  const hour = String(now.getHours()).padStart(2, "0");
  const minute = String(now.getMinutes()).padStart(2, "0");
  const second = String(now.getSeconds()).padStart(2, "0");
  const randomStr = Math.random().toString(36).substring(2, 8);

  // 清理文件名中的特殊字符，保留中文、英文、数字、下划线、连字符、点号
  const cleanName = originalName.replace(/[^\u4e00-\u9fa5\w\-\.]/g, "_");

  // 生成时间戳目录路径：年月日/时分秒_随机字符/原始文件名
  return `${year}${month}${day}/${hour}${minute}${second}_${randomStr}/${cleanName}`;
};

const uploadFile = async (
  file: File,
  fileName: string,
  maxSizeMB?: number
): Promise<any> => {
  try {
    // 文件大小验证
    if (!isValidFileSize(file, maxSizeMB)) {
      return {
        success: false,
        error: `文件大小超过限制（${maxSizeMB || 5}MB）`,
      };
    }

    // 获取 OSS 客户端
    const client = await getOssClient();
    // 在文件名前添加配置的KEY前缀
    const fullFileName = `${OSS_KEY}${fileName}`;
    // 使用文件名作为 OSS 对象名
    const result = await client.put(fullFileName, file);

    // 更精确的URL解码：只解码文件名部分，保持URL结构正确
    const createDisplayUrl = (originalUrl: string): string => {
      try {
        // 找到最后一个斜杠的位置，分离出文件名部分
        const lastSlashIndex = originalUrl.lastIndexOf("/");
        if (lastSlashIndex === -1) return decodeURIComponent(originalUrl);

        const baseUrl = originalUrl.substring(0, lastSlashIndex + 1);
        const encodedFileName = originalUrl.substring(lastSlashIndex + 1);
        const decodedFileName = decodeURIComponent(encodedFileName);

        return baseUrl + decodedFileName;
      } catch (error) {
        // 如果解码失败，返回原始URL
        return originalUrl;
      }
    };

    const displayUrl = createDisplayUrl(result.url);

    // 返回完整的访问URL
    return {
      success: true,
      url: result.url, // 原始编码的URL（用于实际访问）
      name: result.name,
      fullUrl: displayUrl, // 精确解码后的显示URL
    };
  } catch (error) {
    console.error("OSS上传失败:", error);
    return {
      success: false,
      error: error instanceof Error ? error.message : "上传失败",
    };
  }
};

// 批量上传文件
const uploadMultipleFiles = async (
  files: File[],
  maxSizeMB?: number
): Promise<any[]> => {
  const uploadPromises = files.map((file) => {
    const filePath = generateFilePathWithTimestamp(file.name);
    return uploadFile(file, filePath, maxSizeMB);
  });
  return await Promise.all(uploadPromises);
};

export {
  uploadFile,
  uploadMultipleFiles,
  OSS_CONFIG,
  isValidFileSize,
  generateFilePathWithTimestamp,
};
