/**
 * Type declarations for tiptap-markdown module
 * The package doesn't ship its own .d.ts files, so we declare them here.
 */
declare module 'tiptap-markdown' {
  import { Extension } from '@tiptap/core'

  interface MarkdownOptions {
    html?: boolean
    transformPastedText?: boolean
    transformCopiedText?: boolean
    transformHardBreaks?: boolean
    tightLists?: boolean
    tightListClass?: string
    listTypes?: string[]
    markdownit?: Record<string, any>
    linkify?: boolean
    paragraph?: boolean
  }

  export const Markdown: Extension<MarkdownOptions>

  interface MarkdownStorage {
    getMarkdown(): string
    getHTML(): string
  }
}
