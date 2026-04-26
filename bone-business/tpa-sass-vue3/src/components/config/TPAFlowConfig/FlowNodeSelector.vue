<script setup lang="ts">
import { Condition, JumpType } from "@/api/jobConfig/flow";

defineOptions({
  name: "FlowNodeSelector",
});

const emit = defineEmits<{
  (e: "editScript", condition: Condition): void;
}>();

interface Props {
  nodeName: string;
  bgColor?: string;
}

withDefaults(defineProps<Props>(), {
  bgColor: "#f9f9f9",
});

const flowNode = defineModel<{ jumpTypeList: JumpType[] }>({ required: true });

// 选中的 jumpType
const selectedJumpType = computed({
  get: () => {
    const selected = flowNode.value.jumpTypeList.find(
      (item) => item.status === 1
    );
    return selected?.name || "";
  },
  set: (value: string) => {
    // 先将所有的 status 设置为 0
    flowNode.value.jumpTypeList.forEach((item) => {
      item.status = 0;
    });
    // 再将选中的 status 设置为 1
    const target = flowNode.value.jumpTypeList.find(
      (item) => item.name === value
    );
    if (target) {
      target.status = 1;
    }
  },
});

// 选中的 conditions
const selectedConditions = computed({
  get: () => {
    const selectedJumpType = flowNode.value.jumpTypeList.find(
      (item) => item.status === 1
    );
    if (!selectedJumpType || !selectedJumpType.conditionList) {
      return [];
    }
    return selectedJumpType.conditionList
      .filter((condition) => condition.status === 1)
      .map((condition) => condition.name);
  },
  set: (values: string[]) => {
    const selectedJumpType = flowNode.value.jumpTypeList.find(
      (item) => item.status === 1
    );
    if (!selectedJumpType || !selectedJumpType.conditionList) {
      return;
    }
    // 更新每个 condition 的 status
    selectedJumpType.conditionList.forEach((condition) => {
      condition.status = values.includes(condition.name) ? 1 : 0;
    });
  },
});

// 获取当前选中的 jumpType 的 conditionList（用于显示）
const conditionList = computed(() => {
  const selectedJumpType = flowNode.value.jumpTypeList.find(
    (item) => item.status === 1
  );
  return selectedJumpType?.conditionList || [];
});
</script>

<template>
  <div class="w-160px">
    <div class="flow-item" :style="{ backgroundColor: bgColor }">
      {{ nodeName }}
    </div>
    <el-select v-model="selectedJumpType" placeholder="请选择">
      <el-option
        v-for="item in flowNode.jumpTypeList"
        :key="item.name"
        :label="item.name"
        :value="item.name"
      />
    </el-select>
    <el-checkbox-group
      v-if="conditionList.length > 0"
      v-model="selectedConditions"
      class="mt-2"
    >
      <el-checkbox
        v-for="item in conditionList"
        :key="item.name"
        :value="item.name"
      >
        {{ item.name }}
        <el-button
          v-if="item.type == 1"
          type="primary"
          link
          size="small"
          @click="emit('editScript', item)"
        >
          编辑脚本
        </el-button>
      </el-checkbox>
    </el-checkbox-group>
  </div>
</template>

<style lang="scss" scoped>
.flow-item {
  @apply flex-center border px-2 py-1 mb-2 text-[#303133] text-14px;
}
</style>
