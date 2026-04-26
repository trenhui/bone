<script setup lang="ts">
import { DisplayLevelOptions } from "@/enums/event/DisplayLevelEnum";
import { DisplayTypeOptions } from "@/enums/event/DisplayTypeEnum";
import { EventOwnerEnum } from "@/enums/event/EventOwnerEnum";
import { useModalLockScroll } from "@/hooks/common/useModalLockScroll";
import EventAPI from "@/api/event";
import { ElForm } from "element-plus";
defineOptions({
  name: "CreateEventButtonDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  id?: string;
  owner?: EventOwnerEnum;
}>();

const form = ref({
  label: "",
  style: "",
  displayType: "",
  eventId: "",
  owner: props.owner,
  ownerId: props.id,
});
const rules = {
  label: [{ required: true, message: "请输入展示标题" }],
  style: [{ required: true, message: "请选择展示颜色" }],
  displayType: [{ required: true, message: "请选择展示形式" }],
  eventId: [{ required: true, message: "请选择绑定事件" }],
};

const confirmLoading = ref(false);
const isChange = ref(false);
const eventList = ref<any>([]);
const formRef = ref<InstanceType<typeof ElForm> | null>(null);

const resetForm = () => {
  form.value = {
    label: "",
    style: "",
    displayType: "",
    eventId: "",
    owner: props.owner,
    ownerId: props.id,
  };
  if (formRef.value) {
    formRef.value.resetFields();
  }
};

watch(
  () => props.id,
  () => {
    resetForm();
  },
  { immediate: true }
);

const initEventList = async () => {
  try {
    const res = await EventAPI.list({
      owner: props.owner,
      pageNum: 1,
      pageSize: 9999,
    });
    eventList.value = res.rows;
  } catch (error) {
    console.error(error);
  }
};
initEventList();

const handleEventChange = (eventId: string) => {
  form.value.eventId = eventId;
  form.value.label =
    eventList.value.find((item: any) => item.id === eventId)?.name || "";
};

const handleClose = () => {
  dialogVisible.value = false;
  resetForm();
  emits("close", isChange.value);
  isChange.value = false;
};

const handleConfirm = async () => {
  if (formRef.value) {
    const valid = await formRef.value.validate();
    if (!valid) return;
  }

  confirmLoading.value = true;
  try {
    await EventAPI.createEventTrigger(form.value);
    ElMessage.success("创建成功");
    isChange.value = true;
    handleClose();
  } catch (error) {
    console.error(error);
  } finally {
    confirmLoading.value = false;
  }
};

useModalLockScroll(dialogVisible);
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="创建事件按钮"
    width="40%"
    :close-on-click-modal="false"
  >
    <div class="px-4 py-2">
      <el-form ref="formRef" :model="form" :rules="rules">
        <el-form-item required label="绑定事件" prop="eventId">
          <el-select
            v-model="form.eventId"
            @change="handleEventChange"
            placeholder="请选择事件"
          >
            <el-option
              v-for="item in eventList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item required label="展示标题" prop="label">
          <el-input v-model="form.label" placeholder="请输入展示标题" />
        </el-form-item>
        <el-form-item required label="展示颜色" prop="style">
          <el-select v-model="form.style" placeholder="请选择展示颜色">
            <el-option
              v-for="item in DisplayLevelOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            >
              <div class="flex items-center">
                <div
                  class="w-4 h-4 mr-2 rounded border border-light-8"
                  :style="{ backgroundColor: item.color }"
                ></div>

                <span>{{ item.label }}</span>
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item required label="展示形式" prop="displayType">
          <el-select v-model="form.displayType" placeholder="请选择展示形式">
            <el-option
              v-for="item in DisplayTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
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

<style lang="scss" scoped></style>
