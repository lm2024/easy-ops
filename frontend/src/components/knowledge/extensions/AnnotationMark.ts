import { Mark } from '@tiptap/core'

/**
 * AnnotationMark — 自定义 Tiptap Mark 扩展
 * 为选中的文字添加 data-annotation-id 属性，支持批注高亮标记
 */
export interface AnnotationMarkOptions {
  /** HTML 属性名，默认 'data-annotation-id' */
  HTMLAttributes: Record<string, string>
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    annotationMark: {
      /** 为当前选中文字添加批注标记 */
      addAnnotation: (annotationId: string) => ReturnType
      /** 移除指定批注标记 */
      removeAnnotation: (annotationId: string) => ReturnType
      /** 移除所有批注标记 */
      removeAllAnnotations: () => ReturnType
    }
  }
}

export const AnnotationMark = Mark.create<AnnotationMarkOptions>({
  name: 'annotationMark',

  addOptions() {
    return {
      HTMLAttributes: {},
    }
  },

  addAttributes() {
    return {
      'data-annotation-id': {
        default: null,
        parseHTML: (element: HTMLElement) => element.getAttribute('data-annotation-id'),
        renderHTML: (attributes: Record<string, any>) => {
          if (!attributes['data-annotation-id']) {
            return {}
          }
          return {
            'data-annotation-id': attributes['data-annotation-id'] as string,
          }
        },
      },
    }
  },

  parseHTML() {
    return [
      {
        tag: 'span[data-annotation-id]',
      },
    ]
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'span',
      {
        ...this.options.HTMLAttributes,
        ...HTMLAttributes,
        class: 'annotation-mark',
        style: 'background: rgba(245, 158, 11, 0.3); cursor: pointer; position: relative;',
      },
      0,
    ]
  },

  addCommands() {
    return {
      addAnnotation:
        (annotationId: string) =>
        ({ commands }) => {
          return commands.setMark(this.name, { 'data-annotation-id': annotationId })
        },
      removeAnnotation:
        (annotationId: string) =>
        ({ state, tr, dispatch }) => {
          // 遍历文档，移除包含指定 annotationId 的 mark
          let modified = false
          state.doc.descendants((node, pos) => {
            if (!node.isInline || !node.marks) return
            const mark = node.marks.find(
              (m) =>
                m.type.name === this.name &&
                m.attrs['data-annotation-id'] === annotationId
            )
            if (mark) {
              tr.removeMark(pos, pos + node.nodeSize, mark)
              modified = true
            }
          })
          if (modified && dispatch) {
            dispatch(tr)
            return true
          }
          return false
        },
      removeAllAnnotations:
        () =>
        ({ commands }) => {
          return commands.unsetMark(this.name)
        },
    }
  },

  addInputRules() {
    return []
  },

  addPasteRules() {
    return []
  },
})
