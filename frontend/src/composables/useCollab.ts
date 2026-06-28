import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'
import { ref, onUnmounted, watch } from 'vue'
import { saveCollabState, getOnlineUsers } from '../api/knowledge-collab'
import type { Ref } from 'vue'

/**
 * Yjs 协作编辑 composable — 封装 Yjs Doc + y-websocket Provider 的生命周期管理
 * @param documentIdRef 文档 ID 的响应式引用（当 ID 变化时自动重连）
 */
export function useCollab(documentIdRef: Ref<number | null>) {
  const connected = ref(false)
  const onlineUsers = ref<number[]>([])
  const ydoc = ref<Y.Doc | null>(null)
  const provider = ref<WebsocketProvider | null>(null)

  /** 连接 Yjs WebSocket Provider */
  function connect(docId: number) {
    // 先断开旧连接
    disconnect()

    ydoc.value = new Y.Doc()
    const token = localStorage.getItem('token') || ''
    const wsProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:'

    // 关键修复：y-websocket 会把 serverUrl + '/' + roomName 拼接成最终 URL
    // 所以把所有路径参数放在 serverUrl 中，roomName 设为空字符串
    const serverUrl = `${wsProtocol}//${location.host}/ws/kb-collab/${docId}?token=${encodeURIComponent(token)}`
    const roomName = ''

    console.log('[Collab] ========== 连接开始 ==========')
    console.log('[Collab] DocID:', docId)
    console.log('[Collab] Server URL:', serverUrl)
    console.log('[Collab] Room name:', roomName)
    console.log('[Collab] Token:', token ? '存在' : '缺失')
    console.log('[Collab] Token 值:', token)

    try {
      provider.value = new WebsocketProvider(serverUrl, roomName, ydoc.value, {
        connect: true
      })

      console.log('[Collab] WebsocketProvider 创建成功')
      console.log('[Collab] 预期实际连接:', serverUrl)

      // 添加详细的事件监听
      provider.value.on('status', (event: { status: string }) => {
        console.log('[Collab] ✅ Status 事件:', event.status)
        connected.value = event.status === 'connected'
      })

      provider.value.on('sync', (isSynced: boolean) => {
        console.log('[Collab] ✅ Sync 事件:', isSynced)
      })

      provider.value.on('connection-error', (event: any) => {
        console.error('[Collab] ❌ Connection error 事件:', event)
      })

      // 立即检查 WebSocket 实例
      const ws = provider.value.ws
      console.log('[Collab] WebSocket 实例:', ws ? '已创建' : '未创建')

      if (ws) {
        console.log('[Collab] WebSocket readyState:', ws.readyState)
        // 0=CONNECTING, 1=OPEN, 2=CLOSING, 3=CLOSED

        ws.addEventListener('open', () => {
          console.log('[Collab] ✅ WebSocket open 事件触发')
        })

        ws.addEventListener('close', (event: any) => {
          console.log('[Collab] ❌ WebSocket close 事件:', {
            code: event.code,
            reason: event.reason,
            wasClean: event.wasClean
          })
        })

        ws.addEventListener('error', (event: any) => {
          console.error('[Collab] ❌ WebSocket error 事件:', event)
        })

        ws.addEventListener('message', (event: any) => {
          console.log('[Collab] 📨 收到消息, 大小:', event.data?.byteLength || event.data?.length)
        })
      }

      // 3秒后检查最终状态
      setTimeout(() => {
        console.log('[Collab] ========== 3秒后状态检查 ==========')
        console.log('[Collab] connected (ref):', connected.value)
        console.log('[Collab] provider.wsconnected:', provider.value?.wsconnected)
        console.log('[Collab] provider.synced:', provider.value?.synced)
        console.log('[Collab] WebSocket 实例:', provider.value?.ws ? '存在' : '不存在')
        if (provider.value?.ws) {
          console.log('[Collab] WebSocket readyState:', provider.value.ws.readyState)
          console.log('[Collab] WebSocket readyState 含义:',
            provider.value.ws.readyState === 0 ? 'CONNECTING' :
            provider.value.ws.readyState === 1 ? 'OPEN' :
            provider.value.ws.readyState === 2 ? 'CLOSING' :
            provider.value.ws.readyState === 3 ? 'CLOSED' : '未知')
        }
        console.log('[Collab] ================================')
      }, 3000)

    } catch (error) {
      console.error('[Collab] ❌ 创建 WebsocketProvider 失败:', error)
    }
  }

  /** 断开 Yjs WebSocket Provider */
  function disconnect() {
    if (provider.value) {
      provider.value.disconnect()
      provider.value.destroy()
    }
    if (ydoc.value) {
      ydoc.value.destroy()
    }
    ydoc.value = null
    provider.value = null
    connected.value = false
    onlineUsers.value = []
  }

  /** 保存协作状态到服务器（将 Yjs Doc 内容转为 Markdown 并通过 REST API 保存） */
  async function saveState() {
    const docId = documentIdRef.value
    if (!docId) return
    try {
      await saveCollabState(docId)
    } catch {
      // 保存失败静默处理，不阻断用户操作
    }
  }

  /** 手动获取在线用户列表（从服务器 REST API 获取，作为 WebSocket awareness 的补充） */
  async function refreshOnlineUsers() {
    const docId = documentIdRef.value
    if (!docId) return
    try {
      const res = await getOnlineUsers(docId)
      onlineUsers.value = res.data || []
    } catch {
      // 静默处理
    }
  }

  // 监听 documentId 变化，自动重连
  watch(documentIdRef, (newId, oldId) => {
    if (oldId && connected.value) {
      disconnect()
    }
    if (newId) {
      connect(newId)
    }
  })

  onUnmounted(() => {
    disconnect()
  })

  return {
    connected,
    onlineUsers,
    ydoc,
    provider,
    connect,
    disconnect,
    saveState,
    refreshOnlineUsers
  }
}
