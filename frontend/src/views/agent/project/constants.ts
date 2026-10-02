/** 构建工具下拉选项，值与后端 build_tool 列约定一致 */
export const BUILD_TOOL_OPTIONS = [
  { label: 'Maven', value: 'MAVEN' },
  { label: 'pnpm', value: 'PNPM' },
  { label: 'Gradle', value: 'GRADLE' },
  { label: 'Go', value: 'GO' }
]

/** 构建工具值转展示文案 */
export const buildToolLabel = (value?: string) => {
  return BUILD_TOOL_OPTIONS.find((item) => item.value === value)?.label || value || ''
}
