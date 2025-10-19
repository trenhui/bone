#!/bin/bash

# 设置类路径
CLASSPATH=""
# 添加Maven依赖到类路径
for jar in $(find /Users/renhui.trh/.m2/repository -name "*.jar"); do
  CLASSPATH="$CLASSPATH:$jar"
done
# 添加target目录到类路径
CLASSPATH="$CLASSPATH:/Users/renhui.trh/code/bone/bone-engine/bone-metadata-sdk/target/classes"
CLASSPATH="$CLASSPATH:/Users/renhui.trh/code/bone/bone-engine/bone-metadata-sdk/target/test-classes"

# 确保target目录存在
mkdir -p /Users/renhui.trh/code/bone/bone-engine/bone-metadata-sdk/target/test-classes/com/bone/metadata/sdk/test/testcase
mkdir -p /Users/renhui.trh/code/bone/bone-engine/bone-metadata-sdk/target/test-classes/com/bone/metadata/sdk/test/domain

# 编译必要的类
echo "Compiling Permission.java..."
javac -d /Users/renhui.trh/code/bone/bone-engine/bone-metadata-sdk/target/test-classes \
  -cp "$CLASSPATH" \
  /Users/renhui.trh/code/bone/bone-engine/bone-metadata-sdk/src/test/java/com/bone/metadata/sdk/test/domain/Permission.java

echo "Compiling DataPermission.java..."
javac -d /Users/renhui.trh/code/bone/bone-engine/bone-metadata-sdk/target/test-classes \
  -cp "$CLASSPATH" \
  /Users/renhui.trh/code/bone/bone-engine/bone-metadata-sdk/src/test/java/com/bone/metadata/sdk/test/domain/DataPermission.java

echo "Compiling SimplePermissionRepositoryTest.java..."
javac -d /Users/renhui.trh/code/bone/bone-engine/bone-metadata-sdk/target/test-classes \
  -cp "$CLASSPATH" \
  /Users/renhui.trh/code/bone/bone-engine/bone-metadata-sdk/src/test/java/com/bone/metadata/sdk/test/testcase/SimplePermissionRepositoryTest.java

# 运行测试
echo "Running test..."
java -cp "$CLASSPATH" org.junit.platform.console.ConsoleLauncher \
  --select-class=com.bone.metadata.sdk.test.testcase.SimplePermissionRepositoryTest