<script setup lang="ts">
import LiabilityAPI, {
  NextDTO,
  PlanDTO,
  LiabilityNextDTO,
  NextConfiged,
} from "@/api/liability";
defineOptions({
  name: "NextRelation",
});

const props = defineProps<{
  policyNo: string;
}>();

const liabilityConfigList = ref<LiabilityNextDTO[]>([]);
const configedList = ref<NextConfiged[]>([]);
const planList = ref<PlanDTO[]>([]);
const nextTypeList = ref<{ key: string; value: string }[]>([]);
const confirmLoading = ref(false);

const init = async () => {
  try {
    const res = await LiabilityAPI.nextRelationPageInit(props.policyNo);
    liabilityConfigList.value = res.liabilityConfigList;
    configedList.value = res.configedList;
    planList.value = res.planList;
    nextTypeList.value = res.nextTypeList;
  } catch (error) {
    console.error("error", error);
  }
};

onMounted(() => {
  init();
});

const getSelectableLiabilityList = (planId: string) => {
  // 先选计划，根据计划id过滤责任列表
  if (!planId) {
    return [];
  }

  return liabilityConfigList.value.filter(
    (liability) => liability.planId === planId
  );
};

const validateNext = (row: NextConfiged) => {
  /**
   *
      先赔与后赔责任不可同一个责任
      提示错误信息："不能选择同一个责任"
      举例：不可添加A->A

      不可重复先赔和后赔责任
      提示错误信息："该后付责任关系已存在"
      举例：已有A -> B，不可再添加A -> B

      一个责任设置为多次先赔责任
      提示错误信息："一个责任仅能设置一次为先赔责任"
      举例：已有A -> B，不可再添加A -> C

      有多条关系时：
      先赔责任与后赔责任，互为后赔责任
      提示错误信息："两者责任不可互为后赔责任"
      举例：已有A -> B，不允许：B -> A

      先赔责任与后赔责任不可循环
      提示错误信息："多条责任不可互为后赔责任"
      举例：已有A -> B -> C，不允许：C -> A或C -> B

      一个先赔责任，超过两层后赔
      提示错误信息："最多限两层后赔责任"
      举例：不可A -> B -> C -> D，最多A -> B -> C
   */

  // 检查是否为空值
  if (!row.id || !row.nextLiabilityUuid || !row.nextLiabilityType) {
    return {
      valid: false,
      message: "请完善信息",
    };
  }

  // 获取先赔责任和后赔责任的信息
  const firstLiability = liabilityConfigList.value.find(
    (liability) => liability.id === row.id
  );

  const nextLiability = liabilityConfigList.value.find(
    (liability) => liability.uuid === row.nextLiabilityUuid
  );

  if (!firstLiability || !nextLiability) {
    return {
      valid: false,
      message: "找不到对应的责任信息",
    };
  }

  // 1. 先赔与后赔责任不可同一个责任
  // 比较先赔责任的id和uuid与后赔责任的id和uuid
  if (firstLiability.id === nextLiability.id) {
    return {
      valid: false,
      message: "不能选择同一个责任",
    };
  }

  // 获取现有的配置列表（排除当前正在编辑的行）
  const existingConfigs = configedList.value.filter((item) => !item.isEditing);

  // 2. 不可重复先赔和后赔责任
  // 检查是否已存在相同的先赔id和后赔uuid的配置
  const hasDuplicate = existingConfigs.some(
    (item) =>
      item.id === row.id && item.nextLiabilityUuid === row.nextLiabilityUuid
  );
  if (hasDuplicate) {
    return {
      valid: false,
      message: "该后付责任关系已存在",
    };
  }

  // 3. 一个责任设置为多次先赔责任
  // 检查是否已存在相同先赔id但不同后赔uuid的配置
  const hasMultipleFirst = existingConfigs.some(
    (item) =>
      item.id === row.id && item.nextLiabilityUuid !== row.nextLiabilityUuid
  );
  if (hasMultipleFirst) {
    return {
      valid: false,
      message: "一个责任仅能设置一次为先赔责任",
    };
  }

  // 4. 先赔责任与后赔责任，互为后赔责任
  // 创建id和uuid的映射关系
  const idToUuidMap = new Map<string, string>();
  const uuidToIdMap = new Map<string, string>();

  liabilityConfigList.value.forEach((liability) => {
    idToUuidMap.set(liability.id, liability.uuid);
    uuidToIdMap.set(liability.uuid, liability.id);
  });

  // 检查是否存在互为后赔的情况
  const hasMutualNext = existingConfigs.some((item) => {
    // 当前行: row.id -> row.nextLiabilityUuid
    // 检查是否存在: nextLiability.id -> firstLiability.uuid
    const nextLiabilityId = uuidToIdMap.get(row.nextLiabilityUuid);
    return (
      item.id === nextLiabilityId &&
      item.nextLiabilityUuid === firstLiability.uuid
    );
  });

  if (hasMutualNext) {
    return {
      valid: false,
      message: "两者责任不可互为后赔责任",
    };
  }

  // 5. 先赔责任与后赔责任不可循环 和 6. 一个先赔责任，超过两层后赔
  // 构建关系图以检测循环和层级，使用uuid作为节点标识
  const relationMap = new Map<string, string[]>();

  // 添加现有配置到关系图
  existingConfigs.forEach((item) => {
    const firstUuid = idToUuidMap.get(item.id) || "";
    if (!relationMap.has(firstUuid)) {
      relationMap.set(firstUuid, []);
    }
    relationMap.get(firstUuid)?.push(item.nextLiabilityUuid);
  });

  // 临时添加当前行到关系图
  const firstUuid = idToUuidMap.get(row.id) || "";
  if (!relationMap.has(firstUuid)) {
    relationMap.set(firstUuid, []);
  }
  relationMap.get(firstUuid)?.push(row.nextLiabilityUuid);

  // 检测是否有循环
  const visited = new Set<string>();
  const path = new Set<string>();

  function hasCycle(nodeUuid: string): boolean {
    if (path.has(nodeUuid)) {
      return true;
    }

    if (visited.has(nodeUuid)) {
      return false;
    }

    visited.add(nodeUuid);
    path.add(nodeUuid);

    const neighbors = relationMap.get(nodeUuid) || [];
    for (const neighbor of neighbors) {
      if (hasCycle(neighbor)) {
        return true;
      }
    }

    path.delete(nodeUuid);
    return false;
  }

  if (hasCycle(firstUuid)) {
    return {
      valid: false,
      message: "多条责任不可互为后赔责任",
    };
  }

  // 检测责任链的最大深度
  function getMaxDepth(nodeUuid: string): number {
    const neighbors = relationMap.get(nodeUuid) || [];
    if (neighbors.length === 0) {
      return 1;
    }

    let maxChildDepth = 0;
    for (const neighbor of neighbors) {
      maxChildDepth = Math.max(maxChildDepth, getMaxDepth(neighbor));
    }

    return maxChildDepth + 1;
  }

  // 检查是否超过最大深度（3层）
  // 检查整个关系网的最大深度
  let maxDepthInNetwork = 0;

  // 构建反向图，从子节点指向父节点
  const reverseMap = new Map<string, string[]>();

  // 初始化所有节点在反向图中
  Array.from(relationMap.keys()).forEach((key) => {
    if (!reverseMap.has(key)) {
      reverseMap.set(key, []);
    }
  });

  // 填充反向关系
  relationMap.forEach((neighbors, node) => {
    neighbors.forEach((neighbor) => {
      if (!reverseMap.has(neighbor)) {
        reverseMap.set(neighbor, []);
      }
      reverseMap.get(neighbor)?.push(node);
    });
  });

  // 找出所有没有入边的节点（起始节点）
  const startNodes = Array.from(reverseMap.keys()).filter((node) => {
    return (reverseMap.get(node)?.length || 0) === 0;
  });

  console.log("起始节点:", startNodes);

  // 计算从每个起始节点开始的最大深度
  startNodes.forEach((node) => {
    const depth = getMaxDepth(node);
    console.log(`节点 ${node} 的最大深度: ${depth}`);
    maxDepthInNetwork = Math.max(maxDepthInNetwork, depth);
  });

  console.log("整个网络的最大深度:", maxDepthInNetwork);

  if (maxDepthInNetwork > 3) {
    return {
      valid: false,
      message: "最多限两层后赔责任",
    };
  }

  return {
    valid: true,
    message: "",
  };
};

const handleAdding = () => {
  const hasEditing = configedList.value.some((row) => row.isEditing);
  if (hasEditing) {
    ElMessage.warning("请先完成正在编辑的行");
    return;
  }

  configedList.value.push({
    coverageId: "",
    nextLiabilityUuid: "",
    nextLiabilityType: "",
    planName: "",
    planId: "",
    id: "",
    nextLiabilityName: "",
    liabilityName: "",
    isEditing: true,
  });
};

const handleCancel = (index: number) => {
  configedList.value.splice(index, 1);
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm("确定删除该行？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(async () => {
      await LiabilityAPI.deleteNextRelation({ id: row.id });
      ElMessage.success("删除成功");
      init();
    })
    .catch(() => {});
};

const handleConfirm = async (row: NextConfiged) => {
  try {
    const result = validateNext(row);
    if (!result.valid) {
      ElMessage.error(result.message);
      return;
    }

    confirmLoading.value = true;
    //保存的网络请求
    await LiabilityAPI.createNextRelation({
      id: row.id,
      nextUuid: row.nextLiabilityUuid,
      nextType: row.nextLiabilityType,
    });

    ElMessage.success("保存成功");
    init();
  } catch (error) {
    console.log("error", error);
  } finally {
    confirmLoading.value = false;
  }
};
</script>

<template>
  <div>
    <div class="flex items-center justify-between">
      <span>先后赔付关系责任列表</span>
      <div>
        <el-button type="primary" @click="handleAdding">
          新增先后赔付责任
        </el-button>
      </div>
    </div>
    <el-table class="mt-2" border :data="configedList">
      <el-table-column label="计划名称" align="center">
        <template #default="{ row }">
          <el-select
            :disabled="!row.isEditing"
            placeholder="请选择"
            v-model="row.planId"
          >
            <el-option
              v-for="option in planList"
              :key="option.id"
              :label="option.planName"
              :value="option.id"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="先赔责任" align="center">
        <template #default="{ row }">
          <el-select
            :disabled="!row.isEditing"
            placeholder="请选择"
            v-model="row.id"
          >
            <el-option
              v-for="option in getSelectableLiabilityList(row.planId)"
              :key="option.id"
              :label="option.liabilityName"
              :value="option.id"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="后赔责任" align="center">
        <template #default="{ row }">
          <el-select
            :disabled="!row.isEditing"
            placeholder="请选择"
            v-model="row.nextLiabilityUuid"
          >
            <el-option
              v-for="option in getSelectableLiabilityList(row.planId)"
              :key="option.id"
              :label="option.liabilityName"
              :value="option.uuid"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="后赔规则" align="center">
        <template #default="{ row }">
          <el-select
            :disabled="!row.isEditing"
            placeholder="请选择"
            v-model="row.nextLiabilityType"
          >
            <el-option
              v-for="option in nextTypeList"
              :key="option.key"
              :label="option.value"
              :value="option.key"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center">
        <template #default="scope">
          <template v-if="scope.row.isEditing">
            <el-button
              type="success"
              icon="Check"
              link
              @click="handleConfirm(scope.row)"
            >
              确认
            </el-button>
            <el-button
              type="warning"
              icon="Close"
              link
              @click="handleCancel(scope.$index)"
            >
              取消
            </el-button>
          </template>
          <template v-else>
            <el-button
              type="danger"
              icon="Delete"
              link
              @click="handleDelete(scope.row)"
            >
              删除
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped></style>
