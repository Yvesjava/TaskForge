import { describe, expect, it } from 'vitest'
import { agentTaskStatusLabel, agentTaskStatusTagType, formatCostMs } from './constants'

describe('agent task constants', () => {
  it('maps known status to label', () => {
    expect(agentTaskStatusLabel('PENDING')).toBe('待执行')
    expect(agentTaskStatusLabel('WAITING_ACCEPTANCE')).toBe('待验收')
    expect(agentTaskStatusLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(agentTaskStatusLabel(undefined)).toBe('')
  })

  it('maps known status to tag type', () => {
    expect(agentTaskStatusTagType('COMPLETED')).toBe('success')
    expect(agentTaskStatusTagType('FAILED')).toBe('danger')
    expect(agentTaskStatusTagType('UNKNOWN')).toBe('info')
  })

  it('formats cost milliseconds', () => {
    expect(formatCostMs(undefined)).toBe('')
    expect(formatCostMs(500)).toBe('500ms')
    expect(formatCostMs(1500)).toBe('1.5s')
    expect(formatCostMs(90_000)).toBe('1m30s')
  })
})
