<script setup lang="ts">
import ClaimImageAPI, { IImageDoc } from "@/api/claimImage";
defineOptions({
  name: "CheckDialog",
});

const emit = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  mode: {
    type: String as PropType<"delete" | "push" | "ocr">,
    default: "delete",
  },
  data: {
    type: Array as PropType<{ className: string; imageList: IImageDoc[] }[]>,
    default: () => [],
  },
});

const title = computed(() => {
  switch (props.mode) {
    case "delete":
      return "删除赔案影像";
    case "push":
      return "推送设置";
    case "ocr":
      return "OCR标记";
    default:
      return "";
  }
});

const imageLength = computed(() => {
  console.log(props.data);
  return props.data.reduce(
    (acc, curr) => acc + (curr.imageList?.length || 0),
    0
  );
});

const pushFlag = ref<boolean>(false);
const ocrFlag = ref<boolean>(false);
const isNeedRefresh = ref<boolean>(false);

const onClose = () => {
  dialogVisible.value = false;
  emit("close", isNeedRefresh.value);
  isNeedRefresh.value = false;
};

const handleConfirm = async () => {
  if (props.mode === "push") {
    await ClaimImageAPI.updatePushFlag({
      idList: props.data
        .flatMap((item) => item.imageList?.map((image) => image.id))
        .filter((id) => id !== undefined),
      pushFlag: pushFlag.value,
    });
  } else if (props.mode === "ocr") {
    await ClaimImageAPI.updateOcrFlag({
      idList: props.data
        .flatMap((item) => item.imageList?.map((image) => image.id))
        .filter((id) => id !== undefined),
      ocrFlag: ocrFlag.value,
    });
  } else if (props.mode === "delete") {
    await ClaimImageAPI.delete({
      idList: props.data
        .flatMap((item) => item.imageList?.map((image) => image.id))
        .filter((id) => id !== undefined),
    });
  }
  isNeedRefresh.value = true;
  onClose();
};

const getImageText = () => {
  return props.data
    .map((item) => {
      return `${item.className}（${item.imageList
        ?.map((image) => `序号${image.classIndex}`)
        .join("、")}）；`;
    })
    .join("\n");
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    :title="title"
    width="30%"
  >
    <div class="p-2">
      <el-form label-width="auto" label-position="left">
        <el-form-item label="被操作影像">
          <div class="flex flex-col">
            <span>共{{ imageLength }}张</span>
            <span class="whitespace-pre-wrap">
              {{ getImageText() }}
            </span>
          </div>
        </el-form-item>
        <div v-if="mode === 'delete'">确认删除？</div>
        <el-form-item v-if="mode === 'push'" label="推送保司设置">
          <el-radio-group v-model="pushFlag">
            <el-radio :value="true">推送</el-radio>
            <el-radio :value="false">不推送</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="mode === 'ocr'" label="设置OCR标记">
          <el-radio-group v-model="ocrFlag">
            <el-radio :value="true">标记</el-radio>
            <el-radio :value="false">不标记</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="flex justify-center">
        <el-button @click="onClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
