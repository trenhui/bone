<script setup>
import ConfigureEditableColumnDialog from "./ConfigureEditableColumnDialog.vue";
import { OperationEnabledOptions } from "@/enums/table/OperationEnabledEnum";
import { OperationFixedOptions } from "@/enums/table/OperationFixedEnum";
import { EventOwnerEnum } from "@/enums";
import EventAPI from "@/api/event";

defineOptions({
  name: "OperationSection",
});

const tableConfig = defineModel("tableConfig", {
  type: Object,
  required: true,
});
const displayColumns = defineModel("displayColumns", {
  type: Array,
  required: true,
});
const selectedRowEventList = defineModel("selectedRowEventList", {
  type: Array,
  required: true,
});
const selectedLeftHeaderEventList = defineModel("selectedLeftHeaderEventList", {
  type: Array,
  required: true,
});
const selectedRightHeaderEventList = defineModel(
  "selectedRightHeaderEventList",
  {
    type: Array,
    required: true,
  }
);
const dialogVisible = ref(false);
const handleAddOperationColumn = () => {
  dialogVisible.value = true;
};

const selectableRowEventList = ref([]);
const selectableLeftHeaderEventList = ref([]);
const selectableRightHeaderEventList = ref([]);

const selectedRowEventIdList = ref([]);
const selectedLeftHeaderEventIdList = ref([]);
const selectedRightHeaderEventIdList = ref([]);

const initSelectableEventList = async () => {
  selectableRowEventList.value = (
    await EventAPI.list({
      owner: EventOwnerEnum.TableRow,
      pageNum: 1,
      pageSize: 9999,
    })
  ).rows.map((item) => ({
    ...item,
    name: item.prepare !== 1 ? item.name : "预设 - " + item.name,
  }));
  selectableLeftHeaderEventList.value = (
    await EventAPI.list({
      owner: EventOwnerEnum.TableLeftHeader,
      pageNum: 1,
      pageSize: 9999,
    })
  ).rows.map((item) => ({
    ...item,
    name: item.prepare !== 1 ? item.name : "预设 - " + item.name,
  }));
  selectableRightHeaderEventList.value = (
    await EventAPI.list({
      owner: EventOwnerEnum.TableRightHeader,
      pageNum: 1,
      pageSize: 9999,
    })
  ).rows.map((item) => ({
    ...item,
    name: item.prepare !== 1 ? item.name : "预设 - " + item.name,
  }));
};
initSelectableEventList();

const initSelectedEventList = () => {
  selectedRowEventIdList.value = tableConfig.value.eventTriggerList
    ?.filter((item) => item.owner === EventOwnerEnum.TableRow)
    ?.map((item) => item.eventId);
  selectedLeftHeaderEventIdList.value = tableConfig.value.eventTriggerList
    ?.filter((item) => item.owner === EventOwnerEnum.TableLeftHeader)
    ?.map((item) => item.eventId);
  selectedRightHeaderEventIdList.value = tableConfig.value.eventTriggerList
    ?.filter((item) => item.owner === EventOwnerEnum.TableRightHeader)
    ?.map((item) => item.eventId);
};

watch(tableConfig, () => {
  if (tableConfig.value.eventTriggerList) {
    initSelectedEventList();
  }
});

watch(selectedRowEventIdList, () => {
  selectedRowEventList.value = selectedRowEventIdList.value.map((id) => ({
    eventId: id,
    owner: EventOwnerEnum.TableRow,
  }));
});
watch(selectedLeftHeaderEventIdList, () => {
  selectedLeftHeaderEventList.value = selectedLeftHeaderEventIdList.value.map(
    (id) => ({ eventId: id, owner: EventOwnerEnum.TableLeftHeader })
  );
});
watch(selectedRightHeaderEventIdList, () => {
  selectedRightHeaderEventList.value = selectedRightHeaderEventIdList.value.map(
    (id) => ({ eventId: id, owner: EventOwnerEnum.TableRightHeader })
  );
});
</script>

<template>
  <div class="section">
    <div class="section-header">
      <p class="section-title">操作列</p>
      <el-button type="primary" link @click="handleAddOperationColumn">
        配置可编辑列
      </el-button>
    </div>
    <div class="section-content">
      <el-form-item label="是否开启">
        <el-select
          v-model="tableConfig.operationColumnEnabled"
          placeholder="请选择"
        >
          <el-option
            v-for="item in OperationEnabledOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="是否固定">
        <el-select
          v-model="tableConfig.operationColumnFixed"
          placeholder="请选择"
        >
          <el-option
            v-for="item in OperationFixedOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="行事件">
        <el-select
          v-model="selectedRowEventIdList"
          placeholder="请选择"
          multiple
        >
          <el-option
            v-for="item in selectableRowEventList"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="左表头事件">
        <el-select
          v-model="selectedLeftHeaderEventIdList"
          placeholder="请选择"
          multiple
        >
          <el-option
            v-for="item in selectableLeftHeaderEventList"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="右表头事件">
        <el-select
          v-model="selectedRightHeaderEventIdList"
          placeholder="请选择"
          multiple
        >
          <el-option
            v-for="item in selectableRightHeaderEventList"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
    </div>

    <ConfigureEditableColumnDialog
      v-model="dialogVisible"
      v-model:displayColumns="displayColumns"
    />
  </div>
</template>

<style lang="scss" scoped></style>
