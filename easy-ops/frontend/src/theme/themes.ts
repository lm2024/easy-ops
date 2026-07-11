import { theme } from 'ant-design-vue'
import type { ThemeConfig } from 'ant-design-vue/es/config-provider/context'

export type ThemeMode = 'dark' | 'light'

const fontFamily = "'Geist', 'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"

/** 暗色主题（默认） */
export const darkTheme: ThemeConfig = {
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
    borderRadius: 8,
    fontFamily,
    motionDurationMid: '0.22s',
    motionEaseInOut: 'cubic-bezier(0.25, 0.1, 0.25, 1)'
  },
  components: {
    Layout: { headerBg: '#141414', bodyBg: '#0a0a0b', siderBg: '#0a0a0b' },
    Menu: {
      darkItemColor: 'rgba(255, 255, 255, 0.75)',
      darkSubMenuItemColor: 'rgba(255, 255, 255, 0.75)',
      darkGroupTitleColor: 'rgba(255, 255, 255, 0.45)',
      darkItemBg: '#0a0a0b',
      darkSubMenuItemBg: '#0a0a0b',
      darkItemSelectedBg: 'rgba(232, 255, 89, 0.12)',
      darkItemHoverBg: 'rgba(255, 255, 255, 0.06)',
      darkItemSelectedColor: '#e8ff59'
    },
    Table: {
      headerBg: '#1a1a1c',
      headerColor: '#f4f4f5',
      rowHoverBg: '#1f1f23',
      borderColor: '#2a2a2a',
      colorBgContainer: '#141414'
    },
    Card: { colorBgContainer: '#141414', colorBorderSecondary: '#2a2a2a' },
    Input: {
      colorBgContainer: '#0f0f10',
      colorText: '#f4f4f5',
      colorTextPlaceholder: '#71717a',
      activeBorderColor: 'rgba(232, 255, 89, 0.55)',
      hoverBorderColor: '#404040'
    },
    Select: {
      colorBgContainer: '#0f0f10',
      colorText: '#f4f4f5',
      optionSelectedBg: 'rgba(232, 255, 89, 0.12)'
    },
    Button: { primaryColor: '#0a0a0b', colorTextLightSolid: '#0a0a0b' }
  }
} as ThemeConfig

/** 亮色主题 */
export const lightTheme: ThemeConfig = {
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: '#65a30d',
    colorBgLayout: '#f5f5f7',
    colorBgContainer: '#ffffff',
    colorBgElevated: '#ffffff',
    colorText: '#1d1d1f',
    colorTextSecondary: '#6e6e73',
    colorTextTertiary: '#86868b',
    colorBorder: '#d2d2d7',
    colorBorderSecondary: '#e8e8ed',
    colorLink: '#2563eb',
    colorTextLightSolid: '#ffffff',
    borderRadius: 8,
    fontFamily,
    motionDurationMid: '0.22s',
    motionEaseInOut: 'cubic-bezier(0.25, 0.1, 0.25, 1)'
  },
  components: {
    Layout: { headerBg: '#ffffff', bodyBg: '#f5f5f7', siderBg: '#fbfbfc' },
    Menu: {
      itemColor: '#3a3a3c',
      itemHoverColor: '#1d1d1f',
      itemSelectedColor: '#65a30d',
      itemSelectedBg: 'rgba(101, 163, 13, 0.1)',
      itemHoverBg: 'rgba(0, 0, 0, 0.04)',
      itemBg: '#fbfbfc',
      subMenuItemBg: '#fbfbfc'
    },
    Table: {
      headerBg: '#f5f5f7',
      headerColor: '#1d1d1f',
      rowHoverBg: '#f5f5f7',
      borderColor: '#e8e8ed',
      colorBgContainer: '#ffffff'
    },
    Card: { colorBgContainer: '#ffffff', colorBorderSecondary: '#e8e8ed' },
    Input: {
      colorBgContainer: '#ffffff',
      colorText: '#1d1d1f',
      colorTextPlaceholder: '#86868b',
      activeBorderColor: '#65a30d',
      hoverBorderColor: '#aeaeb2'
    },
    Select: {
      colorBgContainer: '#ffffff',
      colorText: '#1d1d1f',
      optionSelectedBg: 'rgba(101, 163, 13, 0.1)'
    },
    Button: { primaryColor: '#ffffff', colorTextLightSolid: '#ffffff' }
  }
} as ThemeConfig

export function getAntTheme(mode: ThemeMode): ThemeConfig {
  return mode === 'light' ? lightTheme : darkTheme
}

/** @deprecated 使用 getAntTheme(themeMode) */
export const easyOpsTheme = darkTheme
