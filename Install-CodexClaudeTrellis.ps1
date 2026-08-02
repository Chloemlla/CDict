#requires -Version 5.1
<#
.SYNOPSIS
Deploy the Janus Codex + Claude Code + Trellis workflow files into a project root.

.DESCRIPTION
Copies the reusable workflow layer for both Codex and Claude Code:

Shared Trellis core:
- .trellis/scripts/
- .trellis/spec/
- .trellis/workflow.md and .trellis/config.yaml
- optional .trellis/.version, .developer, .gitignore, .template-hashes.json
- empty .trellis/tasks and .trellis/workspace (never copies source task/runtime data)

Codex platform:
- .codex/  (agents, hooks, hooks.json, config.toml)
- .agents/skills/trellis-*

Claude Code platform (hook format):
- .claude/agents/
- .claude/commands/
- .claude/hooks/  (session-start / inject-workflow-state / inject-subagent-context)
- .claude/skills/trellis-*
- .claude/settings.json  (SessionStart / PreToolUse / UserPromptSubmit hook registration)

It deliberately does not copy:
- .trellis/tasks, .trellis/.runtime, .trellis/workspace contents from source
- .claude/settings.local.json (machine-local)

.EXAMPLE
.\Install-CodexClaudeTrellis.ps1

.EXAMPLE
.\Install-CodexClaudeTrellis.ps1 -TargetRoot F:\Repositories\GitHub\NewProject -Force

.EXAMPLE
.\Install-CodexClaudeTrellis.ps1 -ClaudeOnly -Force

.EXAMPLE
.\Install-CodexClaudeTrellis.ps1 -CodexOnly -ConfigureUserConfig

.EXAMPLE
.\Install-CodexClaudeTrellis.ps1 -Platforms Codex,Claude -Force
#>

[CmdletBinding(SupportsShouldProcess = $true, DefaultParameterSetName = 'Platforms')]
param(
    [string]$SourceRoot = 'F:\Repositories\GitHub\jans\Janus',
    [string]$TargetRoot = $PSScriptRoot,
    [switch]$Force,

    # Enable [features].hooks and mark TargetRoot as trusted in ~/.codex/config.toml
    [switch]$ConfigureUserConfig,

    # Deploy only Codex + shared Trellis (skip .claude/)
    [Parameter(ParameterSetName = 'CodexOnly')]
    [switch]$CodexOnly,

    # Deploy only Claude + shared Trellis (skip .codex/ and .agents/skills)
    [Parameter(ParameterSetName = 'ClaudeOnly')]
    [switch]$ClaudeOnly,

    # Explicit platform list; default is both when neither -CodexOnly nor -ClaudeOnly is set
    [Parameter(ParameterSetName = 'Platforms')]
    [ValidateSet('Codex', 'Claude')]
    [string[]]$Platforms = @('Codex', 'Claude')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function Resolve-ExistingDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        throw "$Name cannot be empty."
    }

    $resolved = Resolve-Path -LiteralPath $Path -ErrorAction Stop
    if (-not (Test-Path -LiteralPath $resolved.ProviderPath -PathType Container)) {
        throw "$Name is not a directory: $Path"
    }

    return [System.IO.Path]::GetFullPath($resolved.ProviderPath)
}

function Join-RootPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Root,
        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    return Join-Path -Path $Root -ChildPath $RelativePath
}

function Assert-SourcePath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing required source $Label`: $Path"
    }
}

function Ensure-Directory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (Test-Path -LiteralPath $Path -PathType Container) {
        return
    }

    if ($PSCmdlet.ShouldProcess($Path, 'Create directory')) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Copy-FileItem {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SourcePath,
        [Parameter(Mandatory = $true)]
        [string]$DestinationPath
    )

    Assert-SourcePath -Path $SourcePath -Label 'file'

    if ((Test-Path -LiteralPath $DestinationPath) -and -not $Force) {
        throw "Destination file already exists: $DestinationPath. Re-run with -Force to overwrite."
    }

    $parent = Split-Path -Parent $DestinationPath
    Ensure-Directory -Path $parent

    if ($PSCmdlet.ShouldProcess($DestinationPath, "Copy file from $SourcePath")) {
        Copy-Item -LiteralPath $SourcePath -Destination $DestinationPath -Force:$Force
    }
}

function Copy-DirectoryTree {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SourceDirectory,
        [Parameter(Mandatory = $true)]
        [string]$DestinationDirectory,
        [string[]]$ExcludeNames = @()
    )

    Assert-SourcePath -Path $SourceDirectory -Label 'directory'

    if ((Test-Path -LiteralPath $DestinationDirectory) -and -not $Force) {
        throw "Destination directory already exists: $DestinationDirectory. Re-run with -Force to merge and overwrite files."
    }

    Ensure-Directory -Path $DestinationDirectory

    $sourceRoot = [System.IO.Path]::GetFullPath($SourceDirectory).TrimEnd('\', '/')
    $items = Get-ChildItem -LiteralPath $SourceDirectory -Force -Recurse

    foreach ($item in $items) {
        $relative = $item.FullName.Substring($sourceRoot.Length).TrimStart('\', '/')

        $skip = $false
        foreach ($exclude in $ExcludeNames) {
            if ($relative -eq $exclude -or $relative.StartsWith("$exclude\") -or $relative.StartsWith("$exclude/")) {
                $skip = $true
                break
            }
            if ($item.Name -eq $exclude) {
                $skip = $true
                break
            }
        }
        if ($skip) {
            continue
        }

        $destination = Join-Path -Path $DestinationDirectory -ChildPath $relative

        if ($item.PSIsContainer) {
            Ensure-Directory -Path $destination
            continue
        }

        if ((Test-Path -LiteralPath $destination) -and -not $Force) {
            throw "Destination file already exists: $destination. Re-run with -Force to overwrite."
        }

        $parent = Split-Path -Parent $destination
        Ensure-Directory -Path $parent

        if ($PSCmdlet.ShouldProcess($destination, "Copy file from $($item.FullName)")) {
            Copy-Item -LiteralPath $item.FullName -Destination $destination -Force:$Force
        }
    }
}

function Copy-TrellisSkills {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResolvedSourceRoot,
        [Parameter(Mandatory = $true)]
        [string]$ResolvedTargetRoot,
        # Relative root under project, e.g. '.agents\skills' or '.claude\skills'
        [Parameter(Mandatory = $true)]
        [string]$SkillsRelativeRoot
    )

    $sourceSkills = Join-RootPath -Root $ResolvedSourceRoot -RelativePath $SkillsRelativeRoot
    Assert-SourcePath -Path $sourceSkills -Label 'skills directory'

    $skills = Get-ChildItem -LiteralPath $sourceSkills -Directory -Force |
        Where-Object { $_.Name -like 'trellis-*' }

    if (-not $skills) {
        throw "No trellis-* skills found under $sourceSkills"
    }

    foreach ($skill in $skills) {
        $destination = Join-RootPath -Root $ResolvedTargetRoot -RelativePath "$SkillsRelativeRoot\$($skill.Name)"
        Copy-DirectoryTree -SourceDirectory $skill.FullName -DestinationDirectory $destination
    }
}

function Copy-SharedTrellis {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResolvedSourceRoot,
        [Parameter(Mandatory = $true)]
        [string]$ResolvedTargetRoot
    )

    $requiredDirectories = @(
        '.trellis\scripts',
        '.trellis\spec'
    )

    $requiredFiles = @(
        '.trellis\workflow.md',
        '.trellis\config.yaml'
    )

    $optionalFiles = @(
        '.trellis\.version',
        '.trellis\.developer',
        '.trellis\.gitignore',
        '.trellis\.template-hashes.json'
    )

    foreach ($relativePath in $requiredDirectories) {
        Copy-DirectoryTree `
            -SourceDirectory (Join-RootPath -Root $ResolvedSourceRoot -RelativePath $relativePath) `
            -DestinationDirectory (Join-RootPath -Root $ResolvedTargetRoot -RelativePath $relativePath)
    }

    foreach ($relativePath in $requiredFiles) {
        Copy-FileItem `
            -SourcePath (Join-RootPath -Root $ResolvedSourceRoot -RelativePath $relativePath) `
            -DestinationPath (Join-RootPath -Root $ResolvedTargetRoot -RelativePath $relativePath)
    }

    foreach ($relativePath in $optionalFiles) {
        $sourcePath = Join-RootPath -Root $ResolvedSourceRoot -RelativePath $relativePath
        if (Test-Path -LiteralPath $sourcePath) {
            Copy-FileItem `
                -SourcePath $sourcePath `
                -DestinationPath (Join-RootPath -Root $ResolvedTargetRoot -RelativePath $relativePath)
        }
    }

    Ensure-Directory -Path (Join-RootPath -Root $ResolvedTargetRoot -RelativePath '.trellis\tasks')
    Ensure-Directory -Path (Join-RootPath -Root $ResolvedTargetRoot -RelativePath '.trellis\workspace')
}

function Copy-CodexPlatform {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResolvedSourceRoot,
        [Parameter(Mandatory = $true)]
        [string]$ResolvedTargetRoot
    )

    Copy-DirectoryTree `
        -SourceDirectory (Join-RootPath -Root $ResolvedSourceRoot -RelativePath '.codex') `
        -DestinationDirectory (Join-RootPath -Root $ResolvedTargetRoot -RelativePath '.codex')

    Copy-TrellisSkills `
        -ResolvedSourceRoot $ResolvedSourceRoot `
        -ResolvedTargetRoot $ResolvedTargetRoot `
        -SkillsRelativeRoot '.agents\skills'
}

function Copy-ClaudePlatform {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResolvedSourceRoot,
        [Parameter(Mandatory = $true)]
        [string]$ResolvedTargetRoot
    )

    $sourceClaude = Join-RootPath -Root $ResolvedSourceRoot -RelativePath '.claude'
    Assert-SourcePath -Path $sourceClaude -Label 'Claude directory'

    $requiredClaudePaths = @(
        '.claude\hooks\session-start.py',
        '.claude\hooks\inject-workflow-state.py',
        '.claude\hooks\inject-subagent-context.py',
        '.claude\settings.json',
        '.claude\agents',
        '.claude\skills'
    )

    foreach ($relativePath in $requiredClaudePaths) {
        Assert-SourcePath `
            -Path (Join-RootPath -Root $ResolvedSourceRoot -RelativePath $relativePath) `
            -Label $relativePath
    }

    # Full .claude tree except machine-local settings
    Copy-DirectoryTree `
        -SourceDirectory $sourceClaude `
        -DestinationDirectory (Join-RootPath -Root $ResolvedTargetRoot -RelativePath '.claude') `
        -ExcludeNames @('settings.local.json')

    # Ensure trellis-* skills landed (source may also use non-trellis skills we skip intentionally)
    $targetSkills = Join-RootPath -Root $ResolvedTargetRoot -RelativePath '.claude\skills'
    if (-not (Test-Path -LiteralPath $targetSkills)) {
        Copy-TrellisSkills `
            -ResolvedSourceRoot $ResolvedSourceRoot `
            -ResolvedTargetRoot $ResolvedTargetRoot `
            -SkillsRelativeRoot '.claude\skills'
    }

    Assert-ClaudeHookRegistration -SettingsPath (Join-RootPath -Root $ResolvedTargetRoot -RelativePath '.claude\settings.json')
}

function Assert-ClaudeHookRegistration {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SettingsPath
    )

    if (-not (Test-Path -LiteralPath $SettingsPath)) {
        throw "Claude settings missing after copy: $SettingsPath"
    }

    $raw = Get-Content -LiteralPath $SettingsPath -Encoding UTF8 -Raw
    $settings = $raw | ConvertFrom-Json

    if (-not $settings.hooks) {
        throw "Claude settings.json has no 'hooks' section: $SettingsPath"
    }

    $requiredEvents = @('SessionStart', 'UserPromptSubmit', 'PreToolUse')
    foreach ($eventName in $requiredEvents) {
        $prop = $settings.hooks.PSObject.Properties[$eventName]
        if (-not $prop -or -not $prop.Value) {
            throw "Claude settings.json missing hooks.$eventName registration: $SettingsPath"
        }
    }

    $commandBlob = ($raw)
    $requiredScripts = @(
        '.claude/hooks/session-start.py',
        '.claude/hooks/inject-workflow-state.py',
        '.claude/hooks/inject-subagent-context.py'
    )
    foreach ($script in $requiredScripts) {
        if ($commandBlob -notlike "*$script*") {
            throw "Claude settings.json does not reference required hook script: $script"
        }
    }
}

function Set-HooksFeature {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Content
    )

    $normalized = $Content -replace "`r`n", "`n"
    $lines = [System.Collections.Generic.List[string]]::new()
    foreach ($line in ($normalized -split "`n", -1)) {
        $lines.Add($line)
    }

    if ($lines.Count -eq 1 -and $lines[0] -eq '') {
        $lines.Clear()
    }

    $featuresStart = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s*\[features\]\s*$') {
            $featuresStart = $i
            break
        }
    }

    if ($featuresStart -lt 0) {
        if ($lines.Count -gt 0 -and $lines[$lines.Count - 1] -ne '') {
            $lines.Add('')
        }
        $lines.Add('[features]')
        $lines.Add('hooks = true')
        return ($lines -join [Environment]::NewLine).TrimEnd() + [Environment]::NewLine
    }

    $featuresEnd = $lines.Count
    for ($i = $featuresStart + 1; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^\s*\[.+\]\s*$') {
            $featuresEnd = $i
            break
        }
    }

    for ($i = $featuresStart + 1; $i -lt $featuresEnd; $i++) {
        if ($lines[$i] -match '^\s*hooks\s*=') {
            $lines[$i] = 'hooks = true'
            return ($lines -join [Environment]::NewLine).TrimEnd() + [Environment]::NewLine
        }
    }

    $lines.Insert($featuresStart + 1, 'hooks = true')
    return ($lines -join [Environment]::NewLine).TrimEnd() + [Environment]::NewLine
}

function Update-CodexUserConfig {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ResolvedTargetRoot
    )

    $codexHome = if ($env:CODEX_HOME) { $env:CODEX_HOME } else { Join-Path -Path $HOME -ChildPath '.codex' }
    $configPath = Join-Path -Path $codexHome -ChildPath 'config.toml'
    $targetForToml = $ResolvedTargetRoot.Replace('\', '/')
    $projectHeader = "[projects.`"$targetForToml`"]"

    Ensure-Directory -Path $codexHome

    $content = ''
    if (Test-Path -LiteralPath $configPath) {
        $content = Get-Content -LiteralPath $configPath -Encoding UTF8 -Raw
    }

    $updated = Set-HooksFeature -Content $content

    if (-not $updated.Contains($projectHeader)) {
        if ($updated.Trim().Length -gt 0) {
            $updated = $updated.TrimEnd() + [Environment]::NewLine + [Environment]::NewLine
        }
        $updated += "$projectHeader" + [Environment]::NewLine
        $updated += 'trust_level = "trusted"' + [Environment]::NewLine
    }

    if ($PSCmdlet.ShouldProcess($configPath, 'Update Codex user config')) {
        $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
        [System.IO.File]::WriteAllText($configPath, $updated, $utf8NoBom)
    }
}

# --- resolve platforms ---
$deployCodex = $false
$deployClaude = $false

if ($PSCmdlet.ParameterSetName -eq 'CodexOnly') {
    $deployCodex = $true
}
elseif ($PSCmdlet.ParameterSetName -eq 'ClaudeOnly') {
    $deployClaude = $true
}
else {
    foreach ($p in $Platforms) {
        if ($p -eq 'Codex') { $deployCodex = $true }
        if ($p -eq 'Claude') { $deployClaude = $true }
    }
}

if (-not $deployCodex -and -not $deployClaude) {
    throw 'No platform selected. Use default (both), -Platforms Codex,Claude, -CodexOnly, or -ClaudeOnly.'
}

if ($ConfigureUserConfig -and -not $deployCodex) {
    throw '-ConfigureUserConfig only applies when Codex is included in the deploy.'
}

$resolvedSourceRoot = Resolve-ExistingDirectory -Path $SourceRoot -Name 'SourceRoot'
$resolvedTargetRoot = Resolve-ExistingDirectory -Path $TargetRoot -Name 'TargetRoot'

if ($resolvedSourceRoot -eq $resolvedTargetRoot) {
    throw 'SourceRoot and TargetRoot must be different directories.'
}

$platformLabel = @()
if ($deployCodex) { $platformLabel += 'Codex' }
if ($deployClaude) { $platformLabel += 'Claude' }
$platformText = $platformLabel -join ' + '

Write-Host "Deploying Trellis workflow ($platformText)"
Write-Host "  Source: $resolvedSourceRoot"
Write-Host "  Target: $resolvedTargetRoot"

# Shared core always
Copy-SharedTrellis -ResolvedSourceRoot $resolvedSourceRoot -ResolvedTargetRoot $resolvedTargetRoot

if ($deployCodex) {
    Write-Host '  -> Codex platform (.codex/, .agents/skills/trellis-*)'
    Copy-CodexPlatform -ResolvedSourceRoot $resolvedSourceRoot -ResolvedTargetRoot $resolvedTargetRoot
}

if ($deployClaude) {
    Write-Host '  -> Claude Code platform (.claude/ agents, commands, hooks, skills, settings.json)'
    Copy-ClaudePlatform -ResolvedSourceRoot $resolvedSourceRoot -ResolvedTargetRoot $resolvedTargetRoot
}

if ($ConfigureUserConfig) {
    Update-CodexUserConfig -ResolvedTargetRoot $resolvedTargetRoot
}

Write-Host ""
Write-Host "Codex + Claude + Trellis workflow deployed to: $resolvedTargetRoot"
Write-Host "Platforms: $platformText"
Write-Host 'Skipped source task/runtime data: .trellis/tasks, .trellis/.runtime, .trellis/workspace'
Write-Host 'Skipped machine-local: .claude/settings.local.json'

if ($deployCodex) {
    if (-not $ConfigureUserConfig) {
        Write-Host 'Codex next: add this project to ~/.codex/config.toml as trusted, enable [features].hooks = true, then run /hooks in Codex.'
    }
    else {
        Write-Host 'Codex next: run /hooks in Codex to approve installed hooks (one-time).'
    }
}

if ($deployClaude) {
    Write-Host 'Claude next: reopen the project in Claude Code; SessionStart / UserPromptSubmit / PreToolUse hooks load from .claude/settings.json automatically.'
    Write-Host '  Verify: python .claude/hooks/session-start.py   (or start a new Claude session and confirm Trellis context injects)'
}
