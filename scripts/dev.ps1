<#
.SYNOPSIS
  Run SteveParty's dev server and client in the background: start, wait, stop gracefully, follow logs.

.DESCRIPTION
  All the server/client configuration lives in gradle/dev-server.gradle (runServer prepares run-server/,
  runClientJoin joins it as LordFinn). This script only adds what Gradle can't do well: running both in the
  background, starting the client once the server is actually ready, and stopping the server gracefully
  through RCON (save-all flush + stop) so no world data is lost.

  State (one log per process) lives in .dev-launch/.

.PARAMETER Command
  up      - start the server, wait until it's ready, then start a client that joins it (default).
  server  - start the dev server only.
  client  - start a client that joins the dev server.
  status  - show whether the server / client are running.
  stop    - stop the server (gracefully, via RCON), the client, or both (default: all).
  tail    - follow the server or client log (Ctrl+C stops following, not the process).
  cmd     - run a server command through RCON and print its output, e.g. .\scripts\dev.ps1 cmd "time set day".

.EXAMPLE
  .\scripts\dev.ps1 up
  .\scripts\dev.ps1 status
  .\scripts\dev.ps1 tail server
  .\scripts\dev.ps1 stop
  .\scripts\dev.ps1 client -Port 25581   # join another checkout's dev server
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('up', 'server', 'client', 'status', 'stop', 'tail', 'cmd')]
    [string]$Command = 'up',

    [Parameter(Position = 1)]
    [string]$Kind = 'all',

    # Dev server port (RCON = port + 10). Default: gradle/dev-server.gradle (25580).
    # Use it to join another checkout's server, e.g. -Port 25581 for a worktree's server.
    [int]$Port = 0
)

$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot
$Gradlew = Join-Path $RepoRoot 'gradlew.bat'
# @(...) keeps an array: a one-element if result would be unwrapped to a string and splatted char by char
$PortArgs = @(if ($Port -gt 0) { "-PdevServerPort=$Port" })
$StateDir = Join-Path $RepoRoot '.dev-launch'
New-Item -ItemType Directory -Force -Path $StateDir | Out-Null

# Single source of truth: gradle/dev-server.gradle
function Get-DevServerInfo {
    $info = @{}
    & $Gradlew -q devServerInfo --console=plain @PortArgs | ForEach-Object {
        if ($_ -match '^(\w+)=(.*)$') { $info[$Matches[1]] = $Matches[2].Trim() }
    }
    if (-not $info.port) { throw "Couldn't read the dev server settings (./gradlew devServerInfo)." }
    return $info
}

function Get-LogFile { param([string]$K) Join-Path $StateDir "$K.log" }

# The game JVM is started by the Gradle daemon, not by the wrapper we launch: find it by command line.
# Loom marks the environment (-Dfabric.dli.env=client|server) and the classpath contains this project's
# build outputs. GameTest servers (-Dfabric-api.gametest) are not the dev server.
function Find-GameJvm {
    param([string]$K)
    $project = [regex]::Escape((Join-Path $RepoRoot 'build'))
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object {
            $_.CommandLine -and $_.CommandLine -match $project -and
            $_.CommandLine -match "fabric\.dli\.env=$K" -and $_.CommandLine -notmatch 'fabric-api\.gametest'
        } |
        Select-Object -ExpandProperty ProcessId
}

function Start-Kind {
    param([string]$K)
    $running = @(Find-GameJvm $K)
    if ($running.Count -gt 0) {
        Write-Host "$K already running (PID $($running -join ', '))."
        return $false
    }
    $task = if ($K -eq 'server') { 'runServer' } else { 'runClientJoin' }
    $log = Get-LogFile $K
    Remove-Item $log, "$log.err" -Force -ErrorAction SilentlyContinue
    Start-Process -FilePath $Gradlew -ArgumentList (@($task, '--console=plain') + $PortArgs) -WorkingDirectory $RepoRoot `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err" -WindowStyle Hidden | Out-Null
    Write-Host "Started $K ($task) -> $log"
    return $true
}

function Wait-ServerReady {
    param([int]$TimeoutSeconds = 600)
    $log = Get-LogFile 'server'
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    Write-Host -NoNewline "Waiting for the server"
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $log) {
            $content = Get-Content $log -Raw -ErrorAction SilentlyContinue
            if ($content -match 'Done \([\d.,]+s\)! For help') { Write-Host " ready."; return $true }
            if ($content -match 'BUILD FAILED|Failed to start the minecraft server|Crash report') {
                Write-Host ""
                Write-Warning "The server failed to start, see $log"
                return $false
            }
        }
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 2
    }
    Write-Host ""
    Write-Warning "The server wasn't ready after $TimeoutSeconds s, see $log"
    return $false
}

# --- RCON: graceful server stop ---------------------------------------------------------------
function Send-RconPacket {
    param([System.Net.Sockets.NetworkStream]$Stream, [int]$RequestId, [int]$Type, [string]$Body)
    $bodyBytes = [System.Text.Encoding]::ASCII.GetBytes($Body)
    $length = 4 + 4 + $bodyBytes.Length + 2
    $buffer = New-Object byte[] (4 + $length)
    [Array]::Copy([BitConverter]::GetBytes($length), 0, $buffer, 0, 4)
    [Array]::Copy([BitConverter]::GetBytes($RequestId), 0, $buffer, 4, 4)
    [Array]::Copy([BitConverter]::GetBytes($Type), 0, $buffer, 8, 4)
    [Array]::Copy($bodyBytes, 0, $buffer, 12, $bodyBytes.Length)
    $Stream.Write($buffer, 0, $buffer.Length)
    $Stream.Flush()
}

function Read-RconRequestId {
    param([System.Net.Sockets.NetworkStream]$Stream)
    $read = {
        param([int]$Count)
        $bytes = New-Object byte[] $Count; $got = 0
        while ($got -lt $Count) {
            $n = $Stream.Read($bytes, $got, $Count - $got)
            if ($n -le 0) { throw 'RCON connection closed' }
            $got += $n
        }
        return , $bytes
    }
    $length = [BitConverter]::ToInt32((& $read 4), 0)
    $payload = & $read $length
    $body = if ($length -gt 10) { [System.Text.Encoding]::UTF8.GetString($payload, 8, $length - 10) } else { '' }
    return @{ RequestId = [BitConverter]::ToInt32($payload, 0); Body = $body }
}

function Send-RconCommands {
    param([int]$Port, [string]$Password, [string[]]$Commands, [switch]$PrintOutput)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        if (-not $client.ConnectAsync('127.0.0.1', $Port).Wait(3000)) { return $false }
        $stream = $client.GetStream()
        $stream.ReadTimeout = 5000
        Send-RconPacket $stream 1 3 $Password
        if ((Read-RconRequestId $stream).RequestId -eq -1) { return $false }
        foreach ($command in $Commands) {
            Send-RconPacket $stream 2 2 $command
            $response = Read-RconRequestId $stream
            if ($PrintOutput -and $response.Body) { Write-Host ($response.Body -replace '§.', '') }
        }
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Stop-Kind {
    param([string]$K)
    $jvms = @(Find-GameJvm $K)
    if ($jvms.Count -eq 0) { Write-Host "$K is not running."; return }
    if ($K -eq 'server') {
        $info = Get-DevServerInfo
        Write-Host "Stopping the server gracefully (save-all flush, stop)..."
        if (Send-RconCommands -Port ([int]$info.rconPort) -Password $info.rconPassword -Commands @('save-all flush', 'stop')) {
            $deadline = (Get-Date).AddSeconds(30)
            while ((Get-Date) -lt $deadline -and @(Find-GameJvm 'server').Count -gt 0) { Start-Sleep -Milliseconds 500 }
            if (@(Find-GameJvm 'server').Count -eq 0) { Write-Host "Server stopped."; return }
            Write-Warning "The server didn't stop within 30 s: killing it (changes since the last autosave may be lost)."
        } else {
            Write-Warning "RCON unreachable (server still starting?): killing it (changes since the last autosave may be lost)."
        }
    }
    # Kill only the game JVM: the Gradle daemon that launched it may run other games too
    $jvms | ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue }
    Write-Host "$K stopped."
}

function Show-Status {
    foreach ($k in 'server', 'client') {
        $jvms = @(Find-GameJvm $k)
        $state = if ($jvms.Count -gt 0) { "running (PID $($jvms -join ', '))" } else { 'stopped' }
        "{0,-7} {1,-28} {2}" -f $k, $state, (Get-LogFile $k) | Write-Host
    }
}

if ($Command -in 'stop', 'tail' -and $Kind -notin 'server', 'client', 'all') { throw "Kind must be server, client or all." }

switch ($Command) {
    'up' {
        $info = Get-DevServerInfo
        $alreadyUp = @(Find-GameJvm 'server').Count -gt 0
        if (-not $alreadyUp) { Start-Kind 'server' | Out-Null }
        if ($alreadyUp -or (Wait-ServerReady)) {
            Start-Kind 'client' | Out-Null
            Write-Host "The client joins localhost:$($info.port) as $($info.player) (op, creative)."
            Write-Host "Stop everything with: .\scripts\dev.ps1 stop"
        }
    }
    'server' { Start-Kind 'server' | Out-Null }
    'client' { Start-Kind 'client' | Out-Null }
    'status' { Show-Status }
    'stop' {
        # Client first, so it doesn't sit on a "connection lost" screen while the server saves
        if ($Kind -in 'client', 'all') { Stop-Kind 'client' }
        if ($Kind -in 'server', 'all') { Stop-Kind 'server' }
    }
    'cmd' {
        if ($Kind -eq 'all') { throw 'Usage: .\scripts\dev.ps1 cmd "<server command>"' }
        $info = Get-DevServerInfo
        if (-not (Send-RconCommands -Port ([int]$info.rconPort) -Password $info.rconPassword -Commands @($Kind) -PrintOutput)) {
            Write-Warning "RCON unreachable: is the server running (.\scripts\dev.ps1 status)?"
        }
    }
    'tail' {
        $k = if ($Kind -eq 'all') { 'server' } else { $Kind }
        $log = Get-LogFile $k
        if (-not (Test-Path $log)) { throw "No $k log yet: start it first (.\scripts\dev.ps1 $k)." }
        Get-Content -Path $log -Wait -Tail 40
    }
}
