<script setup lang="ts">
import OptionConfigAPI, {
  IOptionSetVersion,
} from "@/api/systemManage/optionConfig";
import { formatDate } from "@/utils/date";
defineOptions({
  name: "SystemOptionVersionListDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  id: string;
}>();

const versionList = ref<IOptionSetVersion[]>([]);

const handleClose = () => {
  dialogVisible.value = false;
  emits("close");
};

const getVersionList = async () => {
  const res = await OptionConfigAPI.getOptionSetVersionList(props.id);
  versionList.value = res;
};

watch(dialogVisible, (newVal) => {
  if (newVal) {
    getVersionList();
  } else {
    versionList.value = [];
  }
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="版本记录"
    width="50%"
    top="10vh"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div class="dialog-content">
      <div class="flex items-center mb-2">
        <span class="text-sm">选项集名称</span>
        <span class="text-sm color-[var(--el-color-primary)] font-bold ml-2">
          {{ versionList[0]?.optionSetName }}
        </span>
      </div>
      <el-table :data="versionList" border stripe style="width: 100%">
        <el-table-column prop="version" label="版本" align="center" />
        <el-table-column prop="description" label="描述" align="center" />
        <el-table-column prop="createBy" label="创建人" align="center" />
        <el-table-column prop="createTime" label="创建时间" align="center">
          <template #default="scope">
            {{ formatDate(scope.row.createTime) }}
          </template>
        </el-table-column>
      </el-table>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 65vh;
  padding: 20px;
  overflow-y: auto; // 添加垂直滚动条
}
</style>
