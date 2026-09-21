#!/bin/bash
# Bone 后端服务端点验证脚本 v4
# 基于实际 Controller 源码分析的正确 API 路径和请求体

RESULTS_DIR="/tmp/bone-endpoint-test"
rm -rf "$RESULTS_DIR"
mkdir -p "$RESULTS_DIR"

echo "========================================"
echo "  Bone 后端服务端点验证脚本 v4"
echo "========================================"
echo ""

##############################################
# Step 1: 登录获取 Token
##############################################
echo "[Step 1] 登录获取 IAM Token..."

IAM_TOKEN=""
LOGIN_BODY=$(curl -s -X POST http://localhost:8081/api/v1/iam/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}' 2>/dev/null || echo '{}')

IAM_TOKEN=$(echo "$LOGIN_BODY" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data', {})
    if isinstance(data, dict):
        print(data.get('token', data.get('accessToken', '')))
    else:
        print('')
except:
    print('')
" 2>/dev/null)

if [ -z "$IAM_TOKEN" ]; then
  echo "  直接登录失败（调试重置接口已移除，请确认 BONE_IAM_DEFAULT_PASSWORD / 种子数据）"
  IAM_TOKEN=""
fi

if [ -z "$IAM_TOKEN" ]; then
  echo "  [!] 无法获取 IAM Token（IAM 服务数据库连接异常），将尝试不带 Token 测试..."
else
  echo "  [OK] IAM Token 获取成功: ${IAM_TOKEN:0:30}..."
fi

# Step 1b: 获取 metadata-server Token
echo "[Step 1b] 登录获取 metadata-server Token..."
META_TOKEN=""
META_LOGIN_BODY=$(curl -s -X POST http://localhost:9001/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}' 2>/dev/null || echo '{}')
META_TOKEN=$(echo "$META_LOGIN_BODY" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(d.get('access_token', ''))
except:
    print('')
" 2>/dev/null)

if [ -z "$META_TOKEN" ]; then
  echo "  [!] metadata-server Token 获取失败，将使用 IAM Token 尝试..."
  META_TOKEN="$IAM_TOKEN"
else
  echo "  [OK] metadata-server Token 获取成功: ${META_TOKEN:0:30}..."
fi

echo ""

##############################################
# Step 2: 定义测试函数
##############################################

test_endpoint() {
  local module="$1"
  local method="$2"
  local url="$3"
  local body="${4:-}"
  local token_to_use="${5:-$IAM_TOKEN}"
  
  local resp
  if [ -n "$body" ]; then
    if [ -n "$token_to_use" ]; then
      resp=$(curl -s -w "\n%{http_code}" -X "$method" "$url" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token_to_use" \
        -d "$body" 2>/dev/null) || resp=$'\n000'
    else
      resp=$(curl -s -w "\n%{http_code}" -X "$method" "$url" \
        -H "Content-Type: application/json" \
        -d "$body" 2>/dev/null) || resp=$'\n000'
    fi
  else
    if [ -n "$token_to_use" ]; then
      resp=$(curl -s -w "\n%{http_code}" -X "$method" "$url" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token_to_use" 2>/dev/null) || resp=$'\n000'
    else
      resp=$(curl -s -w "\n%{http_code}" -X "$method" "$url" \
        -H "Content-Type: application/json" 2>/dev/null) || resp=$'\n000'
    fi
  fi
  
  local http_code
  http_code=$(echo "$resp" | tail -1)
  local resp_body
  resp_body=$(echo "$resp" | sed '$d')
  
  local category="unknown"
  if echo "$http_code" | grep -q '^2'; then
    category="2xx"
  elif echo "$http_code" | grep -q '^4'; then
    category="4xx"
  elif echo "$http_code" | grep -q '^5'; then
    category="5xx"
  elif [ "$http_code" = "000" ]; then
    category="5xx"
    http_code="CONN_FAIL"
  fi
  
  echo "${method}|${url}|${http_code}|${category}" >> "$RESULTS_DIR/${module}.txt"
  
  if [ "$category" = "5xx" ]; then
    local truncated_body
    truncated_body=$(echo "$resp_body" | head -c 200)
    echo "${method} ${url}|${http_code}|${truncated_body}" >> "$RESULTS_DIR/${module}_5xx.txt"
  fi
  
  # 记录 4xx 的详细信息（用于分析）
  if [ "$category" = "4xx" ]; then
    local truncated_body
    truncated_body=$(echo "$resp_body" | head -c 200)
    echo "${method} ${url}|${http_code}|${truncated_body}" >> "$RESULTS_DIR/${module}_4xx.txt"
  fi
  
  local status_icon
  case "$category" in
    2xx) status_icon="[OK]" ;;
    4xx) status_icon="[WARN]" ;;
    5xx) status_icon="[FAIL]" ;;
    *) status_icon="[?]" ;;
  esac
  
  echo "  $status_icon $method $url -> $http_code"
}

get_id_from_list() {
  local url="$1"
  local token_to_use="${2:-$IAM_TOKEN}"
  local resp
  if [ -n "$token_to_use" ]; then
    resp=$(curl -s -H "Authorization: Bearer $token_to_use" -H "Content-Type: application/json" "$url" 2>/dev/null || echo '{}')
  else
    resp=$(curl -s -H "Content-Type: application/json" "$url" 2>/dev/null || echo '{}')
  fi
  echo "$resp" | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    data = d.get('data', {})
    if isinstance(data, dict):
        records = data.get('records', data.get('list', data.get('content', [])))
        if records and len(records) > 0:
            print(records[0].get('id', ''))
        elif data.get('id'):
            print(data.get('id', ''))
    elif isinstance(data, list) and len(data) > 0:
        print(data[0].get('id', ''))
except:
    print('')
" 2>/dev/null
}

##############################################
# Step 3: 逐模块测试
##############################################

echo "========================================"
echo "  开始端点验证"
echo "========================================"
echo ""

# ---------- bone-iam (8081) ----------
echo "[bone-iam:8081]"
MODULE="bone-iam"
IAM_BASE="http://localhost:8081/api/v1/iam"

# 登录（实际路径 /login）
test_endpoint "$MODULE" "POST" "${IAM_BASE}/login" \
  '{"username":"admin","password":"123456"}'

# 刷新 Token
test_endpoint "$MODULE" "POST" "${IAM_BASE}/refresh" \
  "{\"token\":\"${IAM_TOKEN}\"}"

# 账号 CRUD
test_endpoint "$MODULE" "POST" "${IAM_BASE}/accounts" \
  '{"username":"testuser_ep","password":"Test123456","email":"test_ep@bone.com","phone":"13800000001","realName":"测试用户"}'
test_endpoint "$MODULE" "GET" "${IAM_BASE}/accounts"

ACCT_ID=$(get_id_from_list "${IAM_BASE}/accounts")
[ -z "$ACCT_ID" ] && ACCT_ID="1"
echo "  使用账号ID: $ACCT_ID"

test_endpoint "$MODULE" "GET" "${IAM_BASE}/accounts/${ACCT_ID}"
test_endpoint "$MODULE" "PUT" "${IAM_BASE}/accounts/${ACCT_ID}" \
  '{"realName":"测试用户-更新","email":"test_ep_updated@bone.com"}'
test_endpoint "$MODULE" "DELETE" "${IAM_BASE}/accounts/${ACCT_ID}"

# 角色 CRUD
test_endpoint "$MODULE" "POST" "${IAM_BASE}/roles" \
  '{"name":"test_role_ep","code":"TEST_ROLE_EP","description":"测试角色"}'
test_endpoint "$MODULE" "GET" "${IAM_BASE}/roles"

ROLE_ID=$(get_id_from_list "${IAM_BASE}/roles")
[ -z "$ROLE_ID" ] && ROLE_ID="1"
echo "  使用角色ID: $ROLE_ID"

test_endpoint "$MODULE" "GET" "${IAM_BASE}/roles/${ROLE_ID}"
test_endpoint "$MODULE" "PUT" "${IAM_BASE}/roles/${ROLE_ID}" \
  '{"name":"test_role_ep_updated","description":"测试角色-更新"}'
test_endpoint "$MODULE" "DELETE" "${IAM_BASE}/roles/${ROLE_ID}"

# 权限
test_endpoint "$MODULE" "POST" "${IAM_BASE}/permissions" \
  '{"name":"test_perm_ep","code":"TEST_PERM_EP","resource":"test","action":"read"}'
test_endpoint "$MODULE" "GET" "${IAM_BASE}/permissions"

# 租户
test_endpoint "$MODULE" "GET" "${IAM_BASE}/tenants"
test_endpoint "$MODULE" "POST" "${IAM_BASE}/tenants" \
  '{"name":"测试租户EP","code":"TEST_TENANT_EP","description":"端点测试租户"}'

TENANT_ID=$(get_id_from_list "${IAM_BASE}/tenants")
[ -z "$TENANT_ID" ] && TENANT_ID="1"
echo "  使用租户ID: $TENANT_ID"

test_endpoint "$MODULE" "PUT" "${IAM_BASE}/tenants/${TENANT_ID}" \
  '{"name":"测试租户EP-更新","description":"端点测试租户-更新"}'

echo ""

# ---------- bone-system (8083) ----------
echo "[bone-system:8083]"
MODULE="bone-system"
SYS_BASE="http://localhost:8083/api/v1/system"

# 实际路径：/config（非 /configs），/alert（非 /alerts），/health（非 /monitor）
test_endpoint "$MODULE" "GET" "${SYS_BASE}/config/page"
test_endpoint "$MODULE" "POST" "${SYS_BASE}/config" \
  '{"key":"test.ep.key","value":"test_value","description":"端点测试配置"}'
test_endpoint "$MODULE" "GET" "${SYS_BASE}/logs/page"
test_endpoint "$MODULE" "GET" "${SYS_BASE}/health"
test_endpoint "$MODULE" "GET" "${SYS_BASE}/alert/events/page"
test_endpoint "$MODULE" "GET" "${SYS_BASE}/alert/rules/page"

echo ""

# ---------- bone-masterdata (8084) ----------
echo "[bone-masterdata:8084]"
MODULE="bone-masterdata"
MD_BASE="http://localhost:8084/api/v1/masterdata"

# SecurityConfig: /api/** permitAll，无需 Token
test_endpoint "$MODULE" "GET" "${MD_BASE}/entities"
test_endpoint "$MODULE" "POST" "${MD_BASE}/entities" \
  '{"name":"test_entity_ep","code":"TEST_ENTITY_EP","description":"端点测试实体"}'

ENTITY_ID=$(get_id_from_list "${MD_BASE}/entities")
[ -z "$ENTITY_ID" ] && ENTITY_ID="1"
echo "  使用实体ID: $ENTITY_ID"

test_endpoint "$MODULE" "GET" "${MD_BASE}/entities/${ENTITY_ID}/fields"
test_endpoint "$MODULE" "POST" "${MD_BASE}/entities/${ENTITY_ID}/fields" \
  '{"name":"test_field","code":"TEST_FIELD","type":"STRING","required":false}'
# 实际路径是 /records（非 /entities/{id}/records）
test_endpoint "$MODULE" "GET" "${MD_BASE}/records"
test_endpoint "$MODULE" "POST" "${MD_BASE}/records" \
  '{"entityId":"1","data":{"test_field":"test_value"}}'
test_endpoint "$MODULE" "GET" "${MD_BASE}/quality/checks"
test_endpoint "$MODULE" "POST" "${MD_BASE}/quality/checks" \
  '{"name":"test_quality_check","entityId":"1","ruleType":"NOT_NULL","fieldName":"name"}'
test_endpoint "$MODULE" "GET" "${MD_BASE}/quality/reports"

echo ""

# ---------- bone-integration (8085, context-path: /api) ----------
echo "[bone-integration:8085]"
MODULE="bone-integration"
INT_BASE="http://localhost:8085/api/v1/integration"

# jwt-enabled=false 时无需 Token
# GET 需要分页参数 pageNum, pageSize
test_endpoint "$MODULE" "GET" "${INT_BASE}/connectors?pageNum=1&pageSize=10"
test_endpoint "$MODULE" "POST" "${INT_BASE}/connectors" \
  '{"name":"test_connector_ep","type":"REST","config":"{\"url\":\"http://example.com\"}","description":"端点测试连接器"}'

CONN_ID=$(get_id_from_list "${INT_BASE}/connectors?pageNum=1&pageSize=10")
[ -z "$CONN_ID" ] && CONN_ID="1"
echo "  使用连接器ID: $CONN_ID"

test_endpoint "$MODULE" "GET" "${INT_BASE}/connectors/${CONN_ID}"

test_endpoint "$MODULE" "GET" "${INT_BASE}/flows?pageNum=1&pageSize=10"
test_endpoint "$MODULE" "POST" "${INT_BASE}/flows" \
  '{"name":"test_flow_ep","description":"端点测试流程"}'

FLOW_ID=$(get_id_from_list "${INT_BASE}/flows?pageNum=1&pageSize=10")
[ -z "$FLOW_ID" ] && FLOW_ID="1"
echo "  使用流程ID: $FLOW_ID"

test_endpoint "$MODULE" "GET" "${INT_BASE}/flows/${FLOW_ID}"

echo ""

# ---------- bone-metadata-server (9001) ----------
echo "[bone-metadata-server:9001]"
MODULE="bone-metadata-server"
META_BASE="http://localhost:9001/api/v1/metadata"

# 实际路径：/entities（非 /models），/entities/{id}/fields（非 /fields）
# 需要 displayName, tableName, name, code
test_endpoint "$MODULE" "GET" "${META_BASE}/entities" "" "$META_TOKEN"
test_endpoint "$MODULE" "POST" "${META_BASE}/entities" \
  '{"name":"test_model_ep","code":"TEST_MODEL_EP","displayName":"测试模型EP","tableName":"test_model_ep","description":"端点测试模型"}' "$META_TOKEN"

META_ENTITY_ID=$(get_id_from_list "${META_BASE}/entities" "$META_TOKEN")
[ -z "$META_ENTITY_ID" ] && META_ENTITY_ID="1"
echo "  使用元数据实体ID: $META_ENTITY_ID"

# 需要 entityId, name, code, displayName, type
test_endpoint "$MODULE" "GET" "${META_BASE}/entities/${META_ENTITY_ID}/fields" "" "$META_TOKEN"
test_endpoint "$MODULE" "POST" "${META_BASE}/entities/${META_ENTITY_ID}/fields" \
  "{\"entityId\":${META_ENTITY_ID},\"name\":\"test_field_ep\",\"code\":\"TEST_FIELD_EP\",\"displayName\":\"测试字段EP\",\"type\":\"STRING\"}" "$META_TOKEN"

echo ""

# ---------- studio-generator (8090) ----------
echo "[studio-generator:8090]"
MODULE="studio-generator"
GEN_BASE="http://localhost:8090/api/v1/generator"

# 无安全配置
test_endpoint "$MODULE" "GET" "${GEN_BASE}/tables"
test_endpoint "$MODULE" "POST" "${GEN_BASE}/tables/metadata" \
  '{"dataSourceId":"1"}'
test_endpoint "$MODULE" "GET" "${GEN_BASE}/generations"
test_endpoint "$MODULE" "POST" "${GEN_BASE}/generate" \
  '{"tableName":"iam_user","moduleName":"test-gen","packageName":"com.bone.test"}'

echo ""

# ---------- bone-extension-studio (8091) ----------
echo "[bone-extension-studio:8091]"
MODULE="bone-extension-studio"
EXT_BASE="http://localhost:8091/api/v1/extension"

# permitUnauthenticated=true 时无需 Token
# POST points 需要 interfaceName
test_endpoint "$MODULE" "GET" "${EXT_BASE}/points"
test_endpoint "$MODULE" "POST" "${EXT_BASE}/points" \
  '{"name":"test_extension_point_v4","interfaceName":"com.bone.test.TestPointV4","description":"端点测试扩展点"}'
test_endpoint "$MODULE" "GET" "${EXT_BASE}/plugins"
# POST plugins 需要 extPointId, name, className
test_endpoint "$MODULE" "POST" "${EXT_BASE}/plugins" \
  '{"name":"test_plugin_ep_v4","extPointId":"1","className":"com.bone.test.TestPluginV4","description":"端点测试插件"}'

echo ""

##############################################
# Step 4: 汇总输出
##############################################
echo "========================================"
echo "  端点验证汇总"
echo "========================================"
echo ""

printf "%-28s %-8s %-8s %-8s %-8s\n" "模块" "总数" "2xx" "4xx" "5xx/错误"
echo "------------------------------------------------------------------------"

TOTAL_ALL=0
TOTAL_2XX=0
TOTAL_4XX=0
TOTAL_5XX=0

for module in bone-iam bone-system bone-masterdata bone-integration bone-metadata-server studio-generator bone-extension-studio; do
  result_file="$RESULTS_DIR/${module}.txt"
  if [ ! -f "$result_file" ]; then
    printf "%-28s %-8s %-8s %-8s %-8s\n" "$module" "0" "0" "0" "0"
    continue
  fi
  
  total=0
  c2xx=0
  c4xx=0
  c5xx=0
  
  while IFS='|' read -r method url http_code category; do
    total=$((total + 1))
    case "$category" in
      2xx) c2xx=$((c2xx + 1)) ;;
      4xx) c4xx=$((c4xx + 1)) ;;
      5xx) c5xx=$((c5xx + 1)) ;;
    esac
  done < "$result_file"
  
  printf "%-28s %-8s %-8s %-8s %-8s\n" "$module" "$total" "$c2xx" "$c4xx" "$c5xx"
  
  TOTAL_ALL=$((TOTAL_ALL + total))
  TOTAL_2XX=$((TOTAL_2XX + c2xx))
  TOTAL_4XX=$((TOTAL_4XX + c4xx))
  TOTAL_5XX=$((TOTAL_5XX + c5xx))
done

echo "------------------------------------------------------------------------"
printf "%-28s %-8s %-8s %-8s %-8s\n" "合计" "$TOTAL_ALL" "$TOTAL_2XX" "$TOTAL_4XX" "$TOTAL_5XX"
echo ""

##############################################
# Step 5: 5xx 详细信息
##############################################
if [ "$TOTAL_5XX" -gt 0 ]; then
  echo "========================================"
  echo "  5xx / 连接失败 端点详细信息"
  echo "========================================"
  echo ""
  
  for detail_file in "$RESULTS_DIR"/*_5xx.txt; do
    if [ ! -f "$detail_file" ]; then
      continue
    fi
    module=$(basename "$detail_file" _5xx.txt)
    echo "[$module]"
    while IFS='|' read -r url http_code resp_body; do
      echo "  [FAIL] $url"
      echo "     状态码: $http_code"
      echo "     响应: ${resp_body:0:200}"
      echo ""
    done < "$detail_file"
  done
fi

##############################################
# Step 6: 4xx 详细信息
##############################################
if [ "$TOTAL_4XX" -gt 0 ]; then
  echo "========================================"
  echo "  4xx 端点详细信息"
  echo "========================================"
  echo ""
  
  for detail_file in "$RESULTS_DIR"/*_4xx.txt; do
    if [ ! -f "$detail_file" ]; then
      continue
    fi
    module=$(basename "$detail_file" _4xx.txt)
    echo "[$module]"
    while IFS='|' read -r url http_code resp_body; do
      echo "  [WARN] $url"
      echo "     状态码: $http_code"
      echo "     响应: ${resp_body:0:200}"
      echo ""
    done < "$detail_file"
  done
fi

echo "========================================"
echo "  验证完成"
echo "========================================"
