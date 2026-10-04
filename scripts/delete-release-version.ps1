<#
.SYNOPSIS
  Delete a published version from Modrinth and/or point you at CurseForge's deletion page.

.DESCRIPTION
  Modrinth: calls DELETE /v2/version/{id} with $env:MODRINTH_TOKEN (needs the "Delete versions" scope).
  Set it yourself in your own shell first: $env:MODRINTH_TOKEN = "mrp_..."
  CurseForge has no delete API: for -Platform curseforge or both, this only prints the page to do it by hand.

.PARAMETER VersionNumber
  The Modrinth version number (e.g. "0.2.0", the tag without its "v") or a raw 8-character version id.

.PARAMETER Platform
  modrinth (default), curseforge, or both.

.PARAMETER ModrinthProjectId
  Modrinth project slug or id, used to look the version up by number. Defaults to "steveparty".

.EXAMPLE
  .\scripts\delete-release-version.ps1 -VersionNumber "0.2.0-beta.1" -Platform both
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$VersionNumber,

    [Parameter(Position = 1)]
    [ValidateSet('modrinth', 'curseforge', 'both')]
    [string]$Platform = 'modrinth',

    [string]$ModrinthProjectId = 'steveparty'
)

$ErrorActionPreference = 'Stop'

function Resolve-ModrinthVersionId {
    param([string]$VersionOrId, [string]$ProjectId)
    # Modrinth ids are 8 alphanumeric characters; anything else is looked up by version number
    if ($VersionOrId -match '^[A-Za-z0-9]{8}$') {
        return $VersionOrId
    }
    Write-Host "Looking up Modrinth version id for '$VersionOrId' on project '$ProjectId'..."
    # Authenticated: a project still under review 404s on anonymous requests
    $headers = if ($env:MODRINTH_TOKEN) { @{ Authorization = $env:MODRINTH_TOKEN } } else { @{} }
    $versions = Invoke-RestMethod -Uri "https://api.modrinth.com/v2/project/$ProjectId/version" -Method Get -Headers $headers
    $match = $versions | Where-Object { $_.version_number -eq $VersionOrId -or $_.name -eq $VersionOrId }
    if (-not $match) {
        Write-Error "No Modrinth version on project '$ProjectId' matches '$VersionOrId'."
    }
    if (@($match).Count -gt 1) {
        Write-Error "Several Modrinth versions match '$VersionOrId': pass the 8-character version id instead."
    }
    return $match.id
}

function Remove-ModrinthVersion {
    param([string]$VersionOrId, [string]$ProjectId)
    if (-not $env:MODRINTH_TOKEN) {
        Write-Error "`$env:MODRINTH_TOKEN is not set. Set it first: `$env:MODRINTH_TOKEN = 'mrp_...' (a token with the 'Delete versions' scope)."
    }
    $id = Resolve-ModrinthVersionId -VersionOrId $VersionOrId -ProjectId $ProjectId
    Write-Host "Deleting Modrinth version $id ($VersionOrId)..."
    Invoke-RestMethod -Uri "https://api.modrinth.com/v2/version/$id" -Method Delete -Headers @{ Authorization = $env:MODRINTH_TOKEN }
    Write-Host "Deleted."
}

function Show-CurseForgeManualStep {
    param([string]$VersionOrId)
    Write-Host ""
    Write-Host "CurseForge has no delete API." -ForegroundColor Yellow
    Write-Host "Delete '$VersionOrId' yourself: https://authors.curseforge.com/ -> the project -> Files -> the file's page -> delete."
    Write-Host ""
}

switch ($Platform) {
    'modrinth' { Remove-ModrinthVersion -VersionOrId $VersionNumber -ProjectId $ModrinthProjectId }
    'curseforge' { Show-CurseForgeManualStep -VersionOrId $VersionNumber }
    'both' {
        Remove-ModrinthVersion -VersionOrId $VersionNumber -ProjectId $ModrinthProjectId
        Show-CurseForgeManualStep -VersionOrId $VersionNumber
    }
}
