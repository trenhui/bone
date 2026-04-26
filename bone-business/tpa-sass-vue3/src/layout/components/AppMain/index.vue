<template>
  <section class="app-main-template" :style="{ height: minHeight }">
    <router-view>
      <template #default="{ Component, route }">
        <transition
          enter-active-class="animate__animated animate__fadeIn"
          mode="out-in"
        >
          <keep-alive :include="cachedViews">
            <component :is="Component" :key="route.path" />
          </keep-alive>
        </transition>
      </template>
    </router-view>
  </section>
</template>

<script setup lang="ts">
import { useSettingsStore, useTagsViewStore } from "@/store";
import variables from "@/styles/variables.module.scss";
import { isQiankun } from "@/utils/qiankun";

const route = useRoute();
const onlyMain = route.query.onlyMain === "true";

const cachedViews = computed(() => useTagsViewStore().cachedViews); // 缓存页面集合
const minHeight = computed(() => {
  if (isQiankun() || onlyMain) {
    return `calc(100vh - ${variables["tags-view-height"]})`;
  } else {
    if (useSettingsStore().tagsView) {
      return `calc(100vh - ${variables["navbar-height"]} - ${variables["tags-view-height"]})`;
    } else {
      return `calc(100vh - ${variables["navbar-height"]})`;
    }
  }
});
</script>

<style lang="scss" scoped>
.app-main-template {
  position: relative;
  padding: 8px;
  overflow-y: auto;
  background-color: var(--el-bg-color-page);
}
</style>
