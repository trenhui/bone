<script setup>
import { createProps } from "./block";
defineOptions({
  name: "Block",
});

const props = defineProps(createProps());
</script>

<template>
  <div class="p-4">
    <div v-for="item in body" :key="item.id" class="not-last:mb-1">
      <component :is="item.type" v-bind="item" />
    </div>
  </div>
</template>

<style scoped></style>
