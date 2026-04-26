<script setup lang="ts">
import { BaseCompType, getBaseCompTypeName } from "@/enums";
import {
  DictSourceTypeEnum,
  getDictSourceTypeName,
} from "@/enums/DictSourceTypeEnum";
import OptionConfigAPI, {
  IUpdateLinkedOptionField,
} from "@/api/systemManage/optionConfig";
import FieldAPI from "@/api/field";
defineOptions({
  name: "ConfigLinkageOptionFieldDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  fieldId: string;
  fieldName: string;
  componentType: string;
  sourceType: number;
  sourceCode: string;
  sourceName: string;
  isTable?: boolean;
}>();

const confirmLoading = ref(false);
const optionFieldList = ref<any[]>([]);
const fieldOptionList = ref<any[]>([]);
const script = ref("");
const isAddScript = ref(false);

const supportComponentType = [
  BaseCompType.Input,
  BaseCompType.InputNum,
  BaseCompType.DateRange,
  BaseCompType.DateTime,
  BaseCompType.SelectCtrl,
  BaseCompType.SelectDrop,
];

const getOptionFieldList = async () => {
  const res = await OptionConfigAPI.getFieldLinkedDisplayRule(
    props.fieldId,
    props.sourceType,
    props.sourceCode
  );
  optionFieldList.value = res;
  console.log(res);
};

const getFieldOptionList = async () => {
  if (!props.isTable) {
    const res = await FieldAPI.getByFieldIdAndComponentType({
      fieldId: props.fieldId,
      componentType: supportComponentType.join(","),
    });
    fieldOptionList.value = res;
  } else {
    const res = await FieldAPI.getByTableFieldIdAndComponentType({
      fieldId: props.fieldId,
      componentType: supportComponentType.join(","),
    });
    fieldOptionList.value = res;
  }
};

const computedSourceName = computed(() => {
  return `${getDictSourceTypeName(props.sourceType)} - ${props.sourceName}`;
});

watch(
  () => dialogVisible.value,
  (val) => {
    if (val) {
      getOptionFieldList();
      getFieldOptionList();
    }
  },
  { immediate: true }
);

const handleClose = () => {
  dialogVisible.value = false;
  confirmLoading.value = false;
  optionFieldList.value = [];
  fieldOptionList.value = [];
  script.value = "";
  isAddScript.value = false;
  emits("close");
};

/** 保存 */
const handleConfirm = useDebounceFn(async () => {
  const data: IUpdateLinkedOptionField = {
    selectFieldId: props.fieldId,
    datasourceType: props.sourceType,
    datasourceCode: props.sourceCode,
    entryList: optionFieldList.value
      .map((item) => ({
        type: item.type,
        extraProperty: item.extraProperty,
        fieldId: item.fieldId,
        script: item.script,
      }))
      .filter((item) => item.fieldId),
  };
  try {
    confirmLoading.value = true;
    await OptionConfigAPI.updateFieldLinkedDisplayRule(data);
    ElMessage.success("保存成功");
    handleClose();
  } catch (error) {
    console.log(error);
  } finally {
    confirmLoading.value = false;
  }
});

const handleDelete = (row: any) => {
  if (row.type === 1) {
    ElMessageBox.confirm("确定删除该脚本字段吗？", "提示", {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
    })
      .then(() => {
        optionFieldList.value = optionFieldList.value.filter(
          (item) => item !== row
        );
      })
      .catch(() => {
        return;
      });
  } else {
    // 清除当前行的fieldId
    row.fieldId = "";
  }
};

const handleAddScript = () => {
  isAddScript.value = false;
  optionFieldList.value.push({
    type: 1,
    script: script.value,
    fieldId: "",
    extraProperty: "",
  });
  script.value = "";
};

const handleCancelScript = () => {
  isAddScript.value = false;
  script.value = "";
};

const handleViewScript = (row: any) => {
  ElMessageBox.alert(h("div", row.script), "脚本内容", {
    confirmButtonText: "确定",
  });
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    :close-on-click-modal="false"
    title="设置联动展示字段"
    width="50%"
  >
    <div class="dialog-content px-4 py-2">
      <p>
        设置当前下拉字段选择某一个选项值时，指定其他业务字段展示该选项值的其他字段信息
      </p>

      <el-form label-width="auto" label-position="right" inline>
        <el-form-item label="业务字段">
          <span class="font-bold">{{ props.fieldName }}</span>
        </el-form-item>
        <el-form-item label="组件类型">
          <span class="font-bold">
            {{ getBaseCompTypeName(props.componentType) }}
          </span>
        </el-form-item>
        <el-form-item label="选项数据源">
          <span class="font-bold">{{ computedSourceName }}</span>
        </el-form-item>
      </el-form>

      <el-table :data="optionFieldList" border>
        <el-table-column label="类型" align="center" width="120">
          <template #default="scope">
            {{
              scope.row.type === 0 || scope.row.type === null
                ? "扩展字段"
                : "脚本字段"
            }}
          </template>
        </el-table-column>
        <el-table-column label="选项集值" align="center">
          <template #default="scope">
            <span v-if="scope.row.type === 0 || scope.row.type === null">
              {{ scope.row.extraProperty }}
            </span>
            <span v-else>
              <el-button
                type="primary"
                link
                size="small"
                @click="handleViewScript(scope.row)"
              >
                查看脚本内容
              </el-button>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="业务字段" align="center">
          <template #default="scope">
            <el-select
              v-model="scope.row.fieldId"
              placeholder="请选择"
              filterable
            >
              <el-option
                v-for="item in fieldOptionList"
                :key="item.id"
                :label="item.bizName"
                :value="item.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          prop="optionDataSource"
          align="center"
          width="100"
        >
          <template #default="scope">
            <el-button
              type="danger"
              link
              @click="handleDelete(scope.row)"
              v-if="scope.row.fieldId || scope.row.type === 1"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="mt-4">
        <div>
          <el-button
            v-if="!isAddScript"
            type="primary"
            @click="isAddScript = !isAddScript"
            link
            size="small"
          >
            添加脚本字段
          </el-button>

          <el-button
            v-if="isAddScript"
            type="primary"
            @click="handleAddScript"
            link
            size="small"
          >
            确定
          </el-button>

          <el-button
            v-if="isAddScript"
            type="primary"
            @click="handleCancelScript"
            link
            size="small"
          >
            取消
          </el-button>
        </div>

        <el-input
          v-if="isAddScript"
          class="mt-2"
          v-model="script"
          placeholder="请输入脚本"
          type="textarea"
          :rows="3"
        />
      </div>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button
          :loading="confirmLoading"
          type="primary"
          @click="handleConfirm"
        >
          确认
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 70vh;
  overflow-y: auto;
}
</style>
