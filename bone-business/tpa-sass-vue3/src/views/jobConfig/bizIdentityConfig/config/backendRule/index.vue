<script setup lang="ts">
import * as BackendRuleAPI from "@/api/rule/backendRule";

const props = defineProps({
  bizIdentityCode: {
    type: String,
    default: "",
  },
});

const tableLoading = ref(false);
const backendRuleList = ref<BackendRuleAPI.BackendRule[]>([]);

const initBackendRuleList = async () => {
  try {
    tableLoading.value = true;
    backendRuleList.value = await BackendRuleAPI.getBackendRuleList(
      props.bizIdentityCode
    );
  } catch (error: any) {
    console.log(error.message);
  } finally {
    tableLoading.value = false;
  }
};

onMounted(() => {
  initBackendRuleList();
});

//修改规则状态
const handleStatusChange = async (row: BackendRuleAPI.BackendRule) => {
  try {
    await BackendRuleAPI.saveBackendRule({
      bizIdentityCode: props.bizIdentityCode,
      configList: backendRuleList.value.map((item) => {
        if (item.beanName === row.beanName) {
          return {
            ...item,
            status: item.status === 1 ? 0 : 1,
          };
        }
        return item;
      }),
    });
    ElMessage.success("修改成功");
    initBackendRuleList();
  } catch (error: any) {
    console.log(error.message);
  }
};
</script>

<template>
  <div class="tab-pane">
    <el-table
      ref="tableRef"
      v-loading="tableLoading"
      border
      :data="backendRuleList"
    >
      <el-table-column label="序号" align="center" width="60px">
        <template #default="scope">{{ scope.$index + 1 }}</template>
      </el-table-column>

      <el-table-column label="所属领域" align="center">
        <template #default="scope">{{ scope.row.domainDesc }}</template>
      </el-table-column>

      <el-table-column label="细分功能领域" align="center">
        <template #default="scope">{{ scope.row.beanTypeDesc }}</template>
      </el-table-column>

      <el-table-column label="规则描述" align="center">
        <template #default="scope">{{ scope.row.beanDesc }}</template>
      </el-table-column>

      <el-table-column label="状态" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'">
            {{ scope.row.status === 1 ? "启用" : "禁用" }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="150" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleStatusChange(row)">
            {{ row.status === 1 ? "禁用" : "启用" }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style lang="scss" scoped></style>
