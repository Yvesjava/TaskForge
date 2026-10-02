// 校验前端本地开发环境变量约定，确保 `pnpm dev` 指向后端 48080。
// 运行：pnpm check:env
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')

const EXPECTED_PORT = '3000'
const EXPECTED_API_BASE = 'http://localhost:48080/admin-api'

function parseEnvFile(file) {
  const content = readFileSync(file, 'utf8')
  const env = {}
  for (const line of content.split(/\r?\n/)) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('#')) continue
    const index = trimmed.indexOf('=')
    if (index === -1) continue
    env[trimmed.slice(0, index).trim()] = trimmed.slice(index + 1).trim()
  }
  return env
}

const problems = []
const env = parseEnvFile(resolve(root, '.env.local.example'))

if (env.VITE_PORT !== EXPECTED_PORT) {
  problems.push(`VITE_PORT 必须为 ${EXPECTED_PORT}，当前为 ${env.VITE_PORT ?? '<缺失>'}`)
}

const apiBase = `${env.VITE_BASE_URL ?? ''}${env.VITE_API_URL ?? ''}`
if (apiBase !== EXPECTED_API_BASE) {
  problems.push(
    `VITE_BASE_URL + VITE_API_URL 必须为 ${EXPECTED_API_BASE}，当前为 ${apiBase || '<缺失>'}`
  )
}

const devScript = JSON.parse(readFileSync(resolve(root, 'package.json'), 'utf8')).scripts?.dev ?? ''
if (!devScript.includes('--mode env.local')) {
  problems.push(`package.json 的 dev 脚本需使用 --mode env.local，当前为 "${devScript}"`)
}

if (problems.length > 0) {
  console.error('[check-env] 前端环境变量约定校验失败：')
  for (const problem of problems) console.error(`  - ${problem}`)
  process.exit(1)
}

console.log(
  `[check-env] OK: pnpm dev -> http://localhost:${env.VITE_PORT}，API 基地址 ${apiBase}`
)
