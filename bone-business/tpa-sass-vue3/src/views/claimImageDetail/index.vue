<script setup lang="ts">
import ShortCutDialog from "@/components/ClaimImage/ShortCutDialog.vue";
import PrincipleDialog from "@/components/ClaimImage/PrincipleDialog.vue";
import PolicySettingDialog from "@/components/Claim/PolicySettingDialog.vue";
import CheckDialog from "@/components/ClaimImage/CheckDialog.vue";
import UploadImageCheckDialog from "@/components/RenderEngine/base/Upload/ImageCheckDialog.vue";
import ClaimImageAPI, {
  IImageDoc,
  IImageType,
  IInvoice,
} from "@/api/claimImage";
import ProcessPageAPI, { PrecheckDetailConfig } from "@/api/processPage";
import DataAPI from "@/api/data";
import UploadAPI, { UPLOAD_CODE, UploadImageComponent } from "@/api/upload";
import { isEmpty, cloneDeep } from "lodash-es";
import { getValueByJsonPath } from "@/utils/jsonpathUtils";
import { ElRadioGroup, ElRadio } from "element-plus";
import ClaimAPI from "@/api/claim";
import { rotateImage, blobToFile } from "@/utils/canvasUtils";
import { uploadFile, generateFilePathWithTimestamp } from "@/utils/oss";
import clearIcon from "@/assets/icons/clear.png";
import unclearIcon from "@/assets/icons/unclear.png";
import incompleteIcon from "@/assets/icons/incomplete.png";
import upIcon from "@/assets/icons/arrow_up.png";
import downIcon from "@/assets/icons/arrow_down.png";
import resetIcon from "@/assets/icons/reset.png";
import rotateLeftIcon from "@/assets/icons/rotate_left.png";
import rotateRightIcon from "@/assets/icons/rotate_right.png";
import previewIcon from "@/assets/icons/preview.png";
import zoomInIcon from "@/assets/icons/zoom_in.png";
import zoomOutIcon from "@/assets/icons/zoom_out.png";
import introIcon from "@/assets/icons/intro.png";
import shortCutIcon from "@/assets/icons/shortcut.png";
import { useDictStore } from "@/store/modules/dict";
import { BaseCompType } from "@/enums";
import { formatDate } from "@/utils/date";
import { getDateFormat } from "@/enums/baseComp/DateFormatEnum";
import {
  Back,
  Refresh,
  RefreshLeft,
  RefreshRight,
  Right,
  ZoomIn,
  ZoomOut,
} from "@element-plus/icons-vue";
import { PK_IMAGE_TYPE_LIST } from "@/constants";

defineOptions({
  name: "ClaimImageDetail",
});

const route = useRoute();
const claimId = route.query.claimId as string;
const tenantId = route.query.tenantId as string;
const bizIdentityCode = route.query.bizIdentityCode as string;

const dictStore = useDictStore();

// 页面配置
const pageConfig = ref<PrecheckDetailConfig>({
  id: "",
  name: "",
  description: "",
  modelNameList: [],
  pageHead: {
    enablePageHead: 0,
    pageHeadField: [],
    pageHeadModelId: "",
    modelCode: "",
  },
  openDetail: 0,
  specification: "",
  tip: "",
  imageQuality: 0,
  imageType: 0,
});

// 存储处理后的页头数据值
const processedHeadValues = ref(new Map());

// 原始图片列表
let originalImageList: IImageDoc[] = [];
// 当前图片列表
const imageList = ref<IImageDoc[]>([]);
// 赔案信息
const claimInfo = ref<any>({});
// 影像分类类型列表
const classifyTypeList = ref<IImageType[]>([]);
// 用户信息
const userName = ref<string>("");

const imageLoading = ref<boolean>(false);

const selectedImage = ref<IImageDoc | null>(null);
const selectedImageScale = ref<number>(100);
const selectedImageRotate = computed(() => {
  return selectedImage.value?.angle ?? 0;
});

// 当前影像件选中的发票 非初审阶段可以关联发票
const invoiceList = ref<IInvoice[]>([]);
const selectedInvoice = ref<string[]>([]);
let originalInvoice: string[] = [];

// 是否是当前作业人员
const isSameUser = computed(() => {
  return userName.value === claimInfo.value?.main?.operatorUserName;
});

// 是否挂起
const isHangup = computed(() => {
  return claimInfo.value?.main?.hangUpStatus === "1";
});

// 是否是复核阶段
const isReview = computed(() => {
  return claimInfo.value?.main?.stage === "复核";
});

// 控制权限按钮
const hasPermission = computed(() => {
  return !isHangup.value && isSameUser.value && !isReview.value;
});

const getPageConfig = async () => {
  const res = await ProcessPageAPI.getPrecheckDetailPage(bizIdentityCode);
  pageConfig.value = res;
  getClaimInfo();
};

const getImageList = async () => {
  try {
    imageLoading.value = true;
    const res = await ClaimImageAPI.query({
      id: claimId, //赔案id
      tenantId: tenantId,
      pageNo: 0,
      pageSize: 0,
    });

    // 重置 isChecked 状态
    imageList.value = res.map((item) => ({
      ...item,
      isChecked: false,
    }));

    // 保存原始图片列表
    originalImageList = cloneDeep(imageList.value);

    // 如果当前有选中的图片，则更新选中的图片
    if (selectedImage.value) {
      selectedImage.value =
        imageList.value.find((item) => item.id === selectedImage.value?.id) ??
        imageList.value[0];
    } else {
      selectedImage.value = imageList.value[0];
    }
  } catch (error) {
    console.error(error);
  } finally {
    imageLoading.value = false;
  }
};

const getClaimInfo = async () => {
  const res = await DataAPI.getOne({
    id: claimId,
    modelNames: [pageConfig.value.pageHead.modelCode],
  });
  claimInfo.value = res;
  getClasifyTypeList(claimInfo.value?.main?.insuranceName);
  getUserName();
};

const getClasifyTypeList = async (insuranceName: string) => {
  if (!insuranceName) return;

  const res = await ClaimImageAPI.getTypeList(insuranceName);
  classifyTypeList.value = res;
};

const getUserName = async () => {
  const res = await DataAPI.getUserName();
  userName.value = res;
};

const activeCollapseName = ref<string>("未分类");

const getInvoiceList = async (id: string) => {
  const res = await ClaimImageAPI.getInvoiceList(id);
  invoiceList.value = res;

  selectedInvoice.value = res
    .filter((item) => item.bound)
    .map((item) => item.invoiceUuid);

  // 保存原始值
  originalInvoice = cloneDeep(selectedInvoice.value);
};

let unWatchInvoice: any = null;
onMounted(() => {
  unWatchInvoice = watch(
    () => selectedImage.value?.id,
    (newVal) => {
      if (newVal) {
        getInvoiceList(newVal);
      }
    },
    { immediate: true }
  );
});

onUnmounted(() => {
  if (unWatchInvoice) {
    unWatchInvoice();
  }
});

watch(
  () => selectedImage.value?.imageType,
  (newVal) => {
    activeCollapseName.value = newVal || "未分类";
  },
  { immediate: true }
);

const getClassNameCN = (className: string) => {
  if (!className) return "未分类";

  if (className === "未分类") return "未分类";

  if (isEmpty(classifyTypeList.value)) return className;

  return (
    classifyTypeList.value.find((item) => item.classifyCode === className)
      ?.classifyName || "未分类"
  );
};

const PKClassifyImageList = computed(() => {
  //根据普康影像分类，进行分组，得到每个分类的图片数量的字符串
  const map = new Map<string, IImageDoc[]>();
  imageList.value.forEach((item) => {
    if (map.has(item.imagePkType)) {
      map.set(item.imagePkType, [...map.get(item.imagePkType)!, item]);
    } else {
      map.set(item.imagePkType, [item]);
    }
  });

  return PK_IMAGE_TYPE_LIST.map((item) => ({
    className: item.label,
    imagePkType: item.value,
    imageList: map.get(item.value) || [],
  }));
});

const selectedPkTypeImage = ref<any>(null);
const isPersonalImageSelected = ref(false);
const handleSelectPkTypeImage = (item: any) => {
  if (selectedPkTypeImage.value === item.imagePkType) {
    selectedPkTypeImage.value = null;
    return;
  }

  selectedPkTypeImage.value = item.imagePkType;
  isPersonalImageSelected.value = false;
  selectedImage.value = item.imageList[0];
};

const personalImageList = computed(() => {
  return imageList.value.filter((item) => item.fromPersonalImage === 1);
});
const handleSelectPersonalImage = () => {
  if (isPersonalImageSelected.value) {
    isPersonalImageSelected.value = false;
    return;
  }

  selectedPkTypeImage.value = null;
  isPersonalImageSelected.value = true;
  selectedImage.value = personalImageList.value[0];
};

const currentLeftImageList = computed(() => {
  if (selectedPkTypeImage.value) {
    return (
      PKClassifyImageList.value.find(
        (item) => item.imagePkType === selectedPkTypeImage.value
      )?.imageList || []
    );
  }
  if (isPersonalImageSelected.value) {
    return personalImageList.value;
  }
  return imageList.value;
});

const classifyImageList = computed(() => {
  const map = new Map<string, IImageDoc[]>();

  map.set("未分类", []);

  // 先按分类整理图片
  currentLeftImageList.value.forEach((item) => {
    if (!item.imageType) {
      map.set("未分类", [...map.get("未分类")!, item]);
      return;
    }

    if (map.has(item.imageType)) {
      map.set(item.imageType, [...map.get(item.imageType)!, item]);
    } else {
      map.set(item.imageType, [item]);
    }
  });

  // 转成对象数组的形式，key 为分类名称，value 为分类列表
  return Array.from(map.entries()).map(([key, value]) => ({
    className: key,
    imageList: value,
  }));
});

const orderedImageList = computed(() => {
  return classifyImageList.value.map((item) => item.imageList).flat();
});

onMounted(() => {
  // 添加图片拖拽事件
  document.addEventListener("mousemove", handleMouseMove);
  document.addEventListener("mouseup", handleMouseUp);

  // 添加快捷键事件
  window.addEventListener("keydown", handleShortcutKey);
  document.addEventListener("wheel", handleWheel, { passive: false });

  // 获取页面配置
  getPageConfig();

  // 获取图片列表
  getImageList();
});

onUnmounted(() => {
  // 清理事件监听
  document.removeEventListener("mousemove", handleMouseMove);
  document.removeEventListener("mouseup", handleMouseUp);
  document.removeEventListener("wheel", handleWheel);
  window.removeEventListener("keydown", handleShortcutKey);
});

const scrollToSelectedImage = (imageId: string) => {
  const imageElement = document.querySelector(`[data-image-id="${imageId}"]`);
  if (imageElement) {
    imageElement.scrollIntoView({
      behavior: "smooth",
      block: "center",
      inline: "nearest",
    });
  }
};

const handleSelectImage = (item: any) => {
  if (selectedImage.value?.id === item.id) return;

  // 只重置缩放和位置，不重置角度
  selectedImageScale.value = 100;
  position.value = { x: 0, y: 0 };
  selectedImage.value = item;

  // 滚动到选中的图片位置
  nextTick(() => {
    scrollToSelectedImage(item.id);
  });
};

const handlePrevImage = () => {
  if (!selectedImage.value) return;

  if (orderedImageList.value.length <= 1) {
    ElMessage.warning("没有上一张图片");
    return;
  }

  const currentIndex = orderedImageList.value.findIndex(
    (img) => img.id === selectedImage.value?.id
  );
  if (currentIndex > 0) {
    selectedImage.value = orderedImageList.value[currentIndex - 1];
  } else {
    selectedImage.value =
      orderedImageList.value[orderedImageList.value.length - 1];
  }

  selectedImageScale.value = 100;
  position.value = { x: 0, y: 0 };

  // 滚动到选中的图片位置
  nextTick(() => {
    scrollToSelectedImage(selectedImage.value?.id || "");
  });
};

const handleNextImage = () => {
  if (!selectedImage.value) return;

  if (orderedImageList.value.length <= 1) {
    ElMessage.warning("没有下一张图片");
    return;
  }

  const currentIndex = orderedImageList.value.findIndex(
    (img) => img.id === selectedImage.value?.id
  );
  if (currentIndex < orderedImageList.value.length - 1) {
    selectedImage.value = orderedImageList.value[currentIndex + 1];
  } else {
    selectedImage.value = orderedImageList.value[0];
  }

  selectedImageScale.value = 100;
  position.value = { x: 0, y: 0 };

  // 滚动到选中的图片位置
  nextTick(() => {
    scrollToSelectedImage(selectedImage.value?.id || "");
  });
};

// 全屏设置
const mainEl = ref<HTMLElement | null>(null);
const { isFullscreen, enter, exit } = useFullscreen(mainEl);
const handleFullScreen = () => {
  // 如果当前是全屏，则退出全屏
  if (isFullscreen.value) {
    exit();
  } else {
    enter();
  }
};

const handleReset = () => {
  if (!selectedImage.value) return;

  selectedImageScale.value = 100;
  // 找到原始图片数据
  const originalImage = originalImageList.find(
    (item) => item.id === selectedImage.value?.id
  );
  // 重置为原始角度
  selectedImage.value.angle = originalImage?.angle ?? 0;
  position.value = { x: 0, y: 0 };
};

const viewerImageRef = ref<HTMLElement | null>(null);
const handlePreviewImage = () => {
  if (!selectedImage.value || !viewerImageRef.value) return;

  (viewerImageRef.value as any).showPreview();
};

const handleZoomIn = () => {
  if (!selectedImage.value) return;

  // 添加最大缩放限制，防止图片放大过度
  if (selectedImageScale.value >= 400) return;

  selectedImageScale.value += 10;
};

const handleZoomOut = () => {
  if (!selectedImage.value) return;

  // 添加最小缩放限制，防止图片缩得太小
  if (selectedImageScale.value <= 20) return;

  selectedImageScale.value -= 10;
};

const handleRotateLeft90 = () => {
  if (!selectedImage.value) return;

  selectedImage.value.angle =
    (((selectedImageRotate.value - 90) % 360) + 360) % 360;
};

const handleRotateRight90 = () => {
  if (!selectedImage.value) return;

  selectedImage.value.angle =
    (((selectedImageRotate.value + 90) % 360) + 360) % 360;
};

const viewerActions = ref<any>(null);
const viewerReset = ref<any>(null);
const timers: any[] = []; // 存所有计时器

function cachePreviewActions(actions: any, reset: any) {
  if (!viewerActions.value) viewerActions.value = actions;
  if (!viewerReset.value) viewerReset.value = reset;
  return "";
}

function clearPreviewActions() {
  timers.forEach((timer) => clearTimeout(timer));
  timers.length = 0;
  viewerActions.value = null;
  viewerReset.value = null;
}

onUnmounted(() => {
  clearPreviewActions();
});

function handleImageViewerTransform() {
  function applyWhenReady(retry = 0) {
    const img = document.querySelector(
      ".el-image-viewer__img"
    ) as HTMLImageElement;
    if (!img) {
      if (retry < 10) {
        const timeoutId = setTimeout(() => applyWhenReady(retry + 1), 30);
        timers.push(timeoutId);
      }
      return;
    }
    viewerReset.value?.();
    viewerActions.value?.("clockwise", {
      rotateDeg: selectedImageRotate.value,
      enableTransition: false,
    });
  }
  applyWhenReady();
}

const handlePreviewPrevImage = (reset: any, actions: any) => {
  if (!selectedImage.value) return;
  reset();
  handlePrevImage();
  nextTick(() => {
    if (selectedImageRotate.value !== 0) {
      actions("clockwise", {
        rotateDeg: selectedImageRotate.value,
        enableTransition: false,
      });
    }
  });
};

const handlePreviewNextImage = (reset: any, actions: any) => {
  if (!selectedImage.value) return;
  reset();
  handleNextImage();
  nextTick(() => {
    if (selectedImageRotate.value !== 0) {
      actions("clockwise", {
        rotateDeg: selectedImageRotate.value,
        enableTransition: false,
      });
    }
  });
};

const handlePreviewReset = (reset: any) => {
  if (!selectedImage.value) return;
  reset();
  handleReset();
};

const handlePreviewRotateLeft90 = (reset: any, actions: any) => {
  if (!selectedImage.value) return;
  handleRotateLeft90();
  reset();
  actions("clockwise", {
    rotateDeg: selectedImageRotate.value,
    enableTransition: false,
  });
};

const handlePreviewRotateRight90 = (reset: any, actions: any) => {
  if (!selectedImage.value) return;
  handleRotateRight90();
  reset();
  actions("clockwise", {
    rotateDeg: selectedImageRotate.value,
    enableTransition: false,
  });
};

const handlePreviewZoomIn = (reset: any, actions: any) => {
  if (!selectedImage.value) return;
  actions("zoomIn");
};

const handlePreviewZoomOut = (reset: any, actions: any) => {
  if (!selectedImage.value) return;
  actions("zoomOut");
};

const handleRotateLeft30 = () => {
  if (!selectedImage.value) return;

  selectedImage.value.angle =
    (((selectedImageRotate.value - 30) % 360) + 360) % 360;
};

const handleRotateRight30 = () => {
  if (!selectedImage.value) return;

  selectedImage.value.angle =
    (((selectedImageRotate.value + 30) % 360) + 360) % 360;
};

const draggableImageRef = ref<HTMLElement | null>(null);
const isDragging = ref(false);
const position = ref({ x: 0, y: 0 });
const dragStart = ref({ x: 0, y: 0 });

const handleMouseDown = (e: MouseEvent) => {
  isDragging.value = true;
  dragStart.value = {
    x: e.clientX - position.value.x,
    y: e.clientY - position.value.y,
  };
};

const handleMouseMove = (e: MouseEvent) => {
  if (!isDragging.value) return;

  position.value = {
    x: e.clientX - dragStart.value.x,
    y: e.clientY - dragStart.value.y,
  };
};

const handleMouseUp = () => {
  isDragging.value = false;
};

const handleWheel = (e: WheelEvent) => {
  // 仅在按住 Ctrl 键时才触发缩放
  if (!e.ctrlKey || !selectedImage.value) return;

  e.preventDefault(); // 阻止默认的页面滚动

  // deltaY < 0 表示向上滚动(放大)，deltaY > 0 表示向下滚动(缩小)
  if (e.deltaY < 0) {
    handleZoomIn();
  } else {
    handleZoomOut();
  }
};

const clearTypeInputRef = ref<HTMLElement | null>(null);
const imageTypeInputRef = ref<HTMLElement | null>(null);

// 添加快捷键配置
const shortcutConfig = {
  ArrowDown: handleNextImage,
  ArrowUp: handlePrevImage,
  ArrowRight: (e: KeyboardEvent) => {
    if (e.shiftKey && e.altKey) {
      handleRotateRight30();
    } else if (e.shiftKey) {
      handleRotateRight90();
    } else {
      handleZoomIn();
    }
  },
  ArrowLeft: (e: KeyboardEvent) => {
    if (e.shiftKey && e.altKey) {
      handleRotateLeft30();
    } else if (e.shiftKey) {
      handleRotateLeft90();
    } else {
      handleZoomOut();
    }
  },
  "1": (e: KeyboardEvent) => {
    if (e.altKey) {
      // 使用 Alt + 1
      e.preventDefault();
      clearTypeInputRef.value?.focus();
    }
  },
  "2": (e: KeyboardEvent) => {
    if (e.altKey) {
      // 使用 Alt + 2
      e.preventDefault();
      imageTypeInputRef.value?.focus();
    }
  },
  Enter: (e: KeyboardEvent) => {
    if (e.shiftKey) {
      handleFullScreen();
    }
  },
};

// 快捷键监听函数
const handleShortcutKey = (e: KeyboardEvent) => {
  const viewerOpen =
    document.querySelector(".el-image-viewer__wrapper") !== null;
  if (viewerOpen) return; // 预览打开中，屏蔽所有快捷键逻辑

  // 如果当前焦点在输入框且不为Alt键时，不触发快捷键
  if (
    e.target instanceof HTMLInputElement ||
    e.target instanceof HTMLTextAreaElement
  ) {
    if (!e.altKey) {
      return;
    }
  }

  const handler = shortcutConfig[e.key as keyof typeof shortcutConfig];
  if (handler) {
    e.preventDefault(); // 阻止默认行为
    handler(e);
  }
};

const isShowClassifyList = ref<boolean>(false);

const isShowShortCutDialog = ref<boolean>(false);
const handleShowShortCutDialog = () => {
  isShowShortCutDialog.value = true;
};

const isShowPrincipleDialog = ref<boolean>(false);
const handleShowPrincipleDialog = () => {
  isShowPrincipleDialog.value = true;
};

const isManaging = ref<boolean>(false);
const checkDialog = ref({
  visible: false,
  params: {
    mode: "delete" as "push" | "delete" | "ocr",
    data: [] as { className: string; imageList: IImageDoc[] }[],
  },
  close: (isRefresh: boolean) => {
    checkDialog.value.visible = false;
    if (isRefresh) {
      getImageList();
    }
  },
});

const handleToggleManage = () => {
  isManaging.value = !isManaging.value;

  // 如果取消管理，则重置所有图片的isChecked状态
  if (!isManaging.value) {
    imageList.value.forEach((item) => {
      item.isChecked = false;
    });
  }
};

const handleManageImage = (mode: "delete" | "push" | "ocr") => {
  const checkedImageList = classifyImageList.value
    .map((item) => ({
      className: getClassNameCN(item.className),
      imageList: item.imageList
        .map((item, index) => {
          return {
            ...item,
            classIndex: index + 1,
          };
        })
        .filter((image: any) => image.isChecked),
    }))
    .filter((item: any) => item.imageList.length > 0);

  switch (mode) {
    case "delete":
      handleDeleteImage(checkedImageList);
      break;
    case "push":
      handlePushSetting(checkedImageList);
      break;
    case "ocr":
      handleOCRMark(checkedImageList);
  }
};

const handleDeleteImage = (checkedImageList: any) => {
  if (checkedImageList.length === 0) {
    ElMessage.warning("请先选择要删除的影像");
    return;
  }
  checkDialog.value.visible = true;
  checkDialog.value.params.mode = "delete";
  checkDialog.value.params.data = checkedImageList;
};

const handlePushSetting = (checkedImageList: any) => {
  if (checkedImageList.length === 0) {
    ElMessage.warning("请先选择要推送的影像");
    return;
  }
  checkDialog.value.visible = true;
  checkDialog.value.params.mode = "push";
  checkDialog.value.params.data = checkedImageList;
};

const handleOCRMark = (checkedImageList: any) => {
  if (checkedImageList.length === 0) {
    ElMessage.warning("请先选择要标记OCR的影像");
    return;
  }
  checkDialog.value.visible = true;
  checkDialog.value.params.mode = "ocr";
  checkDialog.value.params.data = checkedImageList;
};

const uploadComponent = ref<UploadImageComponent>({
  id: "",
  title: "",
  dataType: 3,
  singleMaxSize: 0,
  fileFormat: "",
  maxCount: 0,
  importDescription: "",
});
onMounted(async () => {
  uploadComponent.value = await UploadAPI.getUploadImageByCode(
    UPLOAD_CODE.FIRST_AUDIT_DETAIL_UPLOAD_PICTURE
  );
});

const uploadImageCheckDialog = ref({
  visible: false,
  params: {
    uploadComponent: {},
    uploadData: {
      relatedId: "",
      configId: "",
      tenantId: "",
    },
  },
  close: () => {
    uploadImageCheckDialog.value.visible = false;
  },
  result: (result: boolean) => {
    if (result) {
      getImageList();
    }
  },
});
const handleAddImage = () => {
  uploadImageCheckDialog.value.params = {
    uploadComponent: uploadComponent.value,
    uploadData: {
      relatedId: claimId,
      configId: uploadComponent.value?.id ?? "",
      tenantId: tenantId,
    },
  };
  uploadImageCheckDialog.value.visible = true;
};

/**
 * 保存所有图片
 * 1. 过滤出需要保存的图片（有修改的图片），通过对比原列表数据，获取有修改的图片
 * 2. 批量保存
 * 3. 刷新列表
 */
const saveLoading = ref<boolean>(false);
const handleSaveAll = async () => {
  try {
    saveLoading.value = true;
    // 过滤出需要保存的图片（有修改的图片），通过对比原列表数据，获取有修改的图片
    const imagesToSave = imageList.value.filter((item) => {
      const originalItem = originalImageList.find(
        (originalItem) => originalItem.id === item.id
      );

      //对比角度、是否清晰、影像分类
      return (
        originalItem?.angle !== item.angle ||
        originalItem?.clearType !== item.clearType ||
        originalItem?.imageType !== item.imageType
      );
    });

    if (imagesToSave.length === 0) {
      ElMessage.warning("没有需要保存的图片");
      return;
    }

    //如果需要保存的图片中有角度旋转的，则需要先旋转图片，得到旋转后的图片上传到oss，得到旋转后的图片的url，再更新到数据库
    // 处理有角度旋转的图片
    const rotatedImages: IImageDoc[] = [];
    const normalImages: IImageDoc[] = [];

    // 分离需要旋转的图片和普通图片
    for (const image of imagesToSave) {
      if (image.angle && image.angle !== 0) {
        rotatedImages.push(image);
      } else {
        normalImages.push(image);
      }
    }

    // 处理需要旋转的图片
    if (rotatedImages.length > 0) {
      const rotationPromises = rotatedImages.map(async (image) => {
        try {
          // 旋转图片
          const rotatedBlob = await rotateImage(
            image.imagePath,
            image.angle || 0
          );

          // 生成新的文件路径（保持原始文件名）
          const originalFileName =
            image.imagePath.split("/").pop() || "rotated_image.jpg";
          const newFilePath = generateFilePathWithTimestamp(originalFileName);

          // 转换为File对象
          const rotatedFile = blobToFile(rotatedBlob, originalFileName);

          // 上传到OSS
          const uploadResult = await uploadFile(rotatedFile, newFilePath);

          if (uploadResult.success) {
            // 更新图片信息
            image.imagePath = uploadResult.fullUrl;
            image.imageName = originalFileName; // 保持原始文件名
            image.angle = 0; // 重置角度为0，因为图片已经旋转了
            return image;
          } else {
            throw new Error(`上传失败: ${uploadResult.error}`);
          }
        } catch (error) {
          console.error(`处理图片 ${image.id} 失败:`, error);
          ElMessage.error(
            `处理图片失败: ${error instanceof Error ? error.message : "未知错误"}`
          );
          throw error;
        }
      });

      try {
        await Promise.all(rotationPromises);
      } catch (error) {
        // 如果有图片处理失败，终止保存操作
        return;
      }
    }

    // 合并所有需要保存的图片
    const finalImagesToSave = [...rotatedImages, ...normalImages];

    // 批量保存
    await ClaimImageAPI.updateList(finalImagesToSave);

    ElMessage.success("保存成功");
    // 刷新列表
    getImageList();
  } catch (error) {
    console.error(error);
  } finally {
    saveLoading.value = false;
  }
};

const debounceSaveAll = useDebounceFn(handleSaveAll, 300);

/**
 * 获取普康保影像件
 */
const pkbImageLoading = ref<boolean>(false);
const pkbPersonType = ref(0); // 0: 出险人, 1: 主被保险人
const handleGetPkbImage = async () => {
  pkbPersonType.value = 0;
  try {
    await ElMessageBox({
      title: "获取普康保影像件",
      message: () =>
        h("div", { style: "margin: 10px;" }, [
          h(
            "div",
            { style: "margin-bottom: 10px;" },
            "请选择获取影像件的人员："
          ),
          h(
            ElRadioGroup,
            {
              modelValue: pkbPersonType.value,
              "onUpdate:modelValue": (val) => {
                const num = Number(val);
                pkbPersonType.value = num === 1 ? 1 : 0;
              },
            },
            [
              h(ElRadio, { value: 0 }, () => "出险人"),
              h(ElRadio, { value: 1 }, () => "主被保险人"),
            ]
          ),
        ]),
      showCancelButton: true,
      confirmButtonText: "确认",
      cancelButtonText: "取消",
      closeOnClickModal: false,
      closeOnPressEscape: false,
    });
    pkbImageLoading.value = true;
    await ClaimImageAPI.getPkbImage({
      claimId: claimId,
      personType: pkbPersonType.value as 0 | 1,
    });
    getImageList();
    ElMessage.success("获取普康保影像件成功");
  } catch (error) {
    // 用户取消弹窗不提示错误
    if (error !== "cancel") {
      console.error(error);
    }
  } finally {
    pkbImageLoading.value = false;
  }
};

// 影像分类变化
const handleImageTypeChange = (value: string) => {
  // value必须为classifyTypeList中的classifyCode，不存在则提示
  const isExist = classifyTypeList.value.some(
    (item) => item.classifyCode === value
  );
  if (!isExist) {
    ElMessage.error("影像分类不存在");
    if (selectedImage.value) {
      selectedImage.value.imageType = "";
    }
    return;
  }
  // 如果存在，则更新影像分类
  if (selectedImage.value) {
    selectedImage.value.imageType = value;
  }
};

// 处理表单按键事件，用于切换输入框焦点
const handleFormKeydown = (e: KeyboardEvent) => {
  if (e.key === "Tab") {
    e.preventDefault();

    const currentTarget = e.target as HTMLInputElement;
    const clearTypeInput = (clearTypeInputRef.value as any)?.input;
    const imageTypeInput = (imageTypeInputRef.value as any)?.input;

    if (currentTarget === clearTypeInput) {
      // 当前在清晰度输入框，切换到影像分类输入框
      imageTypeInput?.focus();
    } else if (currentTarget === imageTypeInput) {
      // 当前在影像分类输入框，切换到清晰度输入框
      clearTypeInput?.focus();
    } else {
      // 如果当前焦点不在任何输入框，默认聚焦到清晰度输入框
      clearTypeInput?.focus();
    }
  }
};

/**
 * 关联发票
 */
const handleBindInvoice = async () => {
  if (!selectedImage.value) return;

  // 只在值发生变化时执行
  if (
    JSON.stringify(selectedInvoice.value) !== JSON.stringify(originalInvoice)
  ) {
    // 调用接口绑定发票
    await ClaimImageAPI.bindInvoice({
      imageId: selectedImage.value.id,
      invoiceUuidList: selectedInvoice.value,
    });

    ElMessage.success("已修改关联发票");

    // 获取发票列表
    getInvoiceList(selectedImage.value.id);
  }
};

const imageQualityList = [
  { name: "清晰", code: 1, icon: clearIcon, color: "#00AD1D" },
  { name: "不清晰", code: 2, icon: unclearIcon, color: "#ED3115" },
  { name: "不完整", code: 3, icon: incompleteIcon, color: "#ED3115" },
];

const getClearType = (clearType: number) => {
  return imageQualityList.find((item) => item.code === clearType);
};

const handleClearTypeChange = (value: string) => {
  if (!selectedImage.value) {
    return;
  }

  const numValue = Number(value);
  if (numValue === 1) {
    selectedImage.value.clearType = 1;
  } else if (numValue === 2) {
    selectedImage.value.clearType = 2;
  } else if (numValue === 3) {
    selectedImage.value.clearType = 3;
  } else {
    ElMessage.error("请输入正确的清晰度");
    selectedImage.value.clearType = null;
  }
};

/**
 * 查看特约信息
 */
const policySettingDialog = ref({
  visible: false,
  params: {
    type: 0,
    claimId,
  },
  close: () => {
    policySettingDialog.value.visible = false;
    policySettingDialog.value.params.type = 0;
    policySettingDialog.value.params.claimId = "";
  },
});
const handleShowPolicySettingDialog = (type: number) => {
  policySettingDialog.value.params.type = type;
  policySettingDialog.value.params.claimId = claimId;
  policySettingDialog.value.visible = true;
};

const getPolicySetting = async () => {
  const res = await ClaimAPI.policySetting(claimId, 0);
  if (res) {
    handleShowPolicySettingDialog(0);
  }
};

onMounted(async () => {
  await getPolicySetting();
});

const getSelectDropValue = async (type: any, code: any, value: any) => {
  if (value === undefined || value === null) {
    return value;
  }
  const valueArray = typeof value === "string" ? value.split(",") : false;
  if (valueArray) {
    const dictLabels = await dictStore.getDictLabel(type, code, valueArray);
    return valueArray.map((item) => dictLabels[item] || item).join(",");
  }
  const dictLabels = await dictStore.getDictLabel(type, code, value);
  return dictLabels[value] || value;
};

const getSelectCtrlValue = (value: any) => {
  try {
    if (!value) return value;
    const parsedValue = JSON.parse(value);
    if (!parsedValue?.desc?.length) return undefined;
    const validValues = parsedValue.desc.filter(
      (item: any) => item && item.trim()
    );
    return validValues.length ? validValues.join("/") : undefined;
  } catch (e) {
    return value;
  }
};

const getHeadDataValue = async (item: any) => {
  const value = getValueByJsonPath(claimInfo.value, item.dataBinding);
  switch (item.componentType?.replace("PK", "")) {
    case BaseCompType.DateTime:
      return formatDate(value, getDateFormat(item?.dateFormatType));
    case BaseCompType.DateRange:
      return value?.replace(",", " ~ ");
    case BaseCompType.SelectDrop:
      return await getSelectDropValue(
        item?.selectDatasource?.type,
        item?.selectDatasource?.code,
        value
      );
    case BaseCompType.SelectCtrl:
      return getSelectCtrlValue(value);
    default:
      return value;
  }
};

// 处理页头显示项的数据值
const processHeadItems = async () => {
  const newProcessedValues = new Map();
  const headFields = pageConfig.value.pageHead.pageHeadField || [];
  for (const item of headFields) {
    try {
      const value = await getHeadDataValue(item);
      newProcessedValues.set(item.bizName, value);
    } catch (error) {
      console.error(`Error processing head item ${item.bizName}:`, error);
      newProcessedValues.set(item.bizName, "");
    }
  }
  processedHeadValues.value = newProcessedValues;
};

// 监听页头字段和赔案信息变化，重新处理数据
watch(
  [() => pageConfig.value.pageHead.pageHeadField, claimInfo],
  processHeadItems,
  { immediate: true, deep: true }
);

// 获取处理后的页头值
const getProcessedHeadValue = (item: any) => {
  return processedHeadValues.value.get(item.bizName) || "";
};

const addPersonalImageLibrary = ref(-1);
const handleAddPersonalImageLibrary = async () => {
  if (addPersonalImageLibrary.value === 1) {
    await ClaimImageAPI.addPersonalImageToClaim(claimId);
    ElMessage.success("添加个人影像库成功");
    getImageList();
  }
};
</script>

<template>
  <div class="w-full h-full flex flex-col">
    <div class="flex flex-col flex-1 overflow-hidden bg-[--el-bg-color-page]">
      <!-- 页头 -->
      <div
        class="sticky top-0 z-10 bg-[var(--el-bg-color)] px-6 py-3 border-b border-[var(--el-border-color-lighter)] flex justify-between items-center"
      >
        <div class="flex-basis-[55%]">
          <span class="text-lg font-bold text-[var(--el-text-color-primary)]">
            赔案影像详情
          </span>
        </div>
      </div>

      <div
        class="m-2 rounded-md flex flex-col flex-1 overflow-hidden bg-[--el-bg-color] border border-[var(--el-border-color-lighter)]"
      >
        <div
          class="flex justify-between px-6 py-3 border-b border-[var(--el-border-color-lighter)]"
        >
          <!-- 赔案信息 -->
          <div class="grid-layout w-full flex-basis-[70%]">
            <div
              class="grid-item"
              v-for="item in pageConfig.pageHead.pageHeadField"
              :key="item.id"
            >
              <span class="item-label">{{ item.bizName }}</span>
              <span class="item-value">
                {{ getProcessedHeadValue(item) }}
              </span>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="flex-basis-[30%] flex gap-2 flex-wrap button-group">
            <el-button
              plain
              type="primary"
              @click="handleShowPolicySettingDialog(0)"
            >
              查看特约信息
            </el-button>
            <el-button
              plain
              type="primary"
              @click="handleShowPolicySettingDialog(1)"
            >
              查看特殊信息
            </el-button>
            <el-button
              v-if="hasPermission"
              plain
              type="primary"
              @click="handleGetPkbImage"
              :loading="pkbImageLoading"
            >
              获取普康保影像
            </el-button>
            <el-button
              v-if="hasPermission"
              plain
              type="primary"
              @click="handleAddImage"
            >
              补充影像件
            </el-button>
            <el-button
              v-if="hasPermission"
              plain
              type="primary"
              @click="handleSaveAll"
              :loading="saveLoading"
            >
              保存分类
            </el-button>
          </div>
        </div>

        <div
          v-loading="imageLoading"
          ref="mainEl"
          class="flex flex-1 bg-[--el-bg-color] overflow-hidden px-4 py-2"
        >
          <!-- 左侧，图片列表 -->
          <el-card shadow="never" class="container-left">
            <div class="overflow-y-auto h-full">
              <el-collapse accordion v-model="activeCollapseName">
                <template
                  v-for="item in classifyImageList"
                  :key="item.className"
                >
                  <el-collapse-item
                    :name="item.className"
                    v-if="!isEmpty(item.imageList)"
                  >
                    <template #title>
                      <div
                        class="ml-4 text-sm text-[var(--el-text-color-regular)] font-bold"
                      >
                        {{
                          getClassNameCN(item.className) +
                          " (" +
                          item.imageList.length +
                          ")"
                        }}
                      </div>
                    </template>

                    <div class="flex flex-col gap-4 px-5 mt-4">
                      <div
                        v-for="(imageDoc, index) in item.imageList"
                        :key="imageDoc.id"
                        :class="{
                          'border-2 border-[var(--el-color-primary-light-3)] rounded-md scale-105 transition-all duration-300':
                            selectedImage?.id === imageDoc.id,
                        }"
                      >
                        <el-card
                          shadow="hover"
                          class="flex flex-col cursor-pointer relative"
                          :data-image-id="imageDoc.id"
                          @click="handleSelectImage(imageDoc)"
                        >
                          <!-- 勾选框 -->
                          <el-checkbox
                            v-if="isManaging"
                            class="inner-checkbox"
                            v-model="imageDoc.isChecked"
                          />
                          <!-- 预览图 -->
                          <el-image
                            :src="imageDoc.imagePath"
                            class="w-full h-full"
                            :style="{
                              transform: `rotate(${imageDoc.angle ?? 0}deg)`,
                            }"
                          />
                          <!-- 序号 -->
                          <div
                            class="absolute top-2 left-2 bg-[--el-bg-color] border-2 border-[var(--el-border-color-lighter)] rounded-sm px-2 py-1 text-sm text-[var(--el-text-color-regular)]"
                          >
                            {{ index + 1 }}
                          </div>

                          <div
                            v-if="imageDoc.clearType"
                            class="absolute bottom-2 right-2 flex items-center gap-1 bg-[--el-bg-color] rounded-xl shadow-md px-2 py-1 text-sm"
                            :style="{
                              color: getClearType(imageDoc.clearType)?.color,
                            }"
                          >
                            <el-image
                              :src="getClearType(imageDoc.clearType)?.icon"
                              class="w-4 h-4"
                            />
                            {{ getClearType(imageDoc.clearType)?.name }}
                          </div>
                        </el-card>
                      </div>
                    </div>
                  </el-collapse-item>
                </template>
              </el-collapse>
              <!-- 添加左侧空状态 -->
              <el-empty v-if="imageList.length === 0" description="暂无影像" />
            </div>

            <template #footer>
              <div class="flex justify-between items-center h-20px">
                <span
                  v-if="!isManaging"
                  class="text-sm text-[var(--el-text-color-regular)]"
                >
                  共{{ imageList.length }}张
                </span>
                <div class="flex items-center" v-else>
                  <el-button
                    type="primary"
                    link
                    @click="handleManageImage('delete')"
                  >
                    删除
                  </el-button>
                  <el-button
                    type="primary"
                    link
                    @click="handleManageImage('push')"
                  >
                    推送设置
                  </el-button>
                </div>
                <el-button
                  v-if="hasPermission"
                  type="primary"
                  link
                  @click="handleToggleManage"
                >
                  {{ isManaging ? "取消" : "影像管理" }}
                </el-button>
              </div>
            </template>
          </el-card>

          <!-- 主体部分，图片展示 -->
          <el-card shadow="never" class="image-container">
            <template #header>
              <div class="flex items-center justify-between">
                <div>
                  <span
                    class="text-sm text-[var(--el-text-color-regular)] mr-2 font-bold"
                  >
                    普康影像分类
                  </span>
                  <template
                    v-for="item in PKClassifyImageList"
                    :key="item.imagePkType"
                  >
                    <span
                      :class="{
                        'text-[var(--el-color-primary)] font-bold':
                          selectedPkTypeImage === item.imagePkType,
                      }"
                      class="text-sm ml-2 cursor-pointer hover:text-[var(--el-color-primary)]"
                      @click="handleSelectPkTypeImage(item)"
                    >
                      {{ item.className }} ({{ item.imageList.length }})
                    </span>
                  </template>
                  <span
                    :class="{
                      'text-[var(--el-color-primary)] font-bold':
                        isPersonalImageSelected,
                    }"
                    class="text-sm ml-2 cursor-pointer hover:text-[var(--el-color-primary)]"
                    @click="handleSelectPersonalImage"
                  >
                    个人影像库（{{ personalImageList.length }}）
                  </span>
                </div>

                <div class="flex items-center" v-if="hasPermission">
                  <span
                    class="text-sm text-[var(--el-text-color-regular)] mr-3 font-bold"
                  >
                    添加个人影像库
                  </span>
                  <el-radio-group
                    v-model="addPersonalImageLibrary"
                    @change="handleAddPersonalImageLibrary"
                  >
                    <el-radio :value="1">是</el-radio>
                    <el-radio :value="-1">否</el-radio>
                  </el-radio-group>
                </div>
              </div>
            </template>
            <div class="flex-1 overflow-hidden">
              <div
                ref="draggableImageRef"
                class="draggable-wrapper h-full"
                :class="{ dragging: isDragging }"
                @mousedown.prevent="handleMouseDown"
                @dblclick="handlePreviewImage"
                :style="{
                  transform: `translate(${position.x}px, ${position.y}px) rotate(${selectedImageRotate}deg) scale(${selectedImageScale / 100})`,
                }"
              >
                <el-image
                  ref="viewerImageRef"
                  v-if="selectedImage"
                  class="transition-none"
                  :src="selectedImage?.imagePath"
                  :preview-src-list="[selectedImage.imagePath]"
                  :hide-on-click-modal="true"
                  preview-teleported
                  lazy
                  @show="handleImageViewerTransform"
                  @close="clearPreviewActions"
                >
                  <template #toolbar="{ actions, reset }">
                    <span v-if="!viewerActions" style="display: none">
                      {{ cachePreviewActions(actions, reset) }}
                    </span>
                    <el-icon @click="handlePreviewPrevImage(reset, actions)">
                      <Back />
                    </el-icon>
                    <el-icon @click="handlePreviewNextImage(reset, actions)">
                      <Right />
                    </el-icon>
                    <el-icon @click="handlePreviewReset(reset)">
                      <Refresh />
                    </el-icon>
                    <el-icon @click="handlePreviewZoomIn(reset, actions)">
                      <ZoomIn />
                    </el-icon>
                    <el-icon @click="handlePreviewZoomOut(reset, actions)">
                      <ZoomOut />
                    </el-icon>
                    <el-icon @click="handlePreviewRotateLeft90(reset, actions)">
                      <RefreshLeft />
                    </el-icon>
                    <el-icon
                      @click="handlePreviewRotateRight90(reset, actions)"
                    >
                      <RefreshRight />
                    </el-icon>
                  </template>
                </el-image>
                <!-- 添加主体区域空状态 -->
                <el-empty
                  v-else
                  description="暂未选中影像"
                  class="h-full flex items-center justify-center"
                />
              </div>
            </div>

            <!-- 影像分类表格 -->
            <div class="image-table-container flex flex-col">
              <el-button
                type="primary"
                link
                class="w-[140px]"
                @click="isShowClassifyList = !isShowClassifyList"
              >
                影像分类规则
                <i-ep-arrow-down
                  class="ml-2 transition-all duration-300"
                  :class="{ 'rotate-180': isShowClassifyList }"
                />
              </el-button>
              <transition name="fade-up">
                <el-table
                  border
                  :data="classifyTypeList"
                  class="mt-2"
                  :max-height="400"
                  v-if="isShowClassifyList"
                >
                  <el-table-column
                    prop="classifyName"
                    label="影像分类"
                    align="center"
                    width="250"
                  />
                  <el-table-column
                    prop="classifyCode"
                    label="编码值"
                    align="center"
                    width="150"
                  />
                </el-table>
              </transition>
            </div>

            <!-- 关联发票 -->
            <div class="invoice-container" v-if="hasPermission">
              <el-form>
                <el-form-item label="关联发票">
                  <el-select
                    v-model="selectedInvoice"
                    placeholder="请选择发票"
                    tag-type="primary"
                    multiple
                    @blur="handleBindInvoice"
                  >
                    <el-option
                      v-for="item in invoiceList"
                      :key="item.invoiceId"
                      :label="item.invoiceNo"
                      :value="item.invoiceUuid"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item class="mt-2" label="当前已关联发票">
                  {{
                    invoiceList
                      .filter((item) => item.bound)
                      .map((item) => item.invoiceNo)
                      .join("\n")
                  }}
                </el-form-item>
              </el-form>
            </div>

            <!-- 底部操作栏 -->
            <template #footer>
              <div class="relative flex justify-between items-center">
                <div>
                  <span
                    v-if="selectedImage"
                    class="text-sm text-[var(--el-color-primary)] mr-2"
                  >
                    {{
                      orderedImageList.findIndex(
                        (img) => img.id === selectedImage?.id
                      ) + 1
                    }}
                    /
                    {{ orderedImageList.length }}
                  </span>
                  <el-button link type="primary" @click="handlePrevImage">
                    <img :src="upIcon" class="w-3 mr-1" />
                    上一张
                  </el-button>
                  <el-button link type="primary" @click="handleNextImage">
                    <img :src="downIcon" class="w-3 mr-1" />
                    下一张
                  </el-button>
                  <el-button link type="primary" @click="handleFullScreen">
                    <svg-icon
                      :icon-class="
                        isFullscreen ? 'fullscreen-exit' : 'fullscreen'
                      "
                      class="w-3 mr-1"
                      color="#407fff"
                    />
                    {{ isFullscreen ? "退出全屏" : "全屏" }}
                  </el-button>
                  <el-button link type="primary" @click="handleReset">
                    <img :src="resetIcon" class="w-3 mr-1" />
                    重置
                  </el-button>
                  <el-button link type="primary" @click="handlePreviewImage">
                    <img :src="previewIcon" class="w-3 mr-1" />
                    预览
                  </el-button>
                  <el-button link type="primary" @click="handleZoomIn">
                    <img :src="zoomInIcon" class="w-3 mr-1" />
                    放大
                  </el-button>
                  <el-button link type="primary" @click="handleZoomOut">
                    <img :src="zoomOutIcon" class="w-3 mr-1" />
                    缩小
                  </el-button>
                  <el-button link type="primary" @click="handleRotateLeft90">
                    <img :src="rotateLeftIcon" class="w-3 mr-1" />
                    向左转90°/30°
                  </el-button>
                  <el-button link type="primary" @click="handleRotateRight90">
                    <img :src="rotateRightIcon" class="w-3 mr-1" />
                    向右转90°/30°
                  </el-button>
                </div>

                <div>
                  <el-button
                    type="primary"
                    link
                    @click="handleShowPrincipleDialog"
                  >
                    <img :src="introIcon" class="w-3 mr-1" />
                    初审规范
                  </el-button>
                  <el-button
                    type="primary"
                    link
                    @click="handleShowShortCutDialog"
                  >
                    <img :src="shortCutIcon" class="w-4 mr-1" />
                    快捷键说明
                  </el-button>
                </div>

                <!-- 影像分类 -->
                <div
                  v-if="hasPermission && selectedImage"
                  class="image-form-container"
                >
                  <el-form
                    inline
                    @keydown.enter.prevent="handleNextImage"
                    @keydown="handleFormKeydown"
                  >
                    <el-form-item label="是否清晰">
                      <el-input
                        ref="clearTypeInputRef"
                        v-model.number="selectedImage.clearType"
                        @change="handleClearTypeChange"
                      />
                    </el-form-item>
                    <el-form-item label="影像分类">
                      <el-input
                        ref="imageTypeInputRef"
                        v-model="selectedImage.imageType"
                        @change="handleImageTypeChange"
                      />
                    </el-form-item>
                    <el-form-item>
                      <el-button
                        :loading="saveLoading"
                        type="primary"
                        @click="debounceSaveAll"
                      >
                        保存
                      </el-button>
                    </el-form-item>
                  </el-form>
                </div>
              </div>
            </template>
          </el-card>
        </div>
      </div>
    </div>

    <!-- 快捷键说明 -->
    <ShortCutDialog v-model="isShowShortCutDialog" />

    <!-- 初审规范 -->
    <PrincipleDialog
      v-model="isShowPrincipleDialog"
      :specification="pageConfig.specification"
    />

    <!-- 特约信息 -->
    <PolicySettingDialog
      v-model="policySettingDialog.visible"
      v-bind="policySettingDialog.params"
      @close="policySettingDialog.close"
    />

    <!-- 影像管理的确认弹窗 -->
    <CheckDialog
      v-model="checkDialog.visible"
      v-bind="checkDialog.params"
      @close="checkDialog.close"
    />

    <!-- 影像上传 -->
    <UploadImageCheckDialog
      v-model="uploadImageCheckDialog.visible"
      v-bind="uploadImageCheckDialog.params"
      @close="uploadImageCheckDialog.close"
      @result="uploadImageCheckDialog.result"
    />
  </div>
</template>

<style lang="scss" scoped>
.grid-layout {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.grid-item {
  display: flex;
  flex-flow: row wrap;
  align-items: center;
}

.item-label {
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.item-value {
  margin-left: 12px;
  font-size: 14px;
  font-weight: bold;
  color: var(--el-text-color-regular);
}

.container-left {
  display: flex;
  flex-direction: column;
  width: 15%;
  height: 100%;

  ::-webkit-scrollbar {
    display: none !important;
    width: 0 !important;
  }

  -ms-overflow-style: none; /* IE 和 Edge */
  scrollbar-width: none; /* Firefox */

  > :deep(.el-card__body) {
    flex: 1;
    padding: 0;
    margin: 0;
    overflow: hidden;
  }

  :deep(.el-collapse-item__header) {
    background-color: var(--el-fill-color);
  }
}

.inner-checkbox {
  position: absolute;
  bottom: 20px;
  left: 10px;
  z-index: 10;
  width: 20px;
  height: 20px;
  transform: scale(1.5); // 添加这行来等比例放大1.5倍
  transform-origin: left top; // 添加这行确保从左上角开始变换
}

.image-container {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
  margin-left: 12px;

  > :deep(.el-card__body) {
    position: relative;
    display: flex;
    flex: 1;
    flex-direction: column;
    padding: 0;
    overflow: hidden;
    background-color: var(--el-fill-color);
  }
}

.draggable-wrapper {
  position: relative;
  display: inline-block;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  cursor: grab;
  user-select: none;
  will-change: transform;
  backface-visibility: hidden;
  transform-style: preserve-3d;

  &.dragging {
    cursor: grabbing;
    transition: none !important;

    * {
      pointer-events: none;
    }
  }

  img {
    max-width: 100%;
    max-height: 100%;
    pointer-events: none;
    object-fit: contain;
    transform: translate3d(0, 0, 0);
    backface-visibility: hidden;
  }
}

.image-table-container {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 10;
}

.image-form-container {
  position: absolute;
  top: -80px;
  left: -10px;
  z-index: 10;
  padding: 8px;
  background-color: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  box-shadow: 0 2px 12px 0 rgb(0 0 0 / 10%);

  :deep(.el-form-item) {
    margin-right: 10px;
    margin-bottom: 0;
  }
}

.button-group {
  :deep(.el-button) {
    border-radius: 8px !important;
  }

  :deep(.el-button + .el-button) {
    margin-left: 0;
  }

  :deep(
    .el-button--primary.is-plain,
    .el-button--primary.is-text,
    .el-button--primary.is-link
  ) {
    --el-button-bg-color: var(--el-color-primary-light-8);
    --el-button-border-color: var(--el-color-primary-light-9);
  }
}

.invoice-container {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 10;
  width: 340px;
  padding: 12px;
  font-size: 14px;
  color: var(--el-text-color-regular);
  background-color: var(--el-fill-color-darker);
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  box-shadow: 0 0 10px 0 rgb(0 0 0 / 10%);

  :deep(.el-form-item--default) {
    margin-bottom: 0;
  }
}
</style>
