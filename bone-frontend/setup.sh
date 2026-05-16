#!/bin/bash
# BONE 前端 Monorepo 依赖安装（npm workspaces）

set -e
cd "$(dirname "$0")"

echo "安装 bone-frontend workspaces（apps + packages + @bone/ui）..."
npm install

echo ""
echo "完成。推荐启动方式："
echo "  1. 根目录: npm run dev                    # 仅 Shell (3000)"
echo "  2. 批量:   bash restart-all-apps.sh       # Shell + 全部微应用"
echo ""
echo "微应用端口: IAM 3003 | 元数据 3004 | 主数据 3005 | 集成 3006 | 系统 3007 | 扩展 3008 | 生成器 3009"
