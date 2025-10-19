#!/bin/bash

# 设置工作目录
cd $(dirname $0)

# 创建目标目录
mkdir -p target/classes

# 只包含必要的依赖
LOMBOK_JAR=$(find ~/.m2/repository -name "lombok-*.jar" | head -1)
SPRING_BOOT_JAR=$(find ~/.m2/repository -name "spring-boot-starter-*.jar" | head -1)

# 设置简化的类路径
CLASSPATH="./target/classes"
if [ -f "$LOMBOK_JAR" ]; then
    CLASSPATH="$CLASSPATH:$LOMBOK_JAR"
fi
if [ -f "$SPRING_BOOT_JAR" ]; then
    CLASSPATH="$CLASSPATH:$SPRING_BOOT_JAR"
fi

# 手动复制依赖到lib目录（如果需要）
mkdir -p lib
cp -f "$LOMBOK_JAR" lib/ 2>/dev/null
cp -f "$SPRING_BOOT_JAR" lib/ 2>/dev/null

# 编译一个简单的类进行测试
echo "正在编译WorkflowMetadata.java..."
echo "使用类路径: $CLASSPATH"
javac -d target/classes -cp "$CLASSPATH" bone-smartmeta/bone-smartmeta-engine/src/main/java/com/bone/smartmeta/engine/metadata/WorkflowMetadata.java

if [ $? -eq 0 ]; then
    echo "编译成功！"
else
    echo "编译失败！"
fi