<script setup lang="ts">
import { LiabilityDTO, RangeObject } from "@/api/liability";
defineOptions({
  name: "RestrictObject",
});

const liability = defineModel("liability", {
  type: Object as PropType<LiabilityDTO>,
  required: true,
});

const isRestrictObject = computed({
  get() {
    return liability.value?.restrictObject !== null;
  },
  set(value) {
    if (!value) {
      liability.value.restrictObject = null;
    } else {
      liability.value.restrictObject = {
        gender: null,
        age: null,
        occupation: null,
        other: null,
      };
    }
  },
});

const gender = computed({
  get() {
    // 返回一个安全的字符串值，而不是 null
    return liability.value?.restrictObject?.gender || "null";
  },
  set(value) {
    // 将 "null" 转回 null
    const newValue = value === "null" ? null : value;
    if (!liability.value?.restrictObject) {
      liability.value.restrictObject = {
        gender: newValue,
        age: null,
        occupation: null,
        other: null,
      };
    } else {
      liability.value.restrictObject.gender = newValue;
    }
  },
});

const occupation = computed({
  get() {
    if (!liability.value?.restrictObject?.occupation) {
      return [];
    }
    return liability.value.restrictObject.occupation;
  },
  set(value) {
    if (!liability.value?.restrictObject) {
      liability.value.restrictObject = {
        gender: null,
        age: null,
        occupation: value,
        other: null,
      };
    } else {
      liability.value.restrictObject.occupation = value;
    }
  },
});

const other = computed({
  get() {
    // 确保返回字符串类型
    return liability.value?.restrictObject?.other || "";
  },
  set(value) {
    if (!liability.value?.restrictObject) {
      liability.value.restrictObject = {
        gender: null,
        age: null,
        occupation: null,
        other: value,
      };
    } else {
      // 保存实际值，而不转换为 null
      liability.value.restrictObject.other = value;
    }
  },
});

// 处理年龄类型选择和年龄区间设置
const ageType = computed({
  get() {
    return liability.value?.restrictObject?.age ? 1 : -1;
  },
  set(value) {
    if (value === -1) {
      if (liability.value?.restrictObject) {
        liability.value.restrictObject.age = null;
      }
    } else {
      // 如果从"不限"切换到"年龄段"，初始化age对象
      if (!liability.value?.restrictObject?.age) {
        if (!liability.value?.restrictObject) {
          liability.value.restrictObject = {
            gender: null,
            age: {
              lowerLimit: 0,
              upperLimit: 100,
              intervalType: "BOTH_CLOSED",
            },
            occupation: null,
            other: null,
          };
        } else {
          liability.value.restrictObject.age = {
            lowerLimit: 0,
            upperLimit: 100,
            intervalType: "BOTH_CLOSED",
          };
        }
      }
    }
  },
});

// 为年龄区间创建一个额外的计算属性来处理嵌套对象
const ageRange = computed({
  get() {
    if (!liability.value?.restrictObject?.age) {
      return {
        lowerLimit: 0,
        upperLimit: 100,
        intervalType: "BOTH_CLOSED",
      };
    }
    return liability.value.restrictObject.age;
  },
  set(value) {
    // 确保 age 对象存在
    if (!liability.value?.restrictObject?.age) {
      if (!liability.value?.restrictObject) {
        liability.value.restrictObject = {
          gender: null,
          age: value,
          occupation: null,
          other: null,
        };
      } else {
        liability.value.restrictObject.age = value;
      }
    } else {
      // 更新现有的 age 对象
      liability.value.restrictObject.age = value;
    }
  },
});

const occupationType = computed({
  get() {
    return liability.value?.restrictObject?.occupation ? 1 : -1;
  },
  set(value) {
    if (value === -1) {
      if (liability.value?.restrictObject) {
        liability.value.restrictObject.occupation = null;
      }
    } else {
      // 如果从"不限"切换到"指定"，初始化空数组
      if (!liability.value?.restrictObject?.occupation) {
        if (!liability.value?.restrictObject) {
          liability.value.restrictObject = {
            gender: null,
            age: null,
            occupation: [],
            other: null,
          };
        } else {
          liability.value.restrictObject.occupation = [];
        }
      }
    }
  },
});

const otherType = computed({
  get() {
    // 更精确的判断方式，检查是否有其他字段，不依赖其值是否为空
    return liability.value?.restrictObject?.other !== null &&
      liability.value?.restrictObject?.other !== undefined
      ? 1
      : -1;
  },
  set(value) {
    if (value === -1) {
      if (liability.value?.restrictObject) {
        liability.value.restrictObject.other = null;
      }
    } else {
      // 如果从"无"切换到"说明"，初始化为空字符串
      if (!liability.value?.restrictObject) {
        liability.value.restrictObject = {
          gender: null,
          age: null,
          occupation: null,
          other: "",
        };
      } else {
        // 确保设置一个空字符串，而不是依赖原有值
        liability.value.restrictObject.other =
          liability.value.restrictObject.other !== null
            ? liability.value.restrictObject.other
            : "";
      }
    }
  },
});
</script>

<template>
  <div>
    <el-form-item label="适用对象">
      <el-radio-group v-model="isRestrictObject">
        <el-radio :value="false">不限</el-radio>
        <el-radio :value="true">指定对象</el-radio>
      </el-radio-group>
    </el-form-item>

    <div class="ml-10" v-if="isRestrictObject">
      <el-form-item label="性别">
        <el-radio-group v-model="gender">
          <el-radio value="null">不限</el-radio>
          <el-radio value="女">女</el-radio>
          <el-radio value="男">男</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 年龄段 -->
      <el-form-item label="年龄">
        <el-radio-group v-model="ageType">
          <el-radio :value="-1">不限</el-radio>
          <el-radio :value="1">年龄段</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="ageType === 1" class="w-50%">
        <el-table border :data="[0]">
          <el-table-column label="年龄起" align="center">
            <template #default>
              <el-input-number
                v-model="ageRange.lowerLimit"
                :min="0"
                :max="ageRange.upperLimit"
                controls-position="right"
                :style="{ width: '100%' }"
              />
            </template>
          </el-table-column>
          <el-table-column label="年龄止" align="center">
            <template #default>
              <el-input-number
                v-model="ageRange.upperLimit"
                :min="ageRange.lowerLimit"
                controls-position="right"
                :style="{ width: '100%' }"
              />
            </template>
          </el-table-column>
          <el-table-column label="起止类型" align="center">
            <template #default>
              <el-select v-model="ageRange.intervalType" placeholder="请选择">
                <el-option label="左开右闭" value="LEFT_OPEN" />
                <el-option label="左闭右开" value="RIGHT_OPEN" />
                <el-option label="左开右开" value="BOTH_OPEN" />
                <el-option label="左闭右闭" value="BOTH_CLOSED" />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>

      <!-- 职业 -->
      <el-form-item label="职业">
        <el-radio-group v-model="occupationType">
          <el-radio :value="-1">不限</el-radio>
          <el-radio :value="1">指定</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item class="w-50%" v-if="occupationType === 1">
        <el-select v-model="occupation" placeholder="请选择职业" multiple>
          <el-option label="程序员" value="programmer" />
          <el-option label="设计师" value="designer" />
        </el-select>
      </el-form-item>

      <!-- 其他 -->
      <el-form-item label="其他">
        <el-radio-group v-model="otherType">
          <el-radio :value="-1">无</el-radio>
          <el-radio :value="1">说明</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item class="w-50%" v-if="otherType === 1">
        <el-input v-model="other" />
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
