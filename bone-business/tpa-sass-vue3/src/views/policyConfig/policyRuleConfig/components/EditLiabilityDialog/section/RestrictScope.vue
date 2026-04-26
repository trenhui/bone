<script setup lang="ts">
import { LiabilityDTO } from "@/api/liability";
import { PropType } from "vue";
defineOptions({
  name: "RestrictScope",
});

const liability = defineModel("liability", {
  type: Object as PropType<LiabilityDTO>,
  required: true,
});

const isRestrictScope = computed({
  get() {
    return liability.value?.restrictScope !== null;
  },
  set(value) {
    if (!value) {
      liability.value.restrictScope = null;
    } else {
      liability.value.restrictScope = [];
    }
  },
});

const handleAdd = () => {
  if (!liability.value.restrictScope) {
    liability.value.restrictScope = [];
  }
  liability.value?.restrictScope?.push({
    restrictRange: "",
    open: false,
    type: "",
    restrictList: [],
  });
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm("确定删除该行？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      liability.value?.restrictScope?.splice(
        liability.value?.restrictScope?.indexOf(row),
        1
      );
    })
    .catch(() => {});
};
</script>

<template>
  <el-form-item label="适用限定">
    <el-radio-group v-model="isRestrictScope">
      <el-radio :value="false">不限定</el-radio>
      <el-radio :value="true" disabled>启用限定</el-radio>
    </el-radio-group>
  </el-form-item>

  <el-form-item class="w-70%" v-if="isRestrictScope">
    <div>开启限定后，根据启用的「范围限定」的内容确定责任是否可赔</div>
    <el-table border :data="liability.restrictScope || []">
      <el-table-column label="范围限定" align="center">
        <template #default="{ row }">
          <el-select v-model="row.restrictRange">
            <el-option label="医院限定" value="HOSPITAL" />
            <el-option label="药品限定" value="DRUG" />
            <el-option label="诊疗限定" value="DIAGNOSE" />
            <el-option label="病种限定" value="DISEASE" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="是否启用" align="center">
        <template #default="{ row }">
          <el-switch v-model="row.open" />
        </template>
      </el-table-column>
      <el-table-column label="限定方式" align="center">
        <template #default="{ row }">
          <el-select v-model="row.type">
            <el-option label="白名单" value="WHITE" />
            <el-option label="黑名单" value="BLACK" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="限定清单" align="center">
        <template #default>暂无</template>
      </el-table-column>
      <el-table-column label="操作" align="center">
        <template #default="{ row }">
          <el-button type="danger" link @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-button type="primary" link class="mt-2" @click="handleAdd">
      新增限定
    </el-button>
  </el-form-item>
</template>

<style lang="scss" scoped></style>
