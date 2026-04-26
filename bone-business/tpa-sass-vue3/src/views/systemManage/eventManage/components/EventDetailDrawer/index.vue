<script setup lang="ts">
import {
  EventTypeOptions,
  getEventTypeLabel,
} from "@/enums/event/EventTypeEnum";
import { TriggerOptions, getTriggerLabel } from "@/enums/event/TriggerEnum";
import {
  EventLevelOptions,
  getEventLevelLabel,
} from "@/enums/event/EventLevelEnum";
import {
  EventOwnerOptions,
  getEventOwnerLabel,
} from "@/enums/event/EventOwnerEnum";
import EventAPI from "@/api/event";
defineOptions({
  name: "EventDetailDrawer",
});
import { cloneDeep, isEqual } from "lodash-es";
import { ElForm } from "element-plus";

const emits = defineEmits(["close"]);
const drawerVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  id: {
    type: String,
    default: "",
  },
});

const confirmLoading = ref(false);
const isChange = ref(false);
const isView = ref(true);
const formRef = ref<InstanceType<typeof ElForm> | null>(null);
let initialEventDetail = {};
const eventDetail = ref<any>({
  name: "",
  code: "",
  type: "",
  triggerType: "",
  level: "",
  owner: "",
  ruleDescription: "",
  remark: "",
});

const initEventDetail = async () => {
  try {
    initialEventDetail = await EventAPI.get(props.id);
    eventDetail.value = cloneDeep(initialEventDetail);
  } catch (error) {
    console.error(error);
  }
};

watch(
  () => props.id,
  (newVal) => {
    if (newVal) {
      initEventDetail();
    }
  }
);

const resetForm = () => {
  eventDetail.value = {
    name: "",
    code: "",
    type: "",
    triggerType: "",
    level: "",
    owner: "",
    ruleDescription: "",
    remark: "",
  };
  if (formRef.value) {
    formRef.value.resetFields();
  }
};

const rules = {
  name: [{ required: true, message: "请输入事件名称" }],
  code: [{ required: true, message: "请输入事件标识" }],
  type: [{ required: true, message: "请选择事件类型" }],
  triggerType: [{ required: true, message: "请选择触发机制" }],
  level: [{ required: true, message: "请选择对象层级" }],
  owner: [{ required: true, message: "请选择对应位置" }],
};

const formatValue = (value: any, type: string) => {
  if (value === null || value === undefined || value === "") {
    return "";
  }
  if (type === "type") {
    return getEventTypeLabel(value);
  }
  if (type === "triggerType") {
    return getTriggerLabel(value);
  }
  if (type === "level") {
    return getEventLevelLabel(value);
  }
  if (type === "owner") {
    return getEventOwnerLabel(value);
  }
  return value;
};

const handleEdit = () => {
  isView.value = false;
};

const onClose = () => {
  drawerVisible.value = false;
  resetForm();
  emits("close", isChange.value);
  isView.value = true;
  isChange.value = false;
};

const handleClose = () => {
  if (!isEqual(eventDetail.value, initialEventDetail)) {
    ElMessageBox.confirm("内容存在更改，是否保存数据?", "提示", {
      confirmButtonText: "保存",
      cancelButtonText: "放弃保存",
      type: "warning",
      center: true,
    })
      .then(() => {
        handleConfirm();
      })
      .catch(() => {
        onClose();
      });
  } else {
    onClose();
  }
};

const handleConfirm = async () => {
  if (formRef.value) {
    const valid = await formRef.value.validate();
    if (!valid) return;
  }

  try {
    confirmLoading.value = true;
    await EventAPI.update(eventDetail.value);
    ElMessage.success("保存成功");
    isChange.value = true;
    isView.value = true;
    initEventDetail();
  } catch (error) {
    console.error(error);
  } finally {
    confirmLoading.value = false;
  }
};
</script>

<template>
  <el-drawer
    v-model="drawerVisible"
    :before-close="handleClose"
    title="事件详情"
    size="35%"
    destroy-on-close
    :show-close="false"
  >
    <template #header="{ titleId, titleClass }">
      <div class="flex justify-between items-center">
        <h4 :id="titleId" :class="titleClass">事件详情</h4>
        <div>
          <el-button v-if="isView" type="primary" @click="handleEdit">
            编辑
          </el-button>
          <el-button
            v-else
            type="danger"
            @click="handleConfirm"
            :loading="confirmLoading"
          >
            保存
          </el-button>
        </div>
      </div>
    </template>
    <div class="p-2">
      <el-form
        ref="formRef"
        label-width="auto"
        :model="eventDetail"
        :rules="rules"
      >
        <el-form-item required label="事件名称" prop="name">
          <el-input :readonly="isView" v-model="eventDetail.name" />
        </el-form-item>

        <el-form-item required label="事件标识" prop="code">
          <el-input :readonly="isView" v-model="eventDetail.code" disabled />
        </el-form-item>

        <el-form-item required label="事件类型" prop="type">
          <el-input
            v-if="isView"
            :readonly="isView"
            :model-value="formatValue(eventDetail.type, 'type')"
          />
          <el-select v-else v-model="eventDetail.type">
            <el-option
              v-for="item in EventTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item required label="触发机制" prop="triggerType">
          <el-input
            v-if="isView"
            :readonly="isView"
            :model-value="formatValue(eventDetail.triggerType, 'triggerType')"
          />
          <el-select v-else v-model="eventDetail.triggerType">
            <el-option
              v-for="item in TriggerOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item required label="对象层级" prop="level">
          <el-input
            v-if="isView"
            :readonly="isView"
            :model-value="formatValue(eventDetail.level, 'level')"
          />
          <el-select v-else v-model="eventDetail.level">
            <el-option
              v-for="item in EventLevelOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item required label="对应位置" prop="owner">
          <el-input
            v-if="isView"
            :readonly="isView"
            :model-value="formatValue(eventDetail.owner, 'owner')"
          />
          <el-select v-else v-model="eventDetail.owner">
            <el-option
              v-for="item in EventOwnerOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="规则说明" prop="ruleDescription">
          <el-input
            :readonly="isView"
            v-model="eventDetail.ruleDescription"
            :rows="4"
            type="textarea"
          />
        </el-form-item>

        <el-form-item label="事件备注" prop="remark">
          <el-input
            :readonly="isView"
            v-model="eventDetail.remark"
            :rows="4"
            type="textarea"
          />
        </el-form-item>
      </el-form>
    </div>
  </el-drawer>
</template>

<style lang="scss" scoped></style>
