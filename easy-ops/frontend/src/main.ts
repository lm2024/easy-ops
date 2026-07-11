import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import App from './App.vue'
import router from './router'
import { useAppStore } from './stores/app'
import 'ant-design-vue/dist/reset.css'
import './styles/global.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(Antd)

useAppStore(pinia).initTheme()

app.mount('#app')
