#!/bin/bash

# BONE 前端项目设置脚本
echo "开始设置 BONE 前端项目..."

# 检查是否在正确的目录
if [ ! -f "package.json" ]; then
    echo "错误: 请在 bone-frontend 根目录下运行此脚本"
    exit 1
fi

# 1. 安装根目录依赖
echo "安装根目录依赖..."
npm install

# 2. 安装主应用依赖
echo "安装主应用 bone-shell 依赖..."
cd apps/bone-shell && npm install && cd ../..

# 3. 安装所有微应用依赖
echo "安装微应用依赖..."
for app in bone-iam-app bone-metadata-app bone-masterdata-app bone-integration-app bone-system-app bone-extension-app; do
    if [ -d "apps/$app" ]; then
        echo "安装 $app 依赖..."
        cd "apps/$app" && npm install && cd ../..
    fi
done

# 4. 安装共享包依赖
echo "安装共享包依赖..."
for pkg in shared-components shared-utils shared-services shared-types; do
    if [ -d "packages/$pkg" ]; then
        echo "安装 $pkg 依赖..."
        cd "packages/$pkg" && npm install && cd ../..
    fi
done

echo "依赖安装完成！"
echo ""
echo "要运行应用，请依次打开多个终端窗口："
echo ""
echo "终端 1 - 主应用 (端口 3000):"
echo "  cd /Users/renhui.trh/wps/bone/bone-frontend/apps/bone-shell && npm run dev"
echo ""
echo "终端 2 - IAM 应用 (端口 3003):"
echo "  cd /Users/renhui.trh/wps/bone/bone-frontend/apps/bone-iam-app && npm run dev"
echo ""
echo "终端 3 - 元数据应用 (端口 3004):"
echo "  cd /Users/renhui.trh/wps/bone/bone-frontend/apps/bone-metadata-app && npm run dev"
echo ""
echo "终端 4 - 主数据应用 (端口 3005):"
echo "  cd /Users/renhui.trh/wps/bone/bone-frontend/apps/bone-masterdata-app && npm run dev"
echo ""
echo "终端 5 - 集成应用 (端口 3006):"
echo "  cd /Users/renhui.trh/wps/bone/bone-frontend/apps/bone-integration-app && npm run dev"
echo ""
echo "终端 6 - 系统应用 (端口 3007):"
echo "  cd /Users/renhui.trh/wps/bone/bone-frontend/apps/bone-system-app && npm run dev"
echo ""
echo "终端 7 - 扩展应用 (端口 3008):"
echo "  cd /Users/renhui.trh/wps/bone/bone-frontend/apps/bone-extension-app && npm run dev"
echo ""
echo "所有应用启动后，访问 http://localhost:3000 即可使用"
