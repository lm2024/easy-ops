import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const currentMenu = ref('nodes')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setMenu(menu: string) {
    currentMenu.value = menu
  }

  return { sidebarCollapsed, currentMenu, toggleSidebar, setMenu }
})
