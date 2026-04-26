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
  DevStatusEnum,
  DevStatusOptions,
  getDevStatusLabel,
} from "@/enums/event/DevStatusEnum";
import EventDetailDrawer from "./components/EventDetailDrawer/index.vue";
import EventAPI from "@/api/event";
import type { ElSelect } from "element-plus";
import dayjs from "dayjs";
defineOptions({
  name: "EventManage",
});

const router = useRouter();

const form = ref({
  name: "",
  devStatus: "",
  type: "",
  triggerType: "",
  level: "",
});
const eventList = ref<any>([]);
const tableLoading = ref(false);

const handleCreateEvent = () => {
  router.push({
    name: "CreateEvent",
  });
};

const resetForm = () => {
  form.value = {
    name: "",
    devStatus: "",
    type: "",
    triggerType: "",
    level: "",
  };
  initEventList();
};

const pagingParams = ref({
  pageNum: 1,
  pageSize: 20,
  totalSize: 0,
});

const initEventList = async () => {
  tableLoading.value = true;
  try {
    const res = await EventAPI.list({
      ...form.value,
      ...pagingParams.value,
    });
    eventList.value = res.rows;
    pagingParams.value.totalSize = res.totalSize;
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};
resetForm();

// 编辑开发状态
const editingDevStatusRowId = ref<string | null>(null);
const devStatusSelectRef = ref<InstanceType<typeof ElSelect> | null>(null);
const handleDevStatusDoubleClick = (row: any) => {
  editingDevStatusRowId.value = row.id;
  nextTick(() => {
    devStatusSelectRef.value?.focus();
  });
};
const handleDevStatusBlur = async (row: any) => {
  try {
    await EventAPI.update({
      id: row.id,
      devStatus: row.devStatus,
    });
    editingDevStatusRowId.value = null;
    ElMessage.success("更新成功");
    initEventList();
  } catch (error: any) {
    console.error(error.message);
  }
};

// 事件详情弹窗
const closeEventDetailDrawer = (isChange: boolean) => {
  eventDetailDrawer.value.visible = false;
  eventDetailDrawer.value.params = { id: "" };
  if (isChange) {
    initEventList();
  }
};
const eventDetailDrawer = ref({
  visible: false,
  params: { id: "" },
  onClose: closeEventDetailDrawer,
});
const handleEventDetail = (row: any) => {
  eventDetailDrawer.value.visible = true;
  eventDetailDrawer.value.params["id"] = row.id;
};

const handelSizeChange = (size: number) => {
  pagingParams.value.pageNum = 1;
  pagingParams.value.pageSize = size;
  initEventList();
};

const handelCurrentChange = (page: number) => {
  pagingParams.value.pageNum = page;
  initEventList();
};
</script>

<template>
  <div class="app-container">
    <el-card class="mb-3" shadow="never">
      <div class="flex justify-between items-center">
        <div class="font-bold">事件列表</div>
        <div>
          <el-button type="primary" @click="handleCreateEvent">
            创建新事件
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <div>
        <el-form label-width="auto" inline>
          <el-form-item label="事件名称">
            <el-input v-model="form.name" style="width: 200px" />
          </el-form-item>
          <el-form-item label="开发状态">
            <el-select v-model="form.devStatus" style="width: 200px">
              <el-option label="全部" value="" />
              <el-option
                v-for="item in DevStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="事件类型">
            <el-select v-model="form.type" style="width: 200px">
              <el-option label="全部" value="" />
              <el-option
                v-for="item in EventTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="触发机制">
            <el-select v-model="form.triggerType" style="width: 200px">
              <el-option label="全部" value="" />
              <el-option
                v-for="item in TriggerOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="对象层级">
            <el-select v-model="form.level" style="width: 200px">
              <el-option label="全部" value="" />
              <el-option
                v-for="item in EventLevelOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="initEventList">搜索</el-button>
            <el-button type="warning" @click="resetForm">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table
        v-adaptive
        v-loading="tableLoading"
        stripe
        :data="eventList"
        border
      >
        <el-table-column label="事件名称" align="center" prop="name" />
        <el-table-column label="事件类型" align="center">
          <template #default="{ row }">
            {{ getEventTypeLabel(row.type) }}
          </template>
        </el-table-column>
        <el-table-column label="触发机制" align="center">
          <template #default="{ row }">
            {{ getTriggerLabel(row.triggerType) }}
          </template>
        </el-table-column>
        <el-table-column label="对象层级" align="center" prop="level">
          <template #default="{ row }">
            {{ getEventLevelLabel(row.level) }}
          </template>
        </el-table-column>
        <el-table-column label="开发状态" align="center" prop="devStatus">
          <template #default="{ row }">
            <el-tag
              :type="row.devStatus === DevStatusEnum.done ? 'success' : 'info'"
              v-if="editingDevStatusRowId !== row.id"
              @dblclick="handleDevStatusDoubleClick(row)"
            >
              {{ getDevStatusLabel(row.devStatus) }}
            </el-tag>
            <el-select
              v-else
              ref="statusSelectRef"
              v-model="row.devStatus"
              @blur="handleDevStatusBlur(row)"
            >
              <el-option
                v-for="item in DevStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center">
          <template #default="{ row }">
            <span>
              {{
                dayjs(row.createTime).isValid()
                  ? dayjs(row.createTime).format("YYYY-MM-DD HH:mm:ss")
                  : "--"
              }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="启用时间" align="center">
          <template #default="{ row }">
            <span>
              {{
                dayjs(row.enableTime).isValid()
                  ? dayjs(row.enableTime).format("YYYY-MM-DD HH:mm:ss")
                  : "--"
              }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="160">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEventDetail(row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end items-center mt-3">
        <el-pagination
          v-model:current-page="pagingParams.pageNum"
          v-model:page-size="pagingParams.pageSize"
          :total="pagingParams.totalSize"
          @size-change="handelSizeChange"
          @current-change="handelCurrentChange"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[5, 10, 20, 30, 40, 50]"
          background
        />
      </div>
    </el-card>

    <event-detail-drawer
      v-model="eventDetailDrawer.visible"
      v-bind="eventDetailDrawer.params"
      @close="eventDetailDrawer.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
