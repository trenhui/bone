<script setup lang="ts">
import FieldAPI, { ExclusiveField } from "@/api/field";
import { getBaseCompTypeName } from "@/enums/baseComp/BaseCompEnum";
defineOptions({
  name: "ExclusiveField",
});

const props = defineProps({
  bizIdentityCode: {
    type: String,
    default: "",
  },
});

const exclusiveFieldList = ref<ExclusiveField[]>([]);
const loading = ref(false);
const initExclusiveFieldList = async () => {
  try {
    loading.value = true;
    const res = await FieldAPI.getExclusiveFieldList(props.bizIdentityCode);
    exclusiveFieldList.value = res;
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const drawerVisible = ref(false);
const handleCreateExclusiveField = () => {
  drawerVisible.value = true;
};

onMounted(() => {
  initExclusiveFieldList();
});
</script>

<template>
  <div>
    <div class="flex justify-between items-center">
      <div class="text-sm text-gray-500">
        统一管理本主体下的所有专属字段，默认适用于所有环节页面，可编辑默认属性和规则。具体页面设置后，则以具体页面的设置为准。
      </div>
      <el-button type="primary" @click="handleCreateExclusiveField">
        创建专属字段
      </el-button>
    </div>

    <el-table
      v-loading="loading"
      class="mt-4"
      :data="exclusiveFieldList"
      border
    >
      <el-table-column label="数据模型" prop="modelName" align="center" />
      <el-table-column label="专属字段名称" prop="fieldName" align="center" />
      <el-table-column label="字段标识" prop="fieldCode" align="center" />
      <el-table-column label="组件类型" prop="componentType" align="center">
        <template #default="scope">
          {{ getBaseCompTypeName(scope.row.componentType) }}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" prop="createTime" align="center" />
      <el-table-column label="创建人员" prop="createBy" align="center" />
      <el-table-column label="最近更新" prop="updateTime" align="center" />
      <el-table-column label="更新人员" prop="updateBy" align="center" />
    </el-table>

    <CreateExclusiveFieldDrawer
      v-model="drawerVisible"
      :bizIdentityCode="bizIdentityCode"
      @close="(isChange: boolean) => isChange && initExclusiveFieldList()"
    />
  </div>
</template>

<style lang="scss" scoped></style>
