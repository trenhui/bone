<script setup lang="ts">
import { InvoiceFeeType } from "@/api/liability";
defineOptions({
  name: "SetExpressionDialog",
});

type InputType =
  | "field"
  | "function"
  | "operator"
  | "leftBracket"
  | "rightBracket"
  | "comma";

const operatorOptions = ref<{ label: string; value: string }[]>([
  {
    label: "加法(+)",
    value: "+",
  },
  {
    label: "减法(-)",
    value: "-",
  },
  {
    label: "乘法(*)",
    value: "*",
  },
  {
    label: "除法(/)",
    value: "/",
  },
  {
    label: "左括号(()",
    value: "(",
  },
  {
    label: "右括号())",
    value: ")",
  },
  {
    label: "最大值(Max)",
    value: "Max",
  },
  {
    label: "最小值(Min)",
    value: "Min",
  },
  {
    label: "逗号",
    value: ",",
  },
]);

const emits = defineEmits(["close", "confirm"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  invoiceFeeTypes: InvoiceFeeType[];
  oldExpression?: string;
  name: string;
}>();

const expressionStack = ref<string[]>([]);
const expressionError = ref("");

// 解析表达式字符串到表达式栈
const parseExpressionString = (expressionStr: string) => {
  if (!expressionStr) return;

  const result: string[] = [];
  let i = 0;

  while (i < expressionStr.length) {
    // 处理函数名 (Max, Min)
    if (
      expressionStr.substring(i, i + 3) === "Max" ||
      expressionStr.substring(i, i + 3) === "Min"
    ) {
      result.push(expressionStr.substring(i, i + 3));
      i += 3;
      continue;
    }

    // 处理操作符、括号和逗号
    if (["+", "-", "*", "/", "(", ")", ","].includes(expressionStr[i])) {
      result.push(expressionStr[i]);
      i++;
      continue;
    }

    // 处理变量名/数字 (包括中文、字母、数字和下划线)
    // 使用更通用的方法来判断：如果不是空格、操作符、括号、逗号，就视为变量名的一部分
    if (![" ", "+", "-", "*", "/", "(", ")", ","].includes(expressionStr[i])) {
      let value = "";
      while (
        i < expressionStr.length &&
        ![" ", "+", "-", "*", "/", "(", ")", ","].includes(expressionStr[i])
      ) {
        value += expressionStr[i];
        i++;
      }
      result.push(value);
      continue;
    }

    // 跳过空格
    if (expressionStr[i] === " ") {
      i++;
      continue;
    }

    // 遇到无法识别的字符
    console.error(`无法识别的字符: ${expressionStr[i]} 在位置 ${i}`);
    i++;
  }

  return result;
};

// 接收外部表达式字符串并解析
const initWithExpressionString = (expressionStr: string) => {
  const parsedStack = parseExpressionString(expressionStr);
  if (parsedStack && parsedStack.length > 0) {
    expressionStack.value = parsedStack;
  }
};

watch(
  () => props.oldExpression,
  (newVal) => {
    if (newVal) {
      initWithExpressionString(newVal);
    }
  },
  { immediate: true }
);

const isFunction = (value: string) => ["Max", "Min"].includes(value);
const isOperator = (value: string) => ["+", "-", "*", "/"].includes(value);
const isLeftBracket = (value: string) => value === "(";
const isRightBracket = (value: string) => value === ")";
const isComma = (value: string) => value === ",";

const getCurrentContext = (): {
  isInFunction: boolean;
  isInInnerBracket: boolean;
  isInBracket: boolean;
} => {
  let isInFunction = false;
  let isInInnerBracket = false;
  let isInBracket = false;

  let functionStack: number[] = [];
  let bracketStack: number[] = [];
  let innerBracketStack: number[] = []; // 记录每个函数内的普通括号数量

  for (let i = 0; i < expressionStack.value.length; i++) {
    const item = expressionStack.value[i];

    if (isFunction(item)) {
      functionStack.push(i);
      innerBracketStack.push(0); // 新函数的内部括号计数初始化为0
      isInFunction = true;
    } else if (isLeftBracket(item)) {
      if (i > 0 && isFunction(expressionStack.value[i - 1])) {
        // 函数的开始括号，不计入任何括号计数
        continue;
      } else if (functionStack.length > 0) {
        // 函数内的普通括号
        innerBracketStack[innerBracketStack.length - 1]++;
      } else {
        // 函数外的普通括号
        bracketStack.push(i);
      }
    } else if (isRightBracket(item)) {
      if (functionStack.length > 0) {
        let lastFunctionIndex = functionStack[functionStack.length - 1];

        // 如果这个右括号紧跟在函数的参数后面，且能与函数的左括号配对
        if (
          expressionStack.value[lastFunctionIndex + 1] === "(" &&
          innerBracketStack[innerBracketStack.length - 1] === 0
        ) {
          // 函数的结束括号
          functionStack.pop();
          innerBracketStack.pop();
          if (functionStack.length === 0) {
            isInFunction = false;
          }
        } else if (innerBracketStack[innerBracketStack.length - 1] > 0) {
          // 函数内的普通右括号
          innerBracketStack[innerBracketStack.length - 1]--;
        }
      } else if (bracketStack.length > 0) {
        // 函数外的普通右括号
        bracketStack.pop();
      }
    }
  }

  // 设置最终状态
  isInBracket = bracketStack.length > 0;
  // 只检查最后一个未闭合函数内的普通括号状态
  isInInnerBracket =
    innerBracketStack.length > 0 &&
    innerBracketStack[innerBracketStack.length - 1] > 0;

  return {
    isInFunction,
    isInInnerBracket,
    isInBracket,
  };
};

const isInFunction = () => {
  return getCurrentContext().isInFunction;
};

const isInInnerBracket = () => {
  return getCurrentContext().isInInnerBracket;
};

const isInBracket = () => {
  return getCurrentContext().isInBracket;
};

// 判断当前输入后的合法后续输入
const getValidNextInputs = (stack: string[]): InputType[] => {
  if (stack.length === 0) {
    return ["field", "function", "leftBracket"];
  }

  const lastItem = stack[stack.length - 1];

  console.log(isInFunction(), isInInnerBracket(), isInBracket());

  // 根据最后一个输入项和当前上下文确定合法的后续输入
  if (isFunction(lastItem)) {
    return ["leftBracket"];
  }

  if (isLeftBracket(lastItem)) {
    return ["field", "function", "leftBracket"];
  }

  if (isRightBracket(lastItem)) {
    if (isInFunction() && isInInnerBracket()) {
      return ["operator", "rightBracket"];
    } else if (isInFunction() && !isInInnerBracket()) {
      return ["comma", "rightBracket"];
    } else {
      const validInputs: InputType[] = ["operator"];
      if (isInBracket()) {
        validInputs.push("rightBracket");
      }
      return validInputs;
    }
  }

  if (isOperator(lastItem)) {
    return ["field", "function", "leftBracket"];
  }

  if (isComma(lastItem)) {
    return ["field", "function", "leftBracket"];
  }

  // 最后是字段
  if (!isInFunction()) {
    const validInputs: InputType[] = ["operator"];
    if (isInBracket()) {
      validInputs.push("rightBracket");
    }
    return validInputs;
  } else if (isInFunction() && isInInnerBracket()) {
    return ["operator", "rightBracket"];
  } else {
    return ["comma", "rightBracket"];
  }
};

// 添加运算符
const handleAddOperator = (item: { label: string; value: string }) => {
  clearError();
  const validInputs = getValidNextInputs(expressionStack.value);

  if (isFunction(item.value) && validInputs.includes("function")) {
    expressionStack.value.push(item.value);
    return;
  }

  if (isLeftBracket(item.value) && validInputs.includes("leftBracket")) {
    expressionStack.value.push(item.value);
    return;
  }

  if (isRightBracket(item.value) && validInputs.includes("rightBracket")) {
    expressionStack.value.push(item.value);
    return;
  }

  if (isOperator(item.value) && validInputs.includes("operator")) {
    expressionStack.value.push(item.value);
    return;
  }

  if (isComma(item.value) && validInputs.includes("comma")) {
    expressionStack.value.push(item.value);
    return;
  }
};

// 添加字段
const handleAddField = (item: string) => {
  clearError();
  const validInputs = getValidNextInputs(expressionStack.value);

  if (validInputs.includes("field")) {
    expressionStack.value.push(item);
  }
};

// 转换为字符串表达式
const expressionStr = computed(() => {
  return expressionStack.value.join("");
});

const handleBackSpace = () => {
  if (expressionStack.value.length > 0) {
    expressionStack.value.pop();
  }
};

const handleClear = () => {
  expressionStack.value = [];
};

const validateExpression = (): string => {
  const stack = expressionStack.value;

  // 1. 检查表达式是否为空
  if (stack.length === 0) {
    return "表达式不能为空";
  }

  // 2. 检查括号是否匹配
  let bracketCount = 0;
  let functionBrackets: number[] = [];

  for (let i = 0; i < stack.length; i++) {
    if (isFunction(stack[i])) {
      functionBrackets.push(1); // 需要一对括号
    }
    if (isLeftBracket(stack[i])) bracketCount++;
    if (isRightBracket(stack[i])) {
      bracketCount--;
      if (functionBrackets.length > 0) functionBrackets.pop();
    }
    if (bracketCount < 0) return "括号不匹配";
  }
  if (bracketCount > 0) return "缺少右括号";
  if (functionBrackets.length > 0) return "函数缺少闭合括号";

  // 3. 检查函数参数
  for (let i = 0; i < stack.length; i++) {
    if (isFunction(stack[i])) {
      if (!stack[i + 1] || !isLeftBracket(stack[i + 1])) {
        return `函数 ${stack[i]} 缺少参数`;
      }
      // 检查函数是否至少有一个参数
      if (isRightBracket(stack[i + 2])) {
        return `函数 ${stack[i]} 至少需要一个参数`;
      }
    }
  }

  // 4. 检查运算符
  for (let i = 0; i < stack.length; i++) {
    if (isOperator(stack[i])) {
      if (i === stack.length - 1) return "表达式不能以运算符结尾";
      if (i === 0) return "表达式不能以运算符开头";
      if (isOperator(stack[i - 1])) return "运算符不能连续使用";
    }
  }

  return "";
};

const handleClose = () => {
  dialogVisible.value = false;
  //重置所有使用到的参数
  clearError();
  handleClear();
  emits("close");
};

// 修改 handleConfirm 函数
const handleConfirm = async () => {
  const error = validateExpression();
  if (error) {
    expressionError.value = error;
    return;
  }

  emits("confirm", expressionStr.value);
  handleClose();
};

// 在添加操作时清除错误提示
const clearError = () => {
  expressionError.value = "";
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    append-to-body
    :close-on-click-modal="false"
    title="费用类型设置表达式"
    width="55%"
  >
    <div class="dialog-content">
      <div class="text-[var(--el-color-danger)]">{{ expressionError }}</div>
      <div class="flex items-center mb-3">
        <span class="mr-2">{{ props.name }} =</span>
        <el-input class="flex-1" v-model="expressionStr" readonly />
        <el-button
          class="ml-2"
          type="warning"
          icon="Close"
          link
          @click="handleBackSpace"
        >
          退格
        </el-button>
        <el-button
          class="ml-2"
          type="danger"
          icon="Delete"
          link
          @click="handleClear"
        >
          清空
        </el-button>
      </div>

      <div class="flex gap-5">
        <div class="flex-1">
          <div class="mb-3">函数</div>
          <el-card shadow="never">
            <el-scrollbar height="300px">
              <div
                class="py-2 px-4 rounded hover:bg-[#f5f7fa] cursor-pointer active:scale-95"
                v-for="item in operatorOptions"
                :key="item.value"
                @click="handleAddOperator(item)"
              >
                {{ item.label }}
              </div>
            </el-scrollbar>
          </el-card>
        </div>

        <div class="flex-1">
          <div class="mb-3">业务字段</div>
          <el-card shadow="never">
            <el-scrollbar height="300px">
              <div
                class="py-2 px-4 rounded hover:bg-[#f5f7fa] cursor-pointer active:scale-95"
                v-for="item in invoiceFeeTypes"
                :key="item.feeType"
                @click="handleAddField(item.feeType)"
              >
                {{ item.feeType }}
              </div>
            </el-scrollbar>
          </el-card>
        </div>
      </div>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确认</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 50vh;
  padding: 20px;
  overflow-y: auto;
}
</style>
