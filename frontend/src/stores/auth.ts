import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserModel } from '../types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const user = ref<UserModel | null>(null)

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUser(u: UserModel) {
    user.value = u
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
  }

  return { token, user, setToken, setUser, logout }
})
