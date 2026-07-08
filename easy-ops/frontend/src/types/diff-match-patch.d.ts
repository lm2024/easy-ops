declare module 'diff-match-patch' {
  class DiffMatchPatch {
    Diff_Timeout: number
    Line_H: number
    diff_main(text1: string, text2: string, cursorPos?: number | boolean): [number, string][]
    diff_cleanupSemantic(diffs: [number, string][]): void
    diff_linesToChars_(text1: string, text2: string): { chars1: string; chars2: string; lineArray: string[] }
    diff_charsToLines_(diffs: [number, string][], lineArray: string[]): void
    patch_make(text1: string | [number, string][], text2?: string | [number, string][], diffs?: [number, string][]): any[]
    patch_apply(patches: any[], text: string): [string, boolean[]]
  }
  export = DiffMatchPatch
}
