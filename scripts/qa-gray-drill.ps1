<#
.SYNOPSIS
TaskForge gray-run and rollback drill health check (TASK-QA-05).

.DESCRIPTION
Checks three invariants for a single-worker, single-tenant, non-production gray run:
  1. No duplicate submissions (unique task_no and operation idempotency key)
  2. No lease leaks (MySQL expired/missing leases + persistent Redis lease keys)
  3. No residual workspaces or worker processes

Run from the repository root. MySQL and Redis are accessed through docker compose,
so `docker compose up -d` must have been run first.

For a one-shot check:
    pwsh -NoProfile -File scripts/qa-gray-drill.ps1

For a 24-hour gray run (checks every minute, writes timestamped results to a log):
    pwsh -NoProfile -File scripts/qa-gray-drill.ps1 -SoakHours 24 -IntervalSeconds 60
#>
[CmdletBinding()]
param(
    [string]$DbName = 'taskforge',
    [string]$DbUser = 'root',
    [string]$DbPassword = 'taskforge_dev',
    [string]$RedisPassword = 'taskforge_dev',
    [string]$WorkspaceRoot = 'C:\data\agent-workspace',
    [string]$BareRepoRoot = 'C:\data\agent-bare-repos',
    [int]$SoakHours = 0,
    [int]$IntervalSeconds = 60,
    [int]$MaxIterations = 0,
    [string]$LogPath = '',
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Continue'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $RepoRoot

function Invoke-MySql([string]$Sql) {
    $output = & docker compose exec -T mysql mysql -N -B "-u$DbUser" "-p$DbPassword" $DbName -e $Sql 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL query failed: $Sql"
    }
    return @($output)
}

function Invoke-RedisCli([string[]]$RedisArgs) {
    $output = & docker compose exec -T redis redis-cli -a $RedisPassword @RedisArgs 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Redis command failed: $($RedisArgs -join ' ')"
    }
    return @($output)
}

function Get-RedisKeys([string]$Pattern) {
    return @(Invoke-RedisCli @('--scan', '--pattern', $Pattern))
}

function Test-RedisPersistentLeaks {
    $leaks = @()
    $keys = @()
    $keys += Get-RedisKeys 'agent:task:lease:*'
    $keys += Get-RedisKeys 'agent:task:heartbeat:*'
    $keys += Get-RedisKeys 'agent:task:cancel:*'
    $keys = $keys | Where-Object { $_ } | Sort-Object -Unique
    foreach ($key in $keys) {
        $ttlLine = @(Invoke-RedisCli @('TTL', $key))
        $ttl = if ($ttlLine.Count -gt 0) { [int]$ttlLine[0] } else { -2 }
        if ($ttl -eq -1) {
            $leaks += "$key (no TTL, persistent)"
        }
    }
    return @{ Total = $keys.Count; Leaks = $leaks }
}

function Get-ResidualWorkspaces {
    if (-not (Test-Path -LiteralPath $WorkspaceRoot -PathType Container)) {
        return @()
    }
    return @(Get-ChildItem -LiteralPath $WorkspaceRoot -Directory -Filter 'dirA-*' -ErrorAction SilentlyContinue |
        ForEach-Object { $_.FullName })
}

function Get-ResidualProcesses {
    $all = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue)
    $taskForgeBackendPids = @($all |
        Where-Object { $_.CommandLine -and $_.CommandLine.Contains('yudao-server.jar') } |
        ForEach-Object { [int]$_.ProcessId })
    $backendPidSet = [System.Collections.Generic.HashSet[int]]::new()
    foreach ($pidValue in $taskForgeBackendPids) {
        [void]$backendPidSet.Add($pidValue)
    }

    $candidates = @($all | Where-Object {
        $cmd = [string]$_.CommandLine
        $workspaceReferenced = $cmd -and ($cmd.Contains($WorkspaceRoot) -or $cmd.Contains($BareRepoRoot))
        $taskForgeWorkerChild = $_.Name -in @('codex.exe', 'claude.exe') -and
            $_.ParentProcessId -and $backendPidSet.Contains([int]$_.ParentProcessId)
        $workspaceReferenced -or $taskForgeWorkerChild
    })
    return @($candidates | ForEach-Object { "$($_.Name) pid=$($_.ProcessId) parent=$($_.ParentProcessId)" })
}

function Invoke-HealthCheck {
    $results = [System.Collections.Generic.List[object]]::new()

    function Add-Result([string]$Name, [int]$Expected, [int]$Actual, [string]$Detail) {
        $results.Add([pscustomobject]@{
            Name = $Name
            Expected = $Expected
            Actual = $Actual
            Pass = ($Actual -eq $Expected)
            Detail = $Detail
        })
    }

    # 1. Duplicate submissions: operation idempotency keys are unique.
    $dupOps = @(Invoke-MySql "SELECT request_idempotency_key, tenant_id, COUNT(*) FROM agent_task_operation_log GROUP BY request_idempotency_key, tenant_id HAVING COUNT(*) > 1;")
    Add-Result 'no duplicate operation idempotency keys' 0 $dupOps.Count (($dupOps | Out-String).Trim())

    # 2. Duplicate submissions: task numbers are unique (including soft-deleted).
    $dupTasks = @(Invoke-MySql "SELECT task_no, tenant_id, deleted, COUNT(*) FROM agent_task GROUP BY task_no, tenant_id, deleted HAVING COUNT(*) > 1;")
    Add-Result 'no duplicate task numbers' 0 $dupTasks.Count (($dupTasks | Out-String).Trim())

    # 3. Lease leaks: no RUNNING task with a missing or expired lease.
    $expiredLeases = @(Invoke-MySql "SELECT COUNT(*) FROM agent_task WHERE status = 'RUNNING' AND deleted = 0 AND (lease_until IS NULL OR lease_until < NOW());")
    $expiredCount = if ($expiredLeases.Count -gt 0) { [int]$expiredLeases[0] } else { 0 }
    Add-Result 'no expired/missing-lease RUNNING tasks' 0 $expiredCount (($expiredLeases | Out-String).Trim())

    # 4. Lease leaks: every RUNNING task holds a worker id.
    $missingWorkers = @(Invoke-MySql "SELECT COUNT(*) FROM agent_task WHERE status = 'RUNNING' AND deleted = 0 AND (worker_id IS NULL OR worker_id = '');")
    $missingWorkerCount = if ($missingWorkers.Count -gt 0) { [int]$missingWorkers[0] } else { 0 }
    Add-Result 'RUNNING tasks hold a worker id' 0 $missingWorkerCount (($missingWorkers | Out-String).Trim())

    # 5. Lease leaks: no persistent (TTL = -1) Redis lease keys.
    $redis = Test-RedisPersistentLeaks
    Add-Result 'no persistent Redis lease keys' 0 $redis.Leaks.Count (($redis.Leaks | Out-String).Trim())

    # 6. Residual workspaces.
    $residualDirs = @(Get-ResidualWorkspaces)
    Add-Result 'no residual dirA-* workspaces' 0 $residualDirs.Count (($residualDirs | Out-String).Trim())

    # 7. Residual worker processes.
    $residualProcs = @(Get-ResidualProcesses)
    Add-Result 'no residual Codex/Claude worker processes' 0 $residualProcs.Count (($residualProcs | Out-String).Trim())

    $failures = @($results | Where-Object { -not $_.Pass })

    $results | ForEach-Object {
        $tag = if ($_.Pass) { 'PASS' } else { 'FAIL' }
        Write-Host ("[{0}] {1} expected={2} actual={3}" -f $tag, $_.Name, $_.Expected, $_.Actual)
        if ($_.Detail) {
            Write-Host ("       detail: {0}" -f $_.Detail)
        }
    }

    Write-Host ''
    Write-Host ("Redis agent:task:* key count: {0}" -f $redis.Total)
    return ($failures.Count -eq 0)
}

if ($SoakHours -le 0) {
    $ok = Invoke-HealthCheck
    Pop-Location
    if ($ok) {
        Write-Host 'ALL CHECKS PASSED'
        exit 0
    }
    Write-Host 'FAILED: one or more checks did not pass'
    exit 1
}

$logPath = if ($LogPath) { $LogPath } else { Join-Path $RepoRoot 'gray-drill-soak.log' }
$iterations = [math]::Ceiling($SoakHours * 3600.0 / [math]::Max($IntervalSeconds, 1))
if ($MaxIterations -gt 0 -and $iterations -gt $MaxIterations) {
    $iterations = $MaxIterations
}

for ($i = 1; $i -le $iterations; $i++) {
    $stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    Write-Host ("=== iteration {0}/{1} at {2} ===" -f $i, $iterations, $stamp)
    $ok = Invoke-HealthCheck
    $line = "[{0}] iteration={1}/{2} passed={3}" -f $stamp, $i, $iterations, $ok
    Add-Content -LiteralPath $logPath -Value $line -Encoding utf8
    if (-not $ok) {
        Pop-Location
        Write-Host 'FAILED: soak run stopped on a failed health check'
        exit 1
    }
    if ($i -lt $iterations) {
        Start-Sleep -Seconds $IntervalSeconds
    }
}

Pop-Location
Write-Host 'SOAK RUN COMPLETED WITHOUT FAILURES'
exit 0
