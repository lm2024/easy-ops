import { ref, onUnmounted, type Ref } from 'vue'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { WebLinksAddon } from '@xterm/addon-web-links'
import { SearchAddon } from '@xterm/addon-search'
import '@xterm/xterm/css/xterm.css'

export interface ConsoleServerMessage {
  type: string
  text?: string
  cwd?: string
  nodeName?: string
  projectId?: string
  nodeId?: string
  exitCode?: number
  elapsed?: number
  candidates?: string[]
}

export interface ConsoleTerminalOptions {
  onExec: (command: string) => void
  onComplete: (line: string, cursor: number) => void
}

/**
 * 基于 xterm.js 的交互式远程 Shell 行编辑器（历史、Tab 补全、提示符）。
 */
export function useConsoleTerminal(
  containerRef: Ref<HTMLElement | undefined>,
  options: ConsoleTerminalOptions
) {
  const connected = ref(false)
  const cwd = ref('/')
  const nodeName = ref('node')

  let terminal: Terminal | null = null
  let fitAddon: FitAddon | null = null
  let searchAddon: SearchAddon | null = null

  let currentLine = ''
  let cursorIndex = 0
  let history: string[] = []
  let historyIndex = -1
  let savedDraft = ''

  function init() {
    if (!containerRef.value || terminal) return

    terminal = new Terminal({
      cursorBlink: true,
      fontSize: 14,
      fontFamily: "'JetBrains Mono', 'Fira Code', 'Courier New', monospace",
      theme: {
        background: '#0d1117',
        foreground: '#e6edf3',
        cursor: '#e8ff59',
        selectionBackground: '#264f78',
        black: '#484f58',
        red: '#ff7b72',
        green: '#7ee787',
        yellow: '#d29922',
        blue: '#79c0ff',
        magenta: '#d2a8ff',
        cyan: '#a5d6ff',
        white: '#e6edf3',
        brightBlack: '#6e7681',
        brightRed: '#ffa198',
        brightGreen: '#56d364',
        brightYellow: '#e3b341',
        brightBlue: '#79c0ff',
        brightMagenta: '#d2a8ff',
        brightCyan: '#56d4dd',
        brightWhite: '#ffffff'
      },
      convertEol: true,
      scrollback: 5000
    })

    fitAddon = new FitAddon()
    searchAddon = new SearchAddon()
    terminal.loadAddon(fitAddon)
    terminal.loadAddon(new WebLinksAddon())
    terminal.loadAddon(searchAddon)
    terminal.open(containerRef.value)
    fitAddon.fit()

    terminal.writeln('\x1b[90mEasyOps 远程控制台 — 选择节点后点击「连接」\x1b[0m')
    terminal.writeln('\x1b[90m快捷键: Enter 执行 · ↑↓ 历史 · Tab 补全 · Ctrl+C 取消 · Ctrl+L 清屏\x1b[0m')

    terminal.onData(handleInput)
    window.addEventListener('resize', fit)
  }

  function fit() {
    try {
      fitAddon?.fit()
    } catch {
      /* ignore */
    }
  }

  function setConnected(value: boolean) {
    connected.value = value
    if (!value) {
      currentLine = ''
      cursorIndex = 0
      historyIndex = -1
    }
  }

  function focus() {
    terminal?.focus()
  }

  function clearScreen() {
    terminal?.clear()
    if (connected.value) {
      writePrompt()
    }
  }

  function shortCwd(path: string): string {
    if (path.startsWith('/home/')) {
      const idx = path.indexOf('/', 6)
      return idx === -1 ? '~' : '~' + path.substring(idx)
    }
    if (path === '/root') return '~'
    return path.length > 32 ? '…' + path.slice(-30) : path
  }

  function writePrompt() {
    if (!terminal) return
    const prompt = `\x1b[32m${nodeName.value}\x1b[0m:\x1b[34m${shortCwd(cwd.value)}\x1b[0m$ `
    terminal.write('\r\n' + prompt)
    currentLine = ''
    cursorIndex = 0
    historyIndex = -1
    savedDraft = ''
  }

  function refreshLine() {
    if (!terminal) return
    const prompt = `\x1b[32m${nodeName.value}\x1b[0m:\x1b[34m${shortCwd(cwd.value)}\x1b[0m$ `
    terminal.write('\r\x1b[K' + prompt + currentLine)
    const moveBack = currentLine.length - cursorIndex
    if (moveBack > 0) {
      terminal.write(`\x1b[${moveBack}D`)
    }
  }

  function handleInput(data: string) {
    if (!connected.value || !terminal) return

    for (let i = 0; i < data.length; i++) {
      const code = data.charCodeAt(i)
      const ch = data[i]

      if (code === 13 || code === 10) {
        submitLine()
        continue
      }
      if (code === 127 || code === 8) {
        if (cursorIndex > 0) {
          currentLine = currentLine.slice(0, cursorIndex - 1) + currentLine.slice(cursorIndex)
          cursorIndex--
          refreshLine()
        }
        continue
      }
      if (code === 9) {
        options.onComplete(currentLine, cursorIndex)
        continue
      }
      if (code === 3) {
        currentLine = ''
        cursorIndex = 0
        terminal.write('^C')
        writePrompt()
        continue
      }
      if (code === 12) {
        terminal.clear()
        writePrompt()
        continue
      }
      if (code === 27 && data[i + 1] === '[') {
        const seq = data.substring(i, i + 3)
        if (seq === '\x1b[A') {
          navigateHistory(-1)
          i += 2
          continue
        }
        if (seq === '\x1b[B') {
          navigateHistory(1)
          i += 2
          continue
        }
        if (seq === '\x1b[C' && cursorIndex < currentLine.length) {
          cursorIndex++
          terminal.write(ch + data[i + 1] + data[i + 2])
          i += 2
          continue
        }
        if (seq === '\x1b[D' && cursorIndex > 0) {
          cursorIndex--
          terminal.write(ch + data[i + 1] + data[i + 2])
          i += 2
          continue
        }
      }
      if (code >= 32) {
        currentLine = currentLine.slice(0, cursorIndex) + ch + currentLine.slice(cursorIndex)
        cursorIndex++
        refreshLine()
      }
    }
  }

  function submitLine() {
    const cmd = currentLine.trim()
    if (!cmd) {
      writePrompt()
      return
    }
    if (history[history.length - 1] !== cmd) {
      history.push(cmd)
      if (history.length > 200) {
        history.shift()
      }
    }
    historyIndex = -1
    savedDraft = ''
    options.onExec(cmd)
    currentLine = ''
    cursorIndex = 0
  }

  function navigateHistory(delta: number) {
    if (history.length === 0) return
    if (historyIndex === -1 && delta < 0) {
      savedDraft = currentLine
    }
    const next = historyIndex + delta
    if (next < -1 || next >= history.length) return
    historyIndex = next
    currentLine = historyIndex === -1 ? savedDraft : history[historyIndex]
    cursorIndex = currentLine.length
    refreshLine()
  }

  function applyCompletions(candidates: string[]) {
    if (!terminal || candidates.length === 0) return

    const prefix = getCompletionPrefix(currentLine, cursorIndex)
    if (candidates.length === 1) {
      const completion = candidates[0]
      const suffix = completion.startsWith(prefix) ? completion.slice(prefix.length) : completion
      currentLine = currentLine.slice(0, cursorIndex) + suffix + currentLine.slice(cursorIndex)
      cursorIndex += suffix.length
      if (completion.endsWith('/')) {
        /* keep path open */
      } else if (!currentLine.endsWith(' ')) {
        currentLine += ' '
        cursorIndex++
      }
      refreshLine()
      return
    }

    terminal.write('\r\n')
    const columns = 4
    candidates.forEach((item, idx) => {
      terminal!.write(item.padEnd(20, ' '))
      if ((idx + 1) % columns === 0) {
        terminal!.write('\r\n')
      }
    })
    if (candidates.length % columns !== 0) {
      terminal.write('\r\n')
    }
    refreshLine()
  }

  function getCompletionPrefix(line: string, cursor: number): string {
    const head = line.slice(0, cursor)
    const space = head.lastIndexOf(' ')
    return space === -1 ? head : head.slice(space + 1)
  }

  function handleServerMessage(raw: string) {
    if (!terminal) return

    if (raw.trim().startsWith('{')) {
      try {
        const msg = JSON.parse(raw) as ConsoleServerMessage
        switch (msg.type) {
          case 'init':
            nodeName.value = msg.nodeName || 'node'
            cwd.value = msg.cwd || '/'
            terminal.clear()
            terminal.writeln(`\x1b[36m✓ 已连接节点 \x1b[1m${nodeName.value}\x1b[0m`)
            terminal.writeln(`\x1b[90m工作目录: ${cwd.value}\x1b[0m`)
            terminal.writeln('\x1b[90m支持 cd / ls / ll / pwd / cat / grep / tail / ps / docker 等命令，Tab 智能补全\x1b[0m')
            writePrompt()
            return
          case 'output':
            if (msg.text) {
              terminal.write(msg.text)
              if (!msg.text.endsWith('\n')) {
                terminal.write('\r\n')
              }
            }
            return
          case 'meta':
            if (msg.cwd) cwd.value = msg.cwd
            terminal.writeln(
              `\x1b[90m[exit ${msg.exitCode ?? '?'} · ${msg.elapsed ?? '?'}ms]\x1b[0m`
            )
            writePrompt()
            return
          case 'complete':
            applyCompletions(msg.candidates || [])
            return
          case 'clear':
            terminal.clear()
            writePrompt()
            return
          case 'error':
            terminal.writeln(`\x1b[31m${msg.text || '未知错误'}\x1b[0m`)
            writePrompt()
            return
          default:
            break
        }
      } catch {
        /* fall through */
      }
    }
    terminal.write(raw)
  }

  function dispose() {
    window.removeEventListener('resize', fit)
    terminal?.dispose()
    terminal = null
    fitAddon = null
    searchAddon = null
  }

  onUnmounted(dispose)

  return {
    connected,
    cwd,
    nodeName,
    init,
    fit,
    focus,
    clearScreen,
    setConnected,
    handleServerMessage,
    dispose
  }
}
