<#
.SYNOPSIS
  A small always-available window to restart SteveParty's dev game (server + clients) on the latest code.
  Buttons run scripts/relance.ps1 (or dev.ps1 stop) in the background and show their output.
#>
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()

$RepoRoot = Split-Path -Parent $PSScriptRoot
$relance = Join-Path $PSScriptRoot 'relance.ps1'
$dev = Join-Path $PSScriptRoot 'dev.ps1'

$form = New-Object System.Windows.Forms.Form
$form.Text = 'SteveParty - Relancer le jeu'
$form.Size = New-Object System.Drawing.Size(460, 520)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedSingle'
$form.MaximizeBox = $false
$form.TopMost = $true

$log = New-Object System.Windows.Forms.TextBox
$log.Multiline = $true
$log.ReadOnly = $true
$log.ScrollBars = 'Vertical'
$log.Location = New-Object System.Drawing.Point(12, 262)
$log.Size = New-Object System.Drawing.Size(420, 208)
$log.Font = New-Object System.Drawing.Font('Consolas', 9)
$form.Controls.Add($log)

$script:proc = $null
$buttons = @()

function Add-Line([string]$text) {
    if ([string]::IsNullOrWhiteSpace($text)) { return }
    $log.AppendText($text.TrimEnd() + [Environment]::NewLine)
}

function Start-Action([string]$title, [string]$arguments) {
    if ($script:proc -and -not $script:proc.HasExited) { return }
    $log.Clear()
    Add-Line "> $title"
    foreach ($b in $buttons) { $b.Enabled = $false }
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = 'pwsh'
    $info.Arguments = "-NoProfile -ExecutionPolicy Bypass $arguments"
    $info.WorkingDirectory = $RepoRoot
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    # stderr is not read: redirected, a full buffer would block the script
    $info.RedirectStandardError = $false
    $script:outTask = $null
    $script:exitedAt = $null
    $script:proc = [System.Diagnostics.Process]::Start($info)
    $timer.Start()
}

function New-Button([string]$text, [int]$y, [scriptblock]$onClick) {
    $b = New-Object System.Windows.Forms.Button
    $b.Text = $text
    $b.Location = New-Object System.Drawing.Point(12, $y)
    $b.Size = New-Object System.Drawing.Size(420, 38)
    $b.Font = New-Object System.Drawing.Font('Segoe UI', 10)
    $b.Add_Click($onClick)
    $form.Controls.Add($b)
    $script:buttons += $b
}

New-Button 'Relancer : serveur + 2 clients (LordFinn et Steve2)' 12 { Start-Action 'Relance (2 clients)' "-File `"$relance`"" }
New-Button 'Relancer : serveur + 1 client (LordFinn)' 56 { Start-Action 'Relance (1 client)' "-File `"$relance`" -Seul" }
New-Button 'Relancer + reconstruire le monde de test' 100 { Start-Action 'Relance + monde de test' "-File `"$relance`" -Monde" }
New-Button 'Tout arreter' 144 { Start-Action 'Arret' "-File `"$dev`" stop" }

# The server console: a command sent through RCON (its answer shows below), and the live logs in their own window
$cmdBox = New-Object System.Windows.Forms.TextBox
$cmdBox.Location = New-Object System.Drawing.Point(12, 190)
$cmdBox.Size = New-Object System.Drawing.Size(330, 26)
$cmdBox.Font = New-Object System.Drawing.Font('Consolas', 10)
$form.Controls.Add($cmdBox)

function Send-ServerCommand {
    $text = $cmdBox.Text.Trim().TrimStart('/')
    if (-not $text) { return }
    # Quotes of the command (JSON text of /tellraw…) survive both the PowerShell string and the process arguments
    $quoted = $text.Replace("'", "''").Replace('"', '\"')
    # dev.ps1 prints with Write-Host: all streams are merged so the answer reaches the window
    Start-Action "/$text" "-Command `"& '$dev' cmd '$quoted' *>&1`""
    $cmdBox.Clear()
}
$send = New-Object System.Windows.Forms.Button
$send.Text = 'Envoyer'
$send.Location = New-Object System.Drawing.Point(346, 189)
$send.Size = New-Object System.Drawing.Size(86, 28)
$send.Add_Click({ Send-ServerCommand })
$form.Controls.Add($send)
$script:buttons += $send
$form.AcceptButton = $send

function Open-Log([string]$kind) {
    Start-Process pwsh -WorkingDirectory $RepoRoot -ArgumentList '-NoProfile', '-NoExit', '-ExecutionPolicy', 'Bypass', '-File', $dev, 'tail', $kind
}
$serverLog = New-Object System.Windows.Forms.Button
$serverLog.Text = 'Console du serveur (journal)'
$serverLog.Location = New-Object System.Drawing.Point(12, 224)
$serverLog.Size = New-Object System.Drawing.Size(208, 30)
$serverLog.Add_Click({ Open-Log 'server' })
$form.Controls.Add($serverLog)
$clientLog = New-Object System.Windows.Forms.Button
$clientLog.Text = 'Journal du client'
$clientLog.Location = New-Object System.Drawing.Point(224, 224)
$clientLog.Size = New-Object System.Drawing.Size(208, 30)
$clientLog.Add_Click({ Open-Log 'client' })
$form.Controls.Add($clientLog)

# Poll the running action's output without blocking the window
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 300
$outTask = $null
$exitedAt = $null
$timer.Add_Tick({
    if (-not $script:proc) { return }
    if (-not $script:outTask) { $script:outTask = $script:proc.StandardOutput.ReadLineAsync() }
    while ($script:outTask -and $script:outTask.IsCompleted) {
        $line = $script:outTask.Result
        if ($null -eq $line) { $script:outTask = $null; break }
        Add-Line $line
        $script:outTask = $script:proc.StandardOutput.ReadLineAsync()
    }
    # The game started by the script keeps its output pipe open after the script ends: once the script has exited,
    # stop waiting for the end of the output after a short while, or the buttons would never come back
    if ($script:proc.HasExited -and -not $script:exitedAt) { $script:exitedAt = Get-Date }
    if ($script:proc.HasExited -and ((-not $script:outTask) -or ((Get-Date) - $script:exitedAt).TotalSeconds -gt 2)) {
        $script:outTask = $null
        $timer.Stop()
        Add-Line '> Termine.'
        $script:proc = $null
        foreach ($b in $buttons) { $b.Enabled = $true }
    }
})

[void]$form.ShowDialog()
