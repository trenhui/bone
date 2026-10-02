package com.bone.studio.generator.application.dto;

/**
 * 生成产物文件视图（只读）。
 *
 * <p><b>为何需要</b>：此前任务完成后的产物只有「下载 zip」一条路，用户必须下载解压才能确认生成了什么； 真实场景里用户先要看清单、抽查关键文件（entity /
 * controller）再决定是否并入工程，在线查看是刚需。
 *
 * @param filePath 相对路径（zip 内条目名）
 * @param fileName 文件名
 * @param size 字节数（UTF-8 编码长度）
 * @param content 文件正文；仅 {@code content=true} 时返回，清单模式为 {@code null}
 */
public record GeneratedFileView(String filePath, String fileName, long size, String content) {}
