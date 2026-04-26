<script setup lang="ts">
import OptionConfigAPI from "@/api/systemManage/optionConfig";
import { cloneDeep } from "lodash-es";

const emit = defineEmits(["success"]);

const dialogVisible = ref(false);
const id = ref("");
const extraPropertyKey = ref<string[]>([]);
const newKeyName = ref("");
const saveLoading = ref(false);

const open = (
  currentOptionSetId: string,
  currentExtraPropertyKey: string[]
) => {
  id.value = currentOptionSetId;
  extraPropertyKey.value = cloneDeep(currentExtraPropertyKey);
  dialogVisible.value = true;
};

const handleClose = () => {
  dialogVisible.value = false;
  id.value = "";
  newKeyName.value = "";
  extraPropertyKey.value = [];
};

const handleDelete = (key: string) => {
  extraPropertyKey.value = extraPropertyKey.value.filter(
    (item) => item !== key
  );
};

const handleAdd = () => {
  if (!newKeyName.value.trim()) {
    ElMessage.warning("请输入字段名称");
    return;
  }

  if (extraPropertyKey.value.some((item) => item === newKeyName.value)) {
    ElMessage.warning("字段名称已存在");
    return;
  }

  extraPropertyKey.value.push(newKeyName.value);
  newKeyName.value = "";
};

const handleSave = async () => {
  if (newKeyName.value.trim()) {
    ElMessage.warning("存在未添加的字段，请先确认添加");
    return;
  }

  try {
    saveLoading.value = true;
    await OptionConfigAPI.updateOptionSet({
      optionSetId: id.value,
      extraPropertyKey: JSON.stringify(extraPropertyKey.value || []),
    });
    ElMessage.success("保存成功");
    emit("success");
    handleClose();
  } catch (error) {
    console.error(error);
  } finally {
    saveLoading.value = false;
  }
};

defineExpose({
  open,
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="编辑额外字段"
    width="25%"
    @close="handleClose"
    :close-on-click-modal="false"
  >
    <div>
      <div class="max-h-[400px] overflow-y-auto">
        <template v-for="key in extraPropertyKey" :key="key">
          <div
            class="flex items-center justify-between gap-2 bg-[var(--el-fill-color-light)] border border-[var(--el-border-color-light)] px-2 py-1 rounded-sm mb-2"
          >
            <span>{{ key }}</span>
            <el-button type="danger" link @click="handleDelete(key)">
              删除
            </el-button>
          </div>
        </template>
      </div>
      <template v-if="!extraPropertyKey.length">
        <div class="text-center text-sm text-[var(--el-text-color-secondary)]">
          暂无字段
        </div>
      </template>

      <el-divider />

      <div class="flex items-center gap-2">
        <el-input
          v-model.trim="newKeyName"
          placeholder="请输入字段名称"
          class="w-full"
          clearable
        />
        <el-button type="primary" link @click="handleAdd">添加</el-button>
      </div>
    </div>
    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saveLoading">
          保存
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>
