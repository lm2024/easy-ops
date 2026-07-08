import { theme } from 'ant-design-vue'
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'

/**
 * EasyOps 全局主题 — 与登录页控制平面一致
 */
export const easyOpsTheme: ThemeConfig = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: '#e8ff59',
    colorBgLayout: '#0a0a0b',
    colorBgContainer: '#141414',
    colorBgElevated: '#1c1c1e',
    colorText: '#f4f4f5',
    colorTextSecondary: '#a1a1aa',
    colorTextTertiary: '#71717a',
    colorBorder: '#2a2a2a',
    colorBorderSecondary: '#1f1f1f',
    colorLink: '#38bdf8',
    colorTextLightSolid: '#0a0a0b',
    borderRadius: 6,
    fontFamily: "'Geist', 'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
  },
  components: {
    Layout: {
      headerBg: '#141414',
      bodyBg: '#0a0a0b',
      siderBg: '#0a0a0b'
    },
    Menu: {
      darkItemColor: 'rgba(255, 255, 255, 0.75)',
      darkSubMenuItemColor: 'rgba(255, 255, 255, 0.75)',
      darkGroupTitleColor: 'rgba(255, 255, 255, 0.45)',
      darkItemBg: '#0a0a0b',
      darkSubMenuItemBg: '#0a0a0b',
      darkItemSelectedBg: 'rgba(232, 255, 89, 0.12)',
      darkItemHoverBg: 'rgba(255, 255, 255, 0.06)',
      darkItemSelectedColor: '#e8ff59',
      itemColor: 'rgba(255, 255, 255, 0.75)',
      itemHoverColor: '#ffffff'
    },
    Table: {
      headerBg: '#1a1a1c',
      headerColor: '#f4f4f5',
      rowHoverBg: '#1f1f23',
      borderColor: '#2a2a2a',
      colorBgContainer: '#141414'
    },
    Card: {
      colorBgContainer: '#141414',
      colorBorderSecondary: '#2a2a2a'
    },
    Input: {
      colorBgContainer: '#0f0f10',
      activeBorderColor: 'rgba(232, 255, 89, 0.5)',
      hoverBorderColor: '#404040'
    },
    Select: {
      colorBgContainer: '#0f0f10',
      optionSelectedBg: 'rgba(232, 255, 89, 0.12)'
    },
    Button: {
      primaryColor: '#0a0a0b',
      colorTextLightSolid: '#0a0a0b'
    }
  }
}
