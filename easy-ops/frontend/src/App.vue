<template>
  <a-config-provider :theme="antTheme">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAppStore } from './stores/app'
import { getAntTheme } from './theme/themes'

const appStore = useAppStore()
const antTheme = computed(() => getAntTheme(appStore.themeMode))
</script>
