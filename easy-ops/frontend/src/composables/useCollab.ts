import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'
import { ref, onUnmounted, watch } from 'vue'
import type { Ref } from 'vue'

/**
 * Yjs 协作编辑 composable — 简化实现：使用 Y.Text 做纯文本协作
 * @param documentIdRef 文档 ID 的响应式引用（当 ID 变化时自动重连）
 */
export function useCollab(documentIdRef: Ref<number | null>) {
  const connected = ref(false)
  const onlineUsers = ref<number[]>([])
  const ydoc = ref<Y.Doc | null>(null)
  const provider = ref<WebsocketProvider | null>(null)
  const ytext = ref<Y.Text | null>(null)
  const remoteContent = ref<string>('')
  let isLocalUpdate = false

  /** 连接 Yjs WebSocket Provider */
  function connect(docId: number) {
    // 先断开旧连接
    disconnect()

    const doc = new Y.Doc()
    ydoc.value = doc
    ytext.value = doc.getText('content')

    // 监听 Y.Text 变化（来自远程的更新）
    ytext.value.observe((event: Y.YTextEvent) => {
      if (isLocalUpdate) return // 忽略本地更新
      const content = ytext.value?.toString() || ''
      remoteContent.value = content
      console.log('[Collab] 📨 远程内容更新, 长度:', content.length)
    })

    const token = localStorage.getItem('token') || ''

    // 直接连接后端
    const serverUrl = `ws://localhost:8081/api/ws/kb-collab`
    const roomName = `${docId}`

    console.log('[Collab] ========== 连接开始 ==========')
    console.log('[Collab] DocID:', docId)

    try {
      provider.value = new WebsocketProvider(serverUrl, roomName, doc, {
        connect: true,
        params: {
          token: token
        }
      })

      provider.value.on('status', (event: { status: string }) => {
        console.log('[Collab] Status:', event.status)
        connected.value = event.status === 'connected'
      })

      provider.value.on('sync', (isSynced: boolean) => {
        console.log('[Collab] Sync:', isSynced)
        if (isSynced && ytext.value) {
          // 同步完成后，获取当前内容
          const content = ytext.value.toString()
          remoteContent.value = content
        }
      })

      provider.value.on('connection-error', (event: any) => {
        console.error('[Collab] Connection error:', event)
      })

    } catch (error) {
      console.error('[Collab] 创建 WebsocketProvider 失败:', error)
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
    ytext.value = null
    connected.value = false
    onlineUsers.value = []
    remoteContent.value = ''
  }

  /** 更新本地内容到 Y.js 文档（编辑器内容变化时调用） */
  function updateContent(content: string) {
    if (!ytext.value) return
    const currentContent = ytext.value.toString()
    if (currentContent === content) return

    isLocalUpdate = true
    ytext.value.delete(0, ytext.value.length)
    ytext.value.insert(0, content)
    isLocalUpdate = false
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
    remoteContent,
    connect,
    disconnect,
    updateContent
  }
}
