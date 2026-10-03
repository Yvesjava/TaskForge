/** 多仓任务项目引用（对应后端 TaskProjectRef） */
export interface TaskProjectRef {
  code: string
  baseBranch: string
  subDir: string
}

/** 任务文档 Front Matter（对应后端 TaskFrontMatter） */
export interface TaskFrontMatter {
  taskId?: string
  title?: string
  targetBranch?: string
  timeoutMinutes?: number
  priority?: number
  dependsOnTaskId?: number
  repoUrl?: string
  baseBranch?: string
  projects?: TaskProjectRef[]
}

/** 正文标准小节 */
export interface TaskDocumentSections {
  goal: string
  plan: string
  steps: string
  criteria: string
}

export interface ParsedTaskDocument {
  frontMatter: TaskFrontMatter
  sections: TaskDocumentSections
}

export type TaskDocumentSectionKey = keyof TaskDocumentSections

const SECTION_HEADINGS: Record<TaskDocumentSectionKey, string> = {
  goal: '## 需求目标与上下文',
  plan: '## 任务执行计划',
  steps: '## 验收步骤',
  criteria: '## 验收标准'
}

const SECTION_KEYS: TaskDocumentSectionKey[] = ['goal', 'plan', 'steps', 'criteria']

interface Heading {
  key?: TaskDocumentSectionKey
  level: number
  index: number
}

/** 解析任务文档，返回类型化 Front Matter 与正文小节 */
export function parseTaskDocument(markdown: string): ParsedTaskDocument {
  const { frontMatterText, body } = splitFrontMatter(markdown)
  return {
    frontMatter: parseFrontMatter(frontMatterText),
    sections: parseSections(body)
  }
}

/** 由 Front Matter 与正文小节重新序列化任务文档 */
export function buildTaskDocument(
  frontMatter: TaskFrontMatter,
  sections: TaskDocumentSections
): string {
  return `---\n${stringifyFrontMatter(frontMatter)}\n---\n\n${buildBody(sections)}\n`
}

function splitFrontMatter(markdown: string): { frontMatterText: string; body: string } {
  const text = (markdown || '').replace(/^\uFEFF/, '')
  const lines = text.split(/\r?\n/)
  let opening = -1
  for (let i = 0; i < lines.length; i++) {
    const trimmed = lines[i].trim()
    if (!trimmed) {
      continue
    }
    if (trimmed === '---') {
      opening = i
      break
    }
    return { frontMatterText: '', body: text }
  }
  if (opening < 0) {
    return { frontMatterText: '', body: text }
  }
  for (let i = opening + 1; i < lines.length; i++) {
    if (lines[i].trim() === '---') {
      return {
        frontMatterText: lines.slice(opening + 1, i).join('\n'),
        body: lines.slice(i + 1).join('\n')
      }
    }
  }
  return { frontMatterText: '', body: text }
}

function parseSections(body: string): TaskDocumentSections {
  const lines = (body || '').split(/\r?\n/)
  const headings = findHeadings(lines)
  const result: TaskDocumentSections = { goal: '', plan: '', steps: '', criteria: '' }

  for (const key of SECTION_KEYS) {
    const index = headings.findIndex((heading) => heading.key === key)
    if (index < 0) {
      continue
    }
    const heading = headings[index]
    let end = lines.length
    for (let j = index + 1; j < headings.length; j++) {
      const candidate = headings[j]
      if (candidate.key || candidate.level <= heading.level) {
        end = candidate.index
        break
      }
    }
    result[key] = lines
      .slice(heading.index + 1, end)
      .join('\n')
      .trim()
  }
  return result
}

function findHeadings(lines: string[]): Heading[] {
  const headings: Heading[] = []
  let inFence = false
  const fencePattern = /^\s{0,3}(```+|~~~+)/
  const headingPattern = /^\s{0,3}(#{1,6})\s+(.+?)\s*#*\s*$/

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (fencePattern.test(line)) {
      inFence = !inFence
      continue
    }
    if (inFence) {
      continue
    }
    const match = line.match(headingPattern)
    if (!match) {
      continue
    }
    headings.push({
      key: recognizeSection(match[2]),
      level: match[1].length,
      index: i
    })
  }
  return headings
}

function recognizeSection(title: string): TaskDocumentSectionKey | undefined {
  const normalized = title.replace(/`/g, '').toLowerCase()
  if (normalized.includes('需求目标') || normalized.includes('目标与上下文')) {
    return 'goal'
  }
  if (normalized.includes('执行计划') || normalized.includes('execution plan')) {
    return 'plan'
  }
  if (normalized.includes('验收步骤') || normalized.includes('verification steps')) {
    return 'steps'
  }
  if (normalized.includes('验收标准') || normalized.includes('acceptance criteria')) {
    return 'criteria'
  }
  return undefined
}

function buildBody(sections: TaskDocumentSections): string {
  return SECTION_KEYS.map((key) => {
    const content = (sections[key] || '').trim()
    return `${SECTION_HEADINGS[key]}\n\n${content}`
  }).join('\n\n')
}

function parseFrontMatter(yaml: string): TaskFrontMatter {
  const raw = parseSimpleYaml(yaml)
  return {
    taskId: asString(raw.taskId),
    title: asString(raw.title),
    targetBranch: asString(raw.targetBranch),
    timeoutMinutes: asNumber(raw.timeoutMinutes),
    priority: asNumber(raw.priority),
    dependsOnTaskId: asNumber(raw.dependsOnTaskId),
    repoUrl: asString(raw.repoUrl),
    baseBranch: asString(raw.baseBranch),
    projects: asProjects(raw.projects)
  }
}

function stringifyFrontMatter(frontMatter: TaskFrontMatter): string {
  const lines: string[] = []
  const push = (key: string, value: unknown) => {
    if (value !== undefined && value !== null) {
      lines.push(`${key}: ${yamlScalar(value)}`)
    }
  }

  push('taskId', frontMatter.taskId)
  push('title', frontMatter.title)
  push('targetBranch', frontMatter.targetBranch)
  push('timeoutMinutes', frontMatter.timeoutMinutes)
  push('priority', frontMatter.priority)
  push('dependsOnTaskId', frontMatter.dependsOnTaskId)

  if (frontMatter.projects && frontMatter.projects.length > 0) {
    lines.push('projects:')
    for (const project of frontMatter.projects) {
      lines.push(`  - code: ${yamlScalar(project.code)}`)
      lines.push(`    baseBranch: ${yamlScalar(project.baseBranch)}`)
      lines.push(`    subDir: ${yamlScalar(project.subDir)}`)
    }
  } else {
    push('repoUrl', frontMatter.repoUrl)
    push('baseBranch', frontMatter.baseBranch)
  }
  return lines.join('\n')
}

/**
 * 解析任务文档所需的 YAML Front Matter 子集：
 * 顶层标量字段 + projects 块状列表。
 */
function parseSimpleYaml(yaml: string): Record<string, unknown> {
  const result: Record<string, unknown> = {}
  const lines = (yaml || '').split(/\r?\n/)
  let i = 0
  const topLevelPattern = /^([A-Za-z0-9_-]+):(.*)$/

  while (i < lines.length) {
    const line = lines[i]
    if (isBlankOrComment(line)) {
      i++
      continue
    }
    const match = line.match(topLevelPattern)
    if (!match) {
      i++
      continue
    }

    const key = match[1]
    const rawValue = match[2].trim()
    if (key === 'projects') {
      i++
      const projects: Record<string, string>[] = []
      while (i < lines.length) {
        if (isBlankOrComment(lines[i])) {
          i++
          continue
        }
        const item = lines[i].match(/^\s*-\s*(.*)$/)
        if (!item) {
          break
        }

        const project: Record<string, string> = {}
        if (item[1].trim()) {
          assignInlineMapping(project, item[1].trim())
        }
        i++

        while (i < lines.length) {
          const continuation = lines[i]
          if (isBlankOrComment(continuation)) {
            i++
            continue
          }
          const field = continuation.match(/^(\s+)([A-Za-z0-9_-]+):(.*)$/)
          if (!field) {
            break
          }
          const value = parseScalar(field[3].trim())
          project[field[2]] = value === null || value === undefined ? '' : String(value)
          i++
        }
        projects.push(project)
      }
      result.projects = projects
      continue
    }

    result[key] = rawValue === '' ? null : parseScalar(rawValue)
    i++
  }
  return result
}

function assignInlineMapping(target: Record<string, string>, text: string) {
  for (const part of text.split(',')) {
    const match = part.match(/^\s*([A-Za-z0-9_-]+):\s*(.*)$/)
    if (match) {
      const value = parseScalar(match[2].trim())
      target[match[1]] = value === null || value === undefined ? '' : String(value)
    }
  }
}

function parseScalar(raw: string): string | number | boolean | null {
  const text = raw.trim()
  if (!text) {
    return null
  }
  if (
    (text.startsWith('"') && text.endsWith('"')) ||
    (text.startsWith("'") && text.endsWith("'"))
  ) {
    const inner = text.slice(1, -1)
    if (text.startsWith('"')) {
      try {
        return JSON.parse(text)
      } catch {
        return inner
      }
    }
    return inner.replace(/''/g, "'")
  }
  if (text === 'null' || text === '~') {
    return null
  }
  if (text === 'true') {
    return true
  }
  if (text === 'false') {
    return false
  }
  const numeric = text.replace(/_/g, '')
  if (/^-?\d+$/.test(numeric)) {
    const number = Number(numeric)
    if (Number.isSafeInteger(number)) {
      return number
    }
  }
  return text
}

function yamlScalar(value: unknown): string {
  if (value === null || value === undefined) {
    return 'null'
  }
  if (typeof value === 'number') {
    return String(value)
  }
  if (typeof value === 'boolean') {
    return value ? 'true' : 'false'
  }
  return JSON.stringify(String(value))
}

function isBlankOrComment(line: string): boolean {
  const trimmed = line.trim()
  return !trimmed || trimmed.startsWith('#')
}

function asString(value: unknown): string | undefined {
  return value === null || value === undefined ? undefined : String(value)
}

function asNumber(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const number = Number(value)
    return Number.isFinite(number) ? number : undefined
  }
  return undefined
}

function asProjects(value: unknown): TaskProjectRef[] | undefined {
  if (!Array.isArray(value)) {
    return undefined
  }
  const projects: TaskProjectRef[] = []
  for (const item of value) {
    if (!item || typeof item !== 'object') {
      continue
    }
    const record = item as Record<string, unknown>
    projects.push({
      code: String(record.code ?? ''),
      baseBranch: String(record.baseBranch ?? ''),
      subDir: String(record.subDir ?? '')
    })
  }
  return projects
}
