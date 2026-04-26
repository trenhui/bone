<script setup>
import { SummaryRowStatusEnum } from "@/enums/table/SummaryRowStatusEnum";
import { GroupingAggregateStatusEnum } from "@/enums/table/GroupingAggregateStatusEnum";
defineOptions({
  name: "RuleSection",
});

const tableConfig = defineModel("tableConfig", {
  type: Object,
  required: true,
});

const SummaryRowDialog = defineAsyncComponent(
  () => import("./SummaryRowDialog/index.vue")
);
const GroupingAggregateDetailDialog = defineAsyncComponent(
  () => import("./GroupingAggregate/DetailDialog.vue")
);
const TableLinkageRuleDrawer = defineAsyncComponent(
  () =>
    import("@/components/RenderEngine/views/TableLinkageRule/DetailDrawer.vue")
);
const TableSubmitRuleDrawer = defineAsyncComponent(
  () =>
    import("@/components/RenderEngine/views/TableSubmitRule/DetailDrawer.vue")
);
const TableRelationDialog = defineAsyncComponent(
  () => import("./TableRelationDialog/index.vue")
);

const currentDialog = ref({
  visible: false,
  component: null,
  params: {},
});

const closeCurrentDialog = () => {
  currentDialog.value.visible = false;
  currentDialog.value.component = null;
  currentDialog.value.params = {};
};

const openDialog = (component, params) => {
  currentDialog.value = {
    visible: true,
    component,
    params: {
      ...params,
      tableId: tableConfig.value.id,
      tableName: tableConfig.value.tableName,
    },
  };
};

const handleClickTableRelation = () => openDialog(TableRelationDialog, {});
const handleClickSummaryRow = () => openDialog(SummaryRowDialog, {});
const handleClickGroupingAggregate = () =>
  openDialog(GroupingAggregateDetailDialog, {});
const handleClickTableLinkageRule = () =>
  openDialog(TableLinkageRuleDrawer, {});
const handleClickTableSubmitRule = () => openDialog(TableSubmitRuleDrawer, {});
</script>

<template>
  <div class="section">
    <p class="section-title">表行规则</p>
    <div class="section-content">
      <div class="flex-box-between">
        <div class="label-style">表格间关系</div>
        <el-button type="primary" link @click="handleClickTableRelation">
          查看表格关系
        </el-button>
      </div>
      <div class="flex-box-between">
        <div class="label-style">动态规则</div>
        <el-button type="primary" link @click="handleClickTableLinkageRule">
          配置动态规则
        </el-button>
      </div>
      <div class="flex-box-between">
        <div class="label-style">保存校验</div>
        <el-button type="primary" link @click="handleClickTableSubmitRule">
          配置校验规则
        </el-button>
      </div>
      <div class="flex-box-between">
        <div class="label-style">汇总行</div>
        <div class="flex items-center">
          <el-switch
            class="mr-2"
            v-model="tableConfig.enableDataSummary"
            :active-value="SummaryRowStatusEnum.OPENED"
            :inactive-value="SummaryRowStatusEnum.CLOSED"
          />
          <el-button
            type="primary"
            link
            :disabled="!tableConfig.enableDataSummary"
            @click="handleClickSummaryRow"
          >
            配置汇总行
          </el-button>
        </div>
      </div>
      <div class="flex-box-between">
        <div class="label-style">分组聚合</div>
        <div class="flex items-center">
          <el-switch
            class="mr-2"
            v-model="tableConfig.enableDataAggregate"
            :active-value="GroupingAggregateStatusEnum.OPENED"
            :inactive-value="GroupingAggregateStatusEnum.CLOSED"
          />
          <el-button
            type="primary"
            link
            :disabled="!tableConfig.enableDataAggregate"
            @click="handleClickGroupingAggregate"
          >
            配置分组聚合
          </el-button>
        </div>
      </div>
    </div>

    <component
      :is="currentDialog.component"
      v-if="currentDialog.visible"
      v-model="currentDialog.visible"
      v-bind="currentDialog.params"
      @close="closeCurrentDialog"
    />
  </div>
</template>

<style lang="scss" scoped>
.label-style {
  height: 32px;
  margin-bottom: 2px;
  margin-left: 10px;
  font-size: 14px;
  line-height: 32px;
  color: #606266;
}

.flex-box-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
