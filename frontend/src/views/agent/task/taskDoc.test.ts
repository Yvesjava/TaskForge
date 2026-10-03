import { describe, expect, it } from 'vitest'
import { buildTaskDocument, parseTaskDocument } from './taskDoc'

describe('taskDoc', () => {
  it('round-trips a single-repo task document', () => {
    const document = [
      '---',
      'taskId: "TASK-20261001-001"',
      'title: "实现项目 CRUD"',
      'targetBranch: "feat/task-001"',
      'timeoutMinutes: 30',
      'priority: 10',
      'repoUrl: "git@host:team/backend.git"',
      'baseBranch: "main"',
      '---',
      '## 需求目标与上下文',
      'goal content',
      '## 任务执行计划',
      '- [ ] step 1',
      '## 验收步骤',
      'mvn test',
      '## 验收标准',
      '- [ ] all green'
    ].join('\n')

    const parsed = parseTaskDocument(document)
    expect(parsed.frontMatter.taskId).toBe('TASK-20261001-001')
    expect(parsed.frontMatter.title).toBe('实现项目 CRUD')
    expect(parsed.frontMatter.targetBranch).toBe('feat/task-001')
    expect(parsed.frontMatter.timeoutMinutes).toBe(30)
    expect(parsed.frontMatter.priority).toBe(10)
    expect(parsed.frontMatter.repoUrl).toBe('git@host:team/backend.git')
    expect(parsed.frontMatter.baseBranch).toBe('main')
    expect(parsed.sections.goal).toBe('goal content')
    expect(parsed.sections.steps).toBe('mvn test')

    const rebuilt = buildTaskDocument(parsed.frontMatter, parsed.sections)
    const reparsed = parseTaskDocument(rebuilt)
    expect(reparsed.frontMatter.taskId).toBe('TASK-20261001-001')
    expect(reparsed.sections.goal).toBe('goal content')
    expect(reparsed.sections.criteria).toContain('all green')
  })

  it('round-trips multi-repo projects', () => {
    const frontMatter = {
      taskId: 'TASK-20261001-002',
      title: 'multi repo',
      targetBranch: 'feat/multi',
      timeoutMinutes: 30,
      projects: [
        { code: 'backend-service', baseBranch: 'main', subDir: 'backend' },
        { code: 'web-portal', baseBranch: 'develop', subDir: 'frontend' }
      ]
    }
    const sections = {
      goal: 'goal',
      plan: 'plan',
      steps: 'steps',
      criteria: 'criteria'
    }

    const parsed = parseTaskDocument(buildTaskDocument(frontMatter, sections))
    expect(parsed.frontMatter.projects).toEqual([
      { code: 'backend-service', baseBranch: 'main', subDir: 'backend' },
      { code: 'web-portal', baseBranch: 'develop', subDir: 'frontend' }
    ])
    expect(parsed.sections).toEqual(sections)
  })

  it('tolerates BOM and CRLF line endings', () => {
    const document =
      '\uFEFF---\r\ntitle: "crlf"\r\ntargetBranch: "feat/x"\r\ntimeoutMinutes: 30\r\nrepoUrl: "git@h:r.git"\r\nbaseBranch: "main"\r\n---\r\n\r\n## 验收步骤\r\n\r\nrun test\r\n'
    const parsed = parseTaskDocument(document)
    expect(parsed.frontMatter.title).toBe('crlf')
    expect(parsed.sections.steps).toBe('run test')
  })
})
