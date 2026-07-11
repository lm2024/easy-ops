import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ThemeMode } from '../theme/themes'

const THEME_STORAGE_KEY = 'easyops-theme'

function readStoredTheme(): ThemeMode {
  const saved = localStorage.getItem(THEME_STORAGE_KEY)
  return saved === 'light' ? 'light' : 'dark'
}

function applyThemeToDocument(mode: ThemeMode) {
  document.documentElement.setAttribute('data-theme', mode)
  document.documentElement.style.colorScheme = mode
}

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const currentMenu = ref('nodes')
  const themeMode = ref<ThemeMode>(readStoredTheme())

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setMenu(menu: string) {
    currentMenu.value = menu
  }

  function setTheme(mode: ThemeMode) {
    themeMode.value = mode
    localStorage.setItem(THEME_STORAGE_KEY, mode)
    applyThemeToDocument(mode)
  }

  function toggleTheme() {
    setTheme(themeMode.value === 'dark' ? 'light' : 'dark')
  }

  function initTheme() {
    applyThemeToDocument(themeMode.value)
  }

  return {
    sidebarCollapsed,
    currentMenu,
    themeMode,
    toggleSidebar,
    setMenu,
    setTheme,
    toggleTheme,
    initTheme
  }
})
