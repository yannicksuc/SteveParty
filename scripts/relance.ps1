<#
.SYNOPSIS
  One command to (re)start the dev game on the latest code of this checkout: stops what runs, restarts the dev server,
  the client (LordFinn) and the second client (Steve2), waits until they joined, and brings the windows to the front.

.DESCRIPTION
  Built on scripts/dev.ps1 (graceful server stop through RCON) and gradle/dev-server.gradle (runClientJoin2 = Steve2).
  Gradle rebuilds what changed, so the game always runs the current sources.

.PARAMETER Seul
  Only the main client (LordFinn), no second client.

.PARAMETER Monde
  Also rebuild the test world (scripts/test-world/build_test_world.py + its build functions) before the players join.

.PARAMETER ArrierePlan
  Leave the windows in the background (default: brought to the front once the players joined).

.EXAMPLE
  .\scripts\relance.ps1
  .\scripts\relance.ps1 -Seul
  .\scripts\relance.ps1 -Monde
#>
[CmdletBinding()]
param(
    [switch]$Seul,
    [switch]$Monde,
    [switch]$ArrierePlan
)

$ErrorActionPreference = 'Continue'
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot
$dev = Join-Path $PSScriptRoot 'dev.ps1'

function Get-Clients {
    Get-CimInstance Win32_Process -Filter "Name='java.exe' or Name='javaw.exe'" |
        Where-Object {
            $_.CommandLine -match 'fabric.dli.env=client' -and $_.CommandLine -match [regex]::Escape($RepoRoot) `
                -and $_.CommandLine -notlike '*\worktrees\*' # never the worktrees' test clients
        }
}

Write-Host "== Arret du jeu en cours..."
& $dev stop 2>&1 | Out-Null
Get-Clients | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

if ($Monde) {
    Write-Host "== Generation du monde de test..."
    python (Join-Path $PSScriptRoot 'test-world\build_test_world.py') | Out-Null
}

Write-Host "== Demarrage du serveur et du client (version : $(git log --oneline -1))..."
& $dev up 2>&1 | Select-Object -Last 1

if ($Monde) {
    & $dev cmd "forceload add 1936 1952 2095 2175" | Out-Null
    Start-Sleep 8
    & $dev cmd "reload" | Out-Null
    Start-Sleep 3
    & $dev cmd "function steveparty_test:build" | Out-Null
}

$players = @('LordFinn')
if (-not $Seul) {
    Write-Host "== Demarrage du second client (Steve2)..."
    Start-Process -FilePath (Join-Path $RepoRoot 'gradlew.bat') -ArgumentList 'runClientJoin2' -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $RepoRoot '.dev-launch\client2.log') `
        -RedirectStandardError (Join-Path $RepoRoot '.dev-launch\client2.err.log')
    $players += 'Steve2'
}

Write-Host "== Attente de la connexion de : $($players -join ', ')..."
$joined = $false
for ($i = 0; $i -lt 100; $i++) {
    $list = (& $dev cmd "list" 2>&1 | Out-String)
    if (-not ($players | Where-Object { $list -notmatch $_ })) { $joined = $true; break }
    Start-Sleep 3
}
if (-not $joined) { Write-Warning "Tous les joueurs ne sont pas encore connectes (voir .dev-launch\*.log)." }

if ($joined -and -not $Seul) { & $dev cmd "tp Steve2 LordFinn" | Out-Null }
if ($Monde -and $joined) {
    & $dev cmd "function steveparty_test:welcome" | Out-Null
    & $dev cmd "function steveparty_test:nouveautes" | Out-Null
    Start-Sleep 4
    & $dev cmd "forceload remove all" | Out-Null
}

if (-not $ArrierePlan) {
    Add-Type -Namespace SteveParty -Name Win -MemberDefinition @'
[DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
[DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
'@ -ErrorAction SilentlyContinue
    # Steve2 first, LordFinn last: the main client ends up on top
    Get-Clients | Sort-Object { $_.CommandLine -notmatch 'Steve2' } | ForEach-Object {
        $h = (Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue).MainWindowHandle
        if ($h) {
            [SteveParty.Win]::ShowWindow($h, 6) | Out-Null
            [SteveParty.Win]::ShowWindow($h, 9) | Out-Null
            [SteveParty.Win]::SetForegroundWindow($h) | Out-Null
        }
    }
}
Write-Host "== Pret."
