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
$form.Size = New-Object System.Drawing.Size(460, 400)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedSingle'
$form.MaximizeBox = $false
$form.TopMost = $true

$log = New-Object System.Windows.Forms.TextBox
$log.Multiline = $true
$log.ReadOnly = $true
$log.ScrollBars = 'Vertical'
$log.Location = New-Object System.Drawing.Point(12, 196)
$log.Size = New-Object System.Drawing.Size(420, 154)
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
    $info.RedirectStandardError = $true
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

# Poll the running action's output without blocking the window
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 300
$outTask = $null
$timer.Add_Tick({
    if (-not $script:proc) { return }
    if (-not $script:outTask) { $script:outTask = $script:proc.StandardOutput.ReadLineAsync() }
    while ($script:outTask -and $script:outTask.IsCompleted) {
        $line = $script:outTask.Result
        if ($null -eq $line) { $script:outTask = $null; break }
        Add-Line $line
        $script:outTask = $script:proc.StandardOutput.ReadLineAsync()
    }
    if ($script:proc.HasExited -and -not $script:outTask) {
        $timer.Stop()
        Add-Line '> Termine.'
        $script:proc = $null
        foreach ($b in $buttons) { $b.Enabled = $true }
    }
})

[void]$form.ShowDialog()
