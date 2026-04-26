<script setup lang="ts">
import OptionConfigAPI from "@/api/systemManage/optionConfig";
import CreateDialog from "./components/CreateDialog.vue";
import DetailDialog from "./components/DetailDialog.vue";
import VersionListDialog from "./components/VersionListDialog.vue";
import { formatDate } from "@/utils/date";

defineOptions({
  name: "OptionConfig",
});

const optionList = ref<any>([]);
const tableLoading = ref(false);

const pagingParams = ref({
  pageNum: 1,
  pageSize: 20,
  totalSize: 0,
});

const getOptionList = async () => {
  tableLoading.value = true;
  try {
    const res = await OptionConfigAPI.getOptionList(
      pagingParams.value.pageNum,
      pagingParams.value.pageSize
    );
    optionList.value = res.rows;
    pagingParams.value.totalSize = res.totalSize;
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};
getOptionList();

const detailDialog = ref({
  isVisible: false,
  params: { id: "", isCreate: false },
  onClose: (isChange: boolean = false) => {
    detailDialog.value.isVisible = false;
    detailDialog.value.params = { id: "", isCreate: false };
    if (isChange) {
      getOptionList();
    }
  },
});

const createDialog = ref({
  isVisible: false,
  onClose: (isChange: boolean = false) => {
    createDialog.value.isVisible = false;
    if (isChange) {
      getOptionList();
    }
  },
});

const versionListDialog = ref({
  isVisible: false,
  params: { id: "" },
  onClose: () => {
    versionListDialog.value.isVisible = false;
    versionListDialog.value.params = { id: "" };
  },
});

const handleDetail = (row: any) => {
  detailDialog.value.isVisible = true;
  detailDialog.value.params = { id: row.id, isCreate: false };
};

const handleCreate = () => {
  createDialog.value.isVisible = true;
};

const handlePublish = (row: any) => {
  ElMessageBox.prompt("请输入版本描述", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
  })
    .then(async ({ value }) => {
      await OptionConfigAPI.addOptionSetVersion(row.id, value);
      ElMessage.success("发布成功");
      getOptionList();
    })
    .catch(() => {});
};

const handleVersion = (row: any) => {
  versionListDialog.value.isVisible = true;
  versionListDialog.value.params = { id: row.id };
};

const handelSizeChange = (size: number) => {
  pagingParams.value.pageNum = 1;
  pagingParams.value.pageSize = size;
  getOptionList();
};

const handelCurrentChange = (page: number) => {
  pagingParams.value.pageNum = page;
  getOptionList();
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm("请确认是否删除该选项集", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  }).then(() => {
    OptionConfigAPI.deleteOptionSet(row.id)
      .then(() => {
        ElMessage.success("删除成功");
        getOptionList();
      })
      .catch((error) => {
        console.error(error);
      });
  });
};
</script>

<template>
  <div class="app-container">
    <el-card class="mb-2" shadow="never">
      <div class="flex justify-between items-center">
        <div class="font-bold">系统选项列表</div>
      </div>
    </el-card>

    <el-card shadow="never">
      <div class="flex justify-end items-center mb-3">
        <el-button type="primary" @click="handleCreate" size="small">
          创建选项集
        </el-button>
      </div>
      <el-table
        v-adaptive
        v-loading="tableLoading"
        stripe
        :data="optionList"
        border
      >
        <el-table-column label="序号" width="60" type="index" align="center" />
        <el-table-column
          label="适用范围"
          align="center"
          prop="useScope"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ scope.row.useScope == 1 ? "通用" : "无" }}
          </template>
        </el-table-column>
        <el-table-column
          label="选项集名称"
          align="center"
          prop="setName"
          show-overflow-tooltip
        />
        <el-table-column
          label="选项集标识"
          align="center"
          prop="setCode"
          show-overflow-tooltip
        />
        <el-table-column
          label="描述"
          align="center"
          prop="setDesc"
          show-overflow-tooltip
        />
        <!-- <el-table-column
          label="最新版本"
          align="center"
          prop="lastVersion"
          width="100"
        /> -->
        <el-table-column
          label="更新时间"
          align="center"
          prop="updateTime"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ formatDate(scope.row.updateTime) }}
          </template>
        </el-table-column>
        <el-table-column
          label="最近操作人"
          align="center"
          prop="updateBy"
          show-overflow-tooltip
        />
        <el-table-column
          label="创建时间"
          align="center"
          prop="createTime"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ formatDate(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column
          label="创建操作人"
          align="center"
          prop="createBy"
          show-overflow-tooltip
        />
        <el-table-column label="操作" align="center" width="140" fixed="right">
          <template #default="scope">
            <el-button type="primary" link @click="handleDetail(scope.row)">
              详情
            </el-button>
            <!-- <el-button type="primary" link @click="handlePublish(scope.row)">
              发布
            </el-button>
            <el-button type="primary" link @click="handleVersion(scope.row)">
              版本记录
            </el-button> -->
            <el-button type="primary" link @click="handleDelete(scope.row)">
              删除
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

    <detail-dialog
      v-model="detailDialog.isVisible"
      v-bind="detailDialog.params"
      @close="detailDialog.onClose"
    />

    <version-list-dialog
      v-model="versionListDialog.isVisible"
      v-bind="versionListDialog.params"
      @close="versionListDialog.onClose"
    />

    <create-dialog
      v-model="createDialog.isVisible"
      @close="createDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
