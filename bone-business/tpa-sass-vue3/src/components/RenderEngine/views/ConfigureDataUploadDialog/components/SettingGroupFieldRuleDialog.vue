<script setup lang="ts">
defineOptions({
  name: "SettingGroupFieldRuleDialog",
});

const emits = defineEmits(["close", "confirm"]);
const dialogVisible = defineModel({ type: Boolean, default: false });

const props = defineProps<{
  index?: number;
  fieldList?: any[];
  fieldIdListA?: string[];
  fieldIdListB?: string[];
}>();

const innerFieldIdListA = ref<string[]>([]);
const innerFieldIdListB = ref<string[]>([]);

const unwatch = watch(
  () => dialogVisible.value,
  (val) => {
    if (val) {
      innerFieldIdListA.value = props.fieldIdListA || [];
      innerFieldIdListB.value = props.fieldIdListB || [];
    }
  }
);

const handleClose = () => {
  dialogVisible.value = false;
  innerFieldIdListA.value = [];
  innerFieldIdListB.value = [];
  emits("close");
};

const handleConfirm = () => {
  if (
    innerFieldIdListA.value.length === 0 ||
    innerFieldIdListB.value.length === 0
  ) {
    ElMessage.warning("请选择字段A和字段B");
    return;
  }

  emits("confirm", {
    index: props.index,
    fieldIdListA: innerFieldIdListA.value,
    fieldIdListB: innerFieldIdListB.value,
  });

  handleClose();
};

onBeforeUnmount(() => {
  unwatch();
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="组合字段内规则"
    width="40%"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div class="dialog-content">
      <div class="flex items-center mb-3">
        <span class="">组合字段</span>
        <span class="ml-3 font-bold table-title">
          {{ `${fieldList?.map((item) => item.bizName).join(" ")}` }}
        </span>
      </div>

      <div class="mt-6">
        <p class="mb-3 text-15px text-[var(--el-color-primary)]">修改规则</p>
        <div class="flex justify-start items-center mb-3">
          <el-button type="primary" icon="Plus" circle size="small" />
          <div class="ml-2 text-14px text-[var(--el-text-color-regular)]">
            <span>
              字段A的值
              <strong>相同</strong>
              时，字段B的值
              <strong>必须相同</strong>
            </span>
          </div>
        </div>
        <el-card shadow="never" class="w-full">
          <div
            class="flex justify-start items-center text-14px text-[var(--el-text-color-regular)]"
          >
            <el-select
              v-model="innerFieldIdListA"
              multiple
              placeholder="请选择字段A"
            >
              <el-option
                v-for="item in fieldList"
                :key="item.id"
                :label="item.bizName"
                :value="item.id"
              />
            </el-select>
            <span class="flex-shrink-0 mx-2">的值相同时，</span>
            <el-select
              v-model="innerFieldIdListB"
              multiple
              placeholder="请选择字段B"
            >
              <el-option
                v-for="item in fieldList"
                :key="item.id"
                :label="item.bizName"
                :value="item.id"
              />
            </el-select>
            <span class="flex-shrink-0 mx-2">的值必须相同</span>
          </div>
        </el-card>
      </div>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确认</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  padding: 0 15px;
}
</style>
