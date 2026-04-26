<script setup>
import ProcessPageAPI from "@/api/processPage";

defineOptions({
  name: "NewSign",
});

const route = useRoute();
const schema = ref({});
const loading = ref(false);
const displayMode = ref(route.query.displayMode);
const bizIdentityCode = ref(route.query.bizIdentityCode);
const uploadComponentId = ref("");
const data = ref({});

const initSchema = async () => {
  try {
    const res = await ProcessPageAPI.getNewSignPage(
      displayMode.value,
      bizIdentityCode.value
    );
    schema.value = res?.page;
    uploadComponentId.value = res?.uploadDataId;
    console.log(uploadComponentId.value);
  } catch (error) {
    console.error(error);
  }
};

const onInit = async () => {
  loading.value = true;
  await initSchema();
  loading.value = false;
};
onInit();

const schemaChange = (currentDisplayMode = displayMode.value) => {
  displayMode.value = currentDisplayMode;
  initSchema();
};
</script>

<template>
  <div class="w-full h-full">
    <PKRender
      :schema="schema"
      :display-mode="displayMode"
      v-model:data="data"
      @update:schema="schemaChange"
      :page-data="{
        uploadComponentId,
      }"
    />
  </div>
</template>

<style lang="scss" scoped></style>
