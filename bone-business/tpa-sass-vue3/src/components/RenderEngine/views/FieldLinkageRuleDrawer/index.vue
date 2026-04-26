<script setup>
import { Plus } from "@element-plus/icons-vue";
import { getBaseCompTypeName } from "@/enums/baseComp/BaseCompEnum";
import LinkageRuleAPI from "@/api/rule/linkageRule";
import FieldNFieldRule from "../LinkageRuleCard/FieldNFieldRule/index.vue";
import FieldNTableRule from "../LinkageRuleCard/FieldNTableRule/index.vue";

defineOptions({
  name: "FieldLinkageRuleDrawer",
});

const emits = defineEmits(["close"]);
const drawerVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  id: {
    type: String,
    default: "",
    required: true,
  },
  name: {
    type: String,
    default: "",
    required: true,
  },
  type: {
    type: String,
    default: "",
    required: true,
  },
});

const fieldFieldRules = ref([]);
const fieldTableRules = ref([]);
const isAddingFieldRule = ref(false);
const isAddingTableRule = ref(false);
const newFieldRuleForm = ref({});
const newTableRuleForm = ref({});

const initNewFieldRuleForm = () => {
  newFieldRuleForm.value = {
    fieldId: props.id,
    sourceOperator: "",
    sourceValueType: undefined,
    sourceValue: "",
    sourceValueCn: "",
    targetFields: [],
    propertyOrValue: undefined,
    targetFieldPropertyName: "",
    targetFieldPropertyValue: undefined,
    targetOperator: "",
    targetValueType: undefined,
    targetValue: "",
    targetValueCn: "",
  };
};

const initNewTableRuleForm = () => {
  newTableRuleForm.value = {
    fieldId: props.id,
    sourceOperator: "",
    sourceValueType: undefined,
    sourceValue: "",
    tableId: "",
    attributeName: "",
    attributeValue: "",
  };
};

initNewFieldRuleForm();
initNewTableRuleForm();

const initFieldLinkageRules = async () => {
  fieldFieldRules.value = await LinkageRuleAPI.getRuleByFieldId(props.id);
  fieldTableRules.value = await LinkageRuleAPI.getFieldTableRuleByFieldId(
    props.id
  );
};

watch(
  () => props.id,
  (newVal) => {
    if (newVal) {
      initFieldLinkageRules();
    }
  },
  { immediate: true }
);

const handleClose = () => {
  drawerVisible.value = false;
  initNewFieldRuleForm();
  initNewTableRuleForm();
  isAddingFieldRule.value = false;
  isAddingTableRule.value = false;
  emits("close");
};

const handleAddFieldRule = async () => {
  isAddingFieldRule.value = false;
  initNewFieldRuleForm();
  initFieldLinkageRules();
};

const handleAddTableRule = async () => {
  isAddingTableRule.value = false;
  initNewTableRuleForm();
  initFieldLinkageRules();
};

const handleShowAddFieldRule = () => {
  isAddingFieldRule.value = !isAddingFieldRule.value;
  isAddingTableRule.value = false;
  initNewFieldRuleForm();
};

const handleShowAddTableRule = () => {
  isAddingTableRule.value = !isAddingTableRule.value;
  isAddingFieldRule.value = false;
  initNewTableRuleForm();
};

const closeUpdateLinkageRuleDrawer = (isChange = false) => {
  updateLinkageRuleDrawer.value.visible = false;
  updateLinkageRuleDrawer.value.params = {
    fieldId: "",
    fieldName: "",
    compType: "",
    rule: {},
    ruleType: "",
  };
  if (isChange) {
    initFieldLinkageRules();
  }
};

const updateLinkageRuleDrawer = ref({
  visible: false,
  params: {
    fieldId: "",
    fieldName: "",
    compType: "",
    rule: {},
    ruleType: "",
  },
  onClose: closeUpdateLinkageRuleDrawer,
});

const handleRuleEdit = (row, ruleType) => {
  updateLinkageRuleDrawer.value.params = {
    fieldId: props.id,
    fieldName: props.name,
    compType: props.type,
    rule: row,
    ruleType: ruleType,
  };
  updateLinkageRuleDrawer.value.visible = true;
};
</script>

<template>
  <div class="field-linkage-rule-drawer">
    <el-drawer
      title="设置动态规则"
      size="50%"
      v-model="drawerVisible"
      :before-close="handleClose"
      destroy-on-close
    >
      <div class="drawer-content">
        <el-descriptions size="large">
          <el-descriptions-item label="业务字段">
            {{ name }}
          </el-descriptions-item>
          <el-descriptions-item label="组件类型">
            {{ getBaseCompTypeName(type) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="group-box">
          <p class="group-title">添加规则</p>
          <div class="flex justify-start items-center">
            <el-button type="primary" circle @click="handleShowAddFieldRule">
              <template #icon>
                <el-icon><Plus /></el-icon>
              </template>
            </el-button>
            <span class="prompt-text">
              <strong>当前字段</strong>
              的
              <strong>值</strong>
              改变 时，
              <strong>目标字段</strong>
              的
              <strong>属性或值</strong>
              会 如何改变
            </span>
          </div>

          <div class="mt-5 mb-5" v-if="isAddingFieldRule === true">
            <field-n-field-rule
              v-bind="{
                fieldId: id,
                type: type,
                status: 'add',
                rule: newFieldRuleForm,
              }"
              @add="handleAddFieldRule"
            />
          </div>

          <div class="flex justify-start items-center mt-5">
            <el-button type="primary" circle @click="handleShowAddTableRule">
              <template #icon>
                <el-icon><Plus /></el-icon>
              </template>
            </el-button>
            <span class="prompt-text">
              <strong>当前字段</strong>
              的
              <strong>值</strong>
              改变 时，
              <strong>目标表格</strong>
              的
              <strong>属性</strong>
              会 如何改变
            </span>
          </div>

          <div class="mt-5 mb-5" v-if="isAddingTableRule === true">
            <field-n-table-rule
              v-bind="{
                fieldId: id,
                type: type,
                status: 'add',
                rule: newTableRuleForm,
              }"
              @add="handleAddTableRule"
            />
          </div>
        </div>

        <div class="group-box">
          <p class="group-title">已有规则</p>

          <div class="mb-4" v-for="item in fieldFieldRules" :key="item.id">
            <field-n-field-rule
              @edit="handleRuleEdit(item, 'fieldNField')"
              v-bind="{
                fieldId: id,
                type: type,
                status: 'view',
                rule: item,
              }"
            />
          </div>

          <div class="mb-4" v-for="item in fieldTableRules" :key="item.id">
            <field-n-table-rule
              @edit="handleRuleEdit(item, 'fieldNTable')"
              v-bind="{
                fieldId: id,
                type: type,
                status: 'view',
                rule: item,
              }"
            />
          </div>
        </div>
      </div>
    </el-drawer>

    <UpdateLinkageRuleDrawer
      v-model="updateLinkageRuleDrawer.visible"
      v-bind="updateLinkageRuleDrawer.params"
      @close="updateLinkageRuleDrawer.onClose"
    />
  </div>
</template>

<style lang="scss" scoped>
.drawer-footer button:first-child {
  margin-right: 10px;
}

.drawer-content {
  padding-right: 20px;
  padding-left: 10px;
}

.group-box {
  margin-bottom: 20px;
}

.group-title {
  padding-left: 10px;
  margin-bottom: 25px;
  border-left: 3px solid var(--el-color-primary);
}

.flex-box {
  display: flex;
  align-items: center;
  justify-content: flex-start;
}

.prompt-text {
  margin-left: 15px;
  font-size: 16px;
  color: #606266;
}
</style>
