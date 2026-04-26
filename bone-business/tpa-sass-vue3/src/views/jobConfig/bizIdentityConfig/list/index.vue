<script setup lang="ts">
import BizIdentityAPI from "@/api/bizIdentity";
import PageAPI from "@/api/page";
import {
  getBizIdentityTypeLabel,
  BizIdentityTypeOptions,
} from "@/enums/BizIdentityTypeEnum";
import {
  getBizIdentityStatusLabel,
  BizIdentityStatusOptions,
} from "@/enums/BizIdentityStatusEnum";
import dayjs from "dayjs";
defineOptions({
  name: "BizIdentityList",
});

const router = useRouter();

const form = ref({
  bizType: "",
  name: "",
  status: "",
});
const tableLoading = ref(false);
const bizIdentityList = ref<any>([]);

const resetForm = () => {
  form.value = {
    bizType: "",
    name: "",
    status: "",
  };
  initBizIdentityList();
};

const pagingParams = ref({
  pageNum: 1,
  pageSize: 20,
  totalSize: 0,
});

const initBizIdentityList = async () => {
  tableLoading.value = true;
  try {
    const res = await BizIdentityAPI.getBizIdentityList({
      ...form.value,
      pageNum: pagingParams.value.pageNum,
      pageSize: pagingParams.value.pageSize,
    });
    bizIdentityList.value = res.rows;
    pagingParams.value.totalSize = res.totalSize;
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};
initBizIdentityList();

const handleDetail = (row: any) => {
  if (!row.createPage) {
    ElMessage.error("该业务主体未创建页面，请先创建页面再进行操作");
    return;
  }

  router.push({
    name: "BizIdentityConfig",
    query: {
      code: row.code,
      name: row.name,
      type: row.type,
    },
  });
};

const handleCreatePage = () => {
  router.push({
    name: "CreateBizIdentity",
  });
};

// const handleDelete = async (row: any) => {
//   ElMessageBox.confirm("确定删除？", "提示", {
//     confirmButtonText: "确定",
//     cancelButtonText: "取消",
//     type: "warning",
//     dangerouslyUseHTMLString: true,
//     closeOnClickModal: false,
//     beforeClose: async (action, instance, done) => {
//       if (action === "confirm") {
//         instance.confirmButtonLoading = true;
//         try {
//           await PageAPI.deleteExclusivePages(row.code);
//           ElMessage.success("删除专属页面成功");
//           initBizIdentityList();
//           done();
//         } catch (error) {
//           console.error(error);
//         } finally {
//           instance.confirmButtonLoading = false;
//         }
//       } else {
//         done();
//       }
//     },
//   });
// };

const handelSizeChange = (size: number) => {
  pagingParams.value.pageNum = 1;
  pagingParams.value.pageSize = size;
  initBizIdentityList();
};

const handelCurrentChange = (page: number) => {
  pagingParams.value.pageNum = page;
  initBizIdentityList();
};
</script>

<template>
  <div class="app-container">
    <el-card class="mb-3" shadow="never">
      <div class="flex justify-between items-center">
        <div class="font-bold">专属页面清单</div>
        <div>
          <el-button type="primary" @click="handleCreatePage">
            创建新主体专属
          </el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <div>
        <el-form label-width="100px" inline>
          <el-form-item label="主体类型">
            <el-select v-model="form.bizType" style="width: 200px">
              <el-option label="全部" value="" />
              <el-option
                v-for="item in BizIdentityTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="主体名称">
            <el-input v-model="form.name" />
          </el-form-item>
          <el-form-item label="页面状态">
            <el-select v-model="form.status" style="width: 200px">
              <el-option label="全部" value="" />
              <el-option
                v-for="item in BizIdentityStatusOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="initBizIdentityList">
              搜索
            </el-button>
            <el-button type="warning" @click="resetForm">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table
        v-adaptive
        v-loading="tableLoading"
        stripe
        :data="bizIdentityList"
        border
        table-layout="auto"
      >
        <el-table-column label="业务主体类型" align="center">
          <template #default="scope">
            <span>
              {{ getBizIdentityTypeLabel(scope.row.type) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="业务主体名称" align="center" prop="name" />
        <el-table-column label="启动状态" align="center" prop="status">
          <template #default="scope">
            <span>{{ getBizIdentityStatusLabel(scope.row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="是否创建专属页面"
          align="center"
          prop="createPage"
        >
          <template #default="scope">
            <span>{{ scope.row.createPage ? "是" : "否" }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近操作人" align="center" prop="updateBy" />
        <el-table-column label="最近操作时间" align="center" prop="updateTime">
          <template #default="{ row }">
            <span>
              {{
                dayjs(row.updateTime).isValid()
                  ? dayjs(row.updateTime).format("YYYY-MM-DD HH:mm:ss")
                  : "--"
              }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="160">
          <template #default="scope">
            <el-button type="primary" link>日志</el-button>
            <el-button type="primary" link @click="handleDetail(scope.row)">
              详情
            </el-button>
            <!-- <el-button type="primary" link @click="handleDelete(scope.row)">
              删除
            </el-button> -->
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
  </div>
</template>

<style lang="scss" scoped></style>
