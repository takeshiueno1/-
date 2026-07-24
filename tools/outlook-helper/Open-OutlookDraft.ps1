[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$LaunchUri
)

$ErrorActionPreference = 'Stop'
$scheme = 'query-attendance-outlook:'
if (-not $LaunchUri.StartsWith($scheme, [StringComparison]::OrdinalIgnoreCase)) {
    exit 2
}

$downloadUrl = [Uri]::UnescapeDataString($LaunchUri.Substring($scheme.Length))
$uri = [Uri]$downloadUrl
if ($uri.Scheme -ne 'http' -or
    -not $uri.IsLoopback -or
    $uri.Port -ne 4173 -or
    -not $uri.AbsolutePath.StartsWith('/api/outlook-drafts/', [StringComparison]::Ordinal) -or
    -not $uri.AbsolutePath.EndsWith('.eml', [StringComparison]::OrdinalIgnoreCase)) {
    exit 3
}

$draftDirectory = Join-Path ([System.IO.Path]::GetTempPath()) 'QueryAttendance\OutlookDrafts'
[System.IO.Directory]::CreateDirectory($draftDirectory) | Out-Null
Get-ChildItem -LiteralPath $draftDirectory -Filter '*.eml' -File -ErrorAction SilentlyContinue |
    Where-Object LastWriteTime -lt (Get-Date).AddDays(-1) |
    Remove-Item -Force -ErrorAction SilentlyContinue

$draftPath = Join-Path $draftDirectory (
    'Attendance_Outlook_' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' +
        [Guid]::NewGuid().ToString('N') + '.eml'
)
Invoke-WebRequest -UseBasicParsing -Uri $uri.AbsoluteUri -OutFile $draftPath -TimeoutSec 15
$draft = Get-Item -LiteralPath $draftPath
if ($draft.Length -lt 1 -or $draft.Length -gt 15MB) {
    Remove-Item -LiteralPath $draftPath -Force -ErrorAction SilentlyContinue
    exit 4
}

Start-Process -FilePath $draftPath
