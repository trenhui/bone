<script setup>
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import PageAPI from "@/api/page";
import DataAPI from "@/api/data";
import ClaimAPI from "@/api/claim";
import { isEmpty } from "lodash-es";
import EventBus from "@/utils/eventBus";

defineOptions({
  name: "ClaimDetail",
});

const route = useRoute();
const pageConfig = ref({});
const loading = ref(false);
const pageCode = ref(route.query.pageCode);
const displayMode = ref(route.query.displayMode);
const bizIdentityCode = ref(route.query.bizIdentityCode);
const tenantId = ref(route.query.tenantId);
const claimId = ref(route.query.claimId);

const data = ref({});

const initSchema = async () => {
  try {
    loading.value = true;
    if (
      displayMode.value === DisplayModeEnum.CONFIG ||
      displayMode.value === DisplayModeEnum.PREVIEW
    ) {
      pageConfig.value = await PageAPI.getEditingPageSchema(
        pageCode.value,
        displayMode.value,
        bizIdentityCode.value
      );
    } else {
      pageConfig.value = await PageAPI.getPublishingPageSchema(
        pageCode.value,
        displayMode.value,
        bizIdentityCode.value
      );
    }
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const getDataFromLocalStorage = (id) => {
  return JSON.parse(localStorage.getItem(`data-${id}`) || "{}");
};

const getHangupInfo = async () => {
  const res = await ClaimAPI.hangupInfo(claimId.value);
  return res;
};

const initData = async () => {
  try {
    if (displayMode.value !== DisplayModeEnum.CONFIG) {
      if (displayMode.value === DisplayModeEnum.PREVIEW) {
        data.value = {};
        return;
      }
      const localData = getDataFromLocalStorage(claimId.value);
      if (!isEmpty(localData)) {
        console.log("从本地获取数据");
        Object.assign(data.value, localData);
        return;
      }

      console.log("从服务器获取数据");
      data.value = await DataAPI.getForm(claimId.value);

      if (data.value.hangUpStatus === "1") {
        const hangupInfo = await getHangupInfo();
        Object.assign(data.value, hangupInfo);
      }
    }
  } catch (error) {
    console.error(error);
  }
};

const onInit = async () => {
  await Promise.all([initSchema(), initData()]);
};
onInit();

const schemaChange = (currentDisplayMode = displayMode.value) => {
  displayMode.value = currentDisplayMode;
  initSchema();
};

onMounted(() => {
  EventBus.on(`claim:${claimId.value}:refresh`, async () => {
    await initData();
  });
});

onUnmounted(() => {
  EventBus.off(`claim:${claimId.value}:refresh`);
});
</script>

<template>
  <div class="w-full h-full overflow-y-hidden">
    <div
      v-loading.fullscreen.lock="loading"
      element-loading-text="正在加载页面数据..."
      class="w-full h-full overflow-y-auto"
    >
      <PKRender
        :schema="pageConfig.schema"
        :rules="{
          ...pageConfig.pageRules,
          ...pageConfig.tableRules,
        }"
        :display-mode="displayMode"
        v-model:data="data"
        :page-data="{
          claimId: claimId,
          tenantId: tenantId,
        }"
        @update:schema="schemaChange"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
