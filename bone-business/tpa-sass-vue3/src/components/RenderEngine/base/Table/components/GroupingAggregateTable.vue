<script setup lang="ts">
import { GroupingAggregateTypeEnum } from "@/enums/table/GroupingAggregateTypeEnum";
import { aggregateGroup } from "@/utils/aggregation/index";
import { getValueByJsonPath } from "@/utils/jsonpathUtils";
defineOptions({
  name: "GroupingAggregateTable",
});

const props = defineProps<{
  rule: any;
  data: any;
  componentManager: any;
}>();

// 设置表格和表格列的宽度
const defaultWidth = 200;
const tableMinWidth = computed(() => {
  return props.rule.fieldList.length * defaultWidth;
});

const tableData = ref<any[]>([]);

// 监听数据变化
watchDebounced(
  () => props.data,
  (newVal) => {
    if (newVal && newVal.length > 0) {
      tableData.value = initTableData();
    } else {
      tableData.value = [];
    }
  },
  { immediate: true, deep: true, debounce: 500, maxWait: 2000 }
);

const initTableData = () => {
  const { data, rule } = props;
  const fieldList = mapAvailableFields(rule.fieldList);

  const { groupField, aggregateFields, otherFields } =
    categorizeFields(fieldList);

  if (!groupField) {
    return [];
  }

  const groupData = groupingData(data, groupField);

  return processGroupData(groupData, groupField, aggregateFields, otherFields);
};

//过滤规则字段中不在body中的字段（只取页面中有的字段）
const mapAvailableFields = (ruleFieldList: any[]) => {
  return ruleFieldList
    .map((field: any) => {
      const fieldItem = props.componentManager.get(field.id);
      if (fieldItem) {
        return {
          ...field,
          dataBinding: fieldItem.dataBinding,
        };
      }
      return null;
    })
    .filter((field: any) => field !== null);
};

// 将字段分类
const categorizeFields = (fieldList: any[]) => {
  return {
    groupField: fieldList.find(
      (item: any) => item.type === GroupingAggregateTypeEnum.GROUP
    ),
    aggregateFields: fieldList.filter(
      (item: any) => item.type === GroupingAggregateTypeEnum.AGGREGATE
    ),
    otherFields: fieldList.filter(
      (item: any) => item.type === GroupingAggregateTypeEnum.OTHER
    ),
  };
};

//将数据根据分组字段进行分组
const groupingData = (data: any[], groupField: any) => {
  const groupData = new Map();
  data.forEach((item: any) => {
    const groupKey = getValueByJsonPath(item, groupField.dataBinding);
    if (!groupData.has(groupKey)) {
      groupData.set(groupKey, []);
    }
    groupData.get(groupKey)?.push(item);
  });
  return groupData;
};

// 处理分组数据, 生成新的聚合数据
const processGroupData = (
  groupData: Map<any, any>,
  groupField: any,
  aggregateFields: any[],
  otherFields: any[]
) => {
  const newData: any[] = [];
  for (const [groupKey, group] of groupData) {
    const newGroup: any = {
      [groupField.bizCode]: groupKey,
      ...processAggregateFields(group, aggregateFields),
      ...processOtherFields(group, otherFields),
    };
    newData.push(newGroup);
  }
  return newData;
};

// 根据不同的聚合方法, 处理聚合字段
const processAggregateFields = (group: any[], aggregateFields: any[]) => {
  const result: any = {};
  aggregateFields.forEach((field: any) => {
    const values = group.map((data: any) =>
      getValueByJsonPath(data, field.dataBinding)
    );
    result[field.bizCode] =
      aggregateGroup(values, field.groupAggregateType) ?? "";
  });
  return result;
};

// 处理其他字段, 直接将值拼接
const processOtherFields = (group: any[], otherFields: any[]) => {
  const result: any = {};
  otherFields.forEach((field: any) => {
    const values = group.map((data: any) =>
      getValueByJsonPath(data, field.dataBinding)
    );
    // 值需要去重拼接
    result[field.bizCode] = [...new Set(values)].join("、");
  });
  return result;
};
</script>

<template>
  <div>
    <p
      class="text-sm font-bold text-[var(--el-text-color-secondary)] mb-2 arrow-right"
    >
      {{ rule.name }}
    </p>
    <div :style="{ width: tableMinWidth + 'px', maxWidth: '100%' }">
      <el-table border :data="tableData">
        <transition-group
          enter-active-class="animate__animated animate__fadeIn animate__faster"
          leave-active-class="animate__animated animate__fadeOut animate__faster"
        >
          <template v-for="item in rule.fieldList" :key="item.id">
            <el-table-column
              :prop="item.bizCode"
              :label="item.bizName"
              :width="defaultWidth"
              align="center"
            />
          </template>
        </transition-group>
      </el-table>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.arrow-right {
  position: relative;
  padding-left: 16px;

  &::before {
    position: absolute;
    top: 50%;
    left: 0;
    width: 0;
    height: 0;
    content: "";
    border-top: 6px solid transparent;
    border-bottom: 6px solid transparent;
    border-left: 8px solid var(--el-color-primary);
    transform: translateY(-50%);
  }
}
</style>
