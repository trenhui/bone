<script setup lang="ts">
import FieldAPI from "@/api/field";
import { isEmpty } from "lodash-es";
import { TabCondition } from "@/api/processPage";
defineOptions({
  name: "ConfigTabDialog",
});

const emits = defineEmits(["close", "confirm"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  tableId: string;
  oriTabConditionList?: TabCondition[];
}>();

const tableInfo = ref<any>([]);
const defaultTabCondition: TabCondition = {
  title: "全部",
  fieldValue: "",
  fieldId: "",
  isEditing: false,
  isNew: false,
};
const tabConditionList = ref<TabCondition[]>([]);

const tabConditionFieldId = ref<string>("");

const initTableInfo = async () => {
  if (!Array.isArray(tableInfo.value)) {
    return [];
  }
  tableInfo.value = await FieldAPI.getByTableId(props.tableId);
};

const modelNames = computed(() => {
  return tableInfo.value?.map((item: any) => item.modelName).join(",");
});
const tableFieldList = computed(() => {
  return tableInfo.value?.map((item: any) => item.fieldList).flat();
});

watch(
  () => props.tableId,
  () => {
    if (props.tableId) {
      initTableInfo();
      tabConditionFieldId.value = props.oriTabConditionList?.[0]?.fieldId || "";

      // 默认添加一个全部Tab
      tabConditionList.value = [
        defaultTabCondition,
        ...(props.oriTabConditionList || []),
      ];
    }
  },
  { immediate: true }
);

const onClose = () => {
  dialogVisible.value = false;
  tabConditionList.value = [];
  tabConditionFieldId.value = "";
  emits("close");
};

const handleFieldChange = () => {
  tabConditionList.value = [];
  tabConditionList.value.push(defaultTabCondition);
};

const handleAdd = () => {
  if (!tabConditionFieldId.value) {
    ElMessage.warning("请先设置条件字段");
    return;
  }

  tabConditionList.value.push({
    title: "",
    fieldValue: "",
    fieldId: tabConditionFieldId.value,
    isEditing: true,
    isNew: true,
  });
};

const handleCancel = (index: number) => {
  if (tabConditionList.value[index].isNew) {
    tabConditionList.value.splice(index, 1);
  } else {
    tabConditionList.value[index].isEditing = false;
  }
};

const handleSave = (row: TabCondition) => {
  if (row.title === "") {
    ElMessage.warning("请设置Tab页名称");
    return;
  }

  if (row.fieldValue === "") {
    ElMessage.warning("请设置条件值");
    return;
  }

  //title不能重复
  if (
    tabConditionList.value.some(
      (item) => item.title === row.title && item !== row
    )
  ) {
    ElMessage.warning("Tab页名称不能重复");
    return;
  }

  row.isEditing = false;
  row.isNew = false;
};

const handleDelete = (index: number) => {
  tabConditionList.value.splice(index, 1);
};

const handleEdit = (row: TabCondition) => {
  row.isEditing = true;
};

const handleConfirm = () => {
  if (!tabConditionFieldId.value || isEmpty(tabConditionList.value)) {
    ElMessage.warning("请设置条件字段及Tab条件");
    return;
  }

  if (tabConditionList.value.some((item) => item.isEditing)) {
    ElMessage.warning("请先保存编辑内容");
    return;
  }

  if (
    tabConditionList.value.length === 1 &&
    tabConditionList.value[0].title === "全部"
  ) {
    ElMessage.warning("不能只有“全部”Tab页");
    return;
  }

  emits(
    "confirm",
    tabConditionList.value.filter((item) => item.title !== "全部")
  );
  onClose();
};

const handleClose = () => {
  onClose();
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    append-to-body
    :close-on-click-modal="false"
    title="配置条件Tab页"
    width="40%"
  >
    <div class="dialog-content">
      <el-form label-width="auto">
        <el-form-item label="数据模型" class="w-[66%]">
          <span class="text-[var(--el-color-primary)]">
            {{ modelNames }}
          </span>
        </el-form-item>
        <el-form-item label="条件字段">
          <el-select
            v-model="tabConditionFieldId"
            placeholder="请选择业务字段"
            filterable
            @change="handleFieldChange"
            tag-type="primary"
          >
            <el-option
              v-for="item in tableFieldList"
              :key="item.id"
              :label="item.bizName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="设置Tab">
          <el-table ref="tableRef" :data="tabConditionList" border>
            <el-table-column label="Tab页名称" align="center">
              <template #default="{ row }">
                <template v-if="row.isEditing">
                  <el-input v-model="row.title" />
                </template>
                <template v-else>
                  {{ row.title }}
                </template>
              </template>
            </el-table-column>
            <el-table-column label="条件" align="center">
              <template #default="{ row }">
                <template v-if="row.isEditing">
                  <el-input v-model="row.fieldValue" />
                </template>
                <template v-else>
                  {{ row.fieldValue || "无" }}
                </template>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center">
              <template #default="scope">
                <template v-if="scope.$index !== 0">
                  <template v-if="scope.row.isEditing">
                    <el-button
                      type="warning"
                      link
                      @click="handleCancel(scope.$index)"
                    >
                      取消
                    </el-button>
                    <el-button
                      type="success"
                      link
                      @click="handleSave(scope.row)"
                    >
                      保存
                    </el-button>
                  </template>
                  <template v-else>
                    <el-button
                      type="warning"
                      link
                      @click="handleEdit(scope.row)"
                    >
                      编辑
                    </el-button>
                    <el-button
                      type="danger"
                      link
                      @click="handleDelete(scope.$index)"
                    >
                      删除
                    </el-button>
                  </template>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <el-button type="primary" link class="mt-2" @click="handleAdd">
            新增
          </el-button>
        </el-form-item>
        <el-form-item label="说明">
          展示规则：条件字段目前仅限一个业务字段。
        </el-form-item>
      </el-form>
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
  max-height: 50vh;
  padding: 20px;
  overflow-y: auto;
}
</style>
