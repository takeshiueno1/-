# Codex Pet Completion Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Codexのターン終了時に、ペット「ブー」の近くへ20秒間の目立つ吹き出しを通知音付きで表示する。

**Architecture:** リポジトリには配布元となるPowerShellモジュール、表示スクリプト、フック、導入スクリプトを置く。導入時にそれらを `C:\Users\user\.codex\boo-notify` へコピーし、既存の `notify` コマンドを保持したラッパーをCodex設定へ登録する。位置計算と通知内容判定は純粋関数に分離し、Pester 3.4.0でテストする。

**Tech Stack:** Windows PowerShell 5.1、WPF、Pester 3.4.0、Codex `notify` フック、TOML設定

## Global Constraints

- 正常終了の文言は「作業完了！」、緑色のチェックマーク付きとする。
- 黄色系の背景、太い縁取り、大きな文字で表示する。
- 表示時間は20秒とし、クリックでも閉じられるようにする。
- Windows標準通知音を1回だけ鳴らし、音の失敗は表示へ影響させない。
- 最前面へ表示するが、利用中アプリケーションのキーボードフォーカスを奪わない。
- 既存のCodex終了通知コマンドを維持する。
- 会話本文、認証情報、通知ペイロードをログへ保存しない。
- 通知処理の失敗をCodex本体の失敗として返さない。
- 外部ライブラリや外部音声ファイルを追加しない。
- ペット画像、スプライトシート、勤怠管理システム本体は変更しない。

---

## File Structure

- Create: `tools/codex-notify/BooCompletionNotification.psm1` — メッセージ判定、ペット位置読取り、画面内配置計算を担当する。
- Create: `tools/codex-notify/Show-BooCompletion.ps1` — WPF吹き出し、20秒タイマー、クリック終了、通知音、単一起動を担当する。
- Create: `tools/codex-notify/Invoke-BooCompletionHook.ps1` — 既存通知の呼出しと吹き出しプロセスの非同期起動を担当する。
- Create: `tools/codex-notify/Install-BooCompletionNotification.ps1` — バックアップ、配布、`config.toml` 更新、復元を担当する。
- Create: `tools/codex-notify/tests/BooCompletionNotification.Tests.ps1` — 純粋関数、フック引数、設定更新、復元を検証する。
- Create: `tools/codex-notify/README.md` — 導入、プレビュー、復元、制約を記録する。
- Modify at install time: `C:\Users\user\.codex\config.toml` — `notify` を安全なラッパー呼出しへ変更する。
- Create at install time: `C:\Users\user\.codex\boo-notify\runtime.json` — 元の通知コマンドだけをJSON配列で保持する。
- Create at install time: `C:\Users\user\.codex\config.toml.boo-notify.bak` — 導入前設定の復元用バックアップ。

### Task 1: 通知判定と配置計算

**Files:**
- Create: `tools/codex-notify/BooCompletionNotification.psm1`
- Create: `tools/codex-notify/tests/BooCompletionNotification.Tests.ps1`

**Interfaces:**
- Produces: `Get-BooNotificationContent -Payload <string[]> -> PSCustomObject { Message, Kind }`
- Produces: `Get-BooPetAnchor -StatePath <string> -> PSCustomObject { X, Y } | $null`
- Produces: `Get-BooBubblePosition -Anchor <object> -WorkArea <object> -Width <double> -Height <double> -> PSCustomObject { Left, Top }`

- [ ] **Step 1: Write failing pure-function tests**

```powershell
Describe 'Get-BooNotificationContent' {
    It 'uses the completion message by default' {
        $result = Get-BooNotificationContent -Payload @('{"type":"agent-turn-complete"}')
        $result.Message | Should Be '作業完了！'
        $result.Kind | Should Be 'success'
    }

    It 'uses the review message only for explicit failure data' {
        $result = Get-BooNotificationContent -Payload @('{"type":"agent-turn-complete","status":"failed"}')
        $result.Message | Should Be '作業を確認してください'
        $result.Kind | Should Be 'warning'
    }
}

Describe 'Get-BooBubblePosition' {
    It 'keeps the bubble inside a negative-coordinate monitor' {
        $anchor = [pscustomobject]@{ X = -123; Y = -438 }
        $area = [pscustomobject]@{ Left = -1184; Top = -1080; Right = 736; Bottom = 0 }
        $result = Get-BooBubblePosition -Anchor $anchor -WorkArea $area -Width 360 -Height 150
        $result.Left | Should BeGreaterThan -1185
        $result.Top | Should BeGreaterThan -1081
        ($result.Left + 360) | Should BeLessThan 737
        ($result.Top + 150) | Should BeLessThan 1
    }
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```powershell
powershell.exe -NoProfile -Command "Invoke-Pester '.\tools\codex-notify\tests\BooCompletionNotification.Tests.ps1'"
```

Expected: FAIL because the module and functions do not exist.

- [ ] **Step 3: Implement minimal pure functions**

Implement these exact rules:

```powershell
function Get-BooNotificationContent {
    [CmdletBinding()]
    param([string[]]$Payload)
    $text = $Payload -join ' '
    $isFailure = $text -match '"status"\s*:\s*"(failed|error|blocked)"'
    if ($isFailure) {
        return [pscustomobject]@{ Message = '作業を確認してください'; Kind = 'warning' }
    }
    [pscustomobject]@{ Message = '作業完了！'; Kind = 'success' }
}
```

`Get-BooPetAnchor` must parse only `electron-persisted-atom-state.electron-avatar-overlay-bounds.x/y`; missing or invalid JSON returns `$null`. `Get-BooBubblePosition` must place the 360x150 bubble above-left of the anchor, clamp all edges to the supplied work area with a 12px margin, and use the work-area right-bottom corner when the anchor is `$null`:

```powershell
function Get-BooPetAnchor {
    param([Parameter(Mandatory)][string]$StatePath)
    try {
        $state = Get-Content -LiteralPath $StatePath -Raw -Encoding UTF8 | ConvertFrom-Json
        $bounds = $state.'electron-persisted-atom-state'.'electron-avatar-overlay-bounds'
        if ($null -eq $bounds.x -or $null -eq $bounds.y) { return $null }
        [pscustomobject]@{ X = [double]$bounds.x; Y = [double]$bounds.y }
    } catch { $null }
}

function Get-BooBubblePosition {
    param($Anchor, $WorkArea, [double]$Width, [double]$Height)
    $margin = 12
    $left = if ($null -eq $Anchor) { $WorkArea.Right - $Width - $margin } else { $Anchor.X - $Width - 20 }
    $top = if ($null -eq $Anchor) { $WorkArea.Bottom - $Height - $margin } else { $Anchor.Y - $Height - 20 }
    [pscustomobject]@{
        Left = [Math]::Min([Math]::Max($left, $WorkArea.Left + $margin), $WorkArea.Right - $Width - $margin)
        Top = [Math]::Min([Math]::Max($top, $WorkArea.Top + $margin), $WorkArea.Bottom - $Height - $margin)
    }
}
```

- [ ] **Step 4: Run the tests and verify GREEN**

Run the Pester command from Step 2.

Expected: all Task 1 tests PASS.

- [ ] **Step 5: Commit the pure logic**

```powershell
git add -- tools/codex-notify/BooCompletionNotification.psm1 tools/codex-notify/tests/BooCompletionNotification.Tests.ps1
git commit -m "feat: ブー通知の判定と配置計算を追加"
```

### Task 2: 20秒の吹き出しと通知音

**Files:**
- Create: `tools/codex-notify/Show-BooCompletion.ps1`
- Modify: `tools/codex-notify/tests/BooCompletionNotification.Tests.ps1`

**Interfaces:**
- Consumes: `Get-BooNotificationContent`, `Get-BooPetAnchor`, `Get-BooBubblePosition`
- Produces: CLI `Show-BooCompletion.ps1 [-Payload <string[]>] [-DurationSeconds <int>] [-NoSound] [-Preview]`

- [ ] **Step 1: Add failing script-contract tests**

Add this contract test:

```powershell
Describe 'Show-BooCompletion script contract' {
    $scriptPath = Join-Path $PSScriptRoot '..\Show-BooCompletion.ps1'
    It 'defines the required visible-notification behavior' {
        $source = Get-Content -LiteralPath $scriptPath -Raw
        $source | Should Match '\[string\[\]\]\s*\$Payload'
        $source | Should Match '\[int\]\s*\$DurationSeconds\s*=\s*20'
        $source | Should Match '\[switch\]\s*\$NoSound'
        $source | Should Match '\[switch\]\s*\$Preview'
        $source | Should Match 'System\.Media\.SystemSounds'
        $source | Should Match 'DispatcherTimer'
        $source | Should Match 'MouseLeftButtonUp'
        $source | Should Match 'ShowActivated\s*=\s*\$false'
        $source | Should Match 'Global\\CodexBooCompletionNotification'
    }
}
```

- [ ] **Step 2: Run Pester and verify RED**

Expected: FAIL because `Show-BooCompletion.ps1` does not exist.

- [ ] **Step 3: Implement the WPF notification**

Use `Add-Type -AssemblyName PresentationFramework,PresentationCore,WindowsBase,System.Windows.Forms`. Build a borderless transparent `System.Windows.Window` with these fixed values:

```powershell
$window.Width = 360
$window.Height = 150
$window.Topmost = $true
$window.ShowActivated = $false
$window.WindowStyle = 'None'
$window.AllowsTransparency = $true
$window.Background = [Windows.Media.Brushes]::Transparent
```

The inner border must use `#FFF4A3` background, `#D89B00` 5px border, 18px corner radius, and drop shadow. Render `✓` in green and the message in bold 34px Japanese-capable system font. A dispatcher timer closes the window after `DurationSeconds`, default 20. A left-click handler stops the timer and closes the window. The named mutex prevents overlapping windows; if it is already owned, exit zero. Play `[System.Media.SystemSounds]::Asterisk.Play()` inside its own `try/catch` unless `-NoSound` is present. `-Preview` uses the same window path but does not require hook payload.

- [ ] **Step 4: Run automated tests and a two-second smoke preview**

```powershell
powershell.exe -NoProfile -Command "Invoke-Pester '.\tools\codex-notify\tests\BooCompletionNotification.Tests.ps1'"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\codex-notify\Show-BooCompletion.ps1 -Preview -DurationSeconds 2 -NoSound
```

Expected: Pester PASS; preview exits successfully after about two seconds without stealing focus.

- [ ] **Step 5: Commit the presentation layer**

```powershell
git add -- tools/codex-notify/Show-BooCompletion.ps1 tools/codex-notify/tests/BooCompletionNotification.Tests.ps1
git commit -m "feat: ブーの作業完了吹き出しを追加"
```

### Task 3: 既存通知を維持するフックと安全な導入

**Files:**
- Create: `tools/codex-notify/Invoke-BooCompletionHook.ps1`
- Create: `tools/codex-notify/Install-BooCompletionNotification.ps1`
- Modify: `tools/codex-notify/tests/BooCompletionNotification.Tests.ps1`
- Create: `tools/codex-notify/README.md`

**Interfaces:**
- Produces: CLI `Invoke-BooCompletionHook.ps1 -RuntimeConfigPath <string> [remaining payload]`
- Produces: CLI `Install-BooCompletionNotification.ps1 [-CodexHome <string>] [-SourceDirectory <string>] [-Uninstall]`

- [ ] **Step 1: Add failing installation and restoration tests**

Create a Pester `$TestDrive` config containing the current shape:

```toml
model = "gpt-5.6-sol"
notify = [ "C:\\original\\notify.exe", "turn-ended" ]
```

Use this concrete fixture and assertions:

```powershell
Describe 'Install-BooCompletionNotification' {
    BeforeEach {
        $sourceDirectory = Split-Path $PSScriptRoot -Parent
        $installer = Join-Path $sourceDirectory 'Install-BooCompletionNotification.ps1'
        $fakeHome = Join-Path $TestDrive '.codex'
        New-Item -ItemType Directory -Path $fakeHome | Out-Null
        $original = "model = `"gpt-5.6-sol`"`nnotify = [ `"C:\\original\\notify.exe`", `"turn-ended`" ]`n"
        Set-Content -LiteralPath (Join-Path $fakeHome 'config.toml') -Value $original -NoNewline -Encoding UTF8
    }

    It 'backs up once, preserves the original command, and is idempotent' {
        & $installer -CodexHome $fakeHome -SourceDirectory $sourceDirectory
        $firstBackupHash = (Get-FileHash (Join-Path $fakeHome 'config.toml.boo-notify.bak')).Hash
        & $installer -CodexHome $fakeHome -SourceDirectory $sourceDirectory
        (Get-FileHash (Join-Path $fakeHome 'config.toml.boo-notify.bak')).Hash | Should Be $firstBackupHash
        $runtime = Get-Content (Join-Path $fakeHome 'boo-notify\runtime.json') -Raw | ConvertFrom-Json
        $runtime.originalCommand[0] | Should Be 'C:\original\notify.exe'
        $runtime.originalCommand[1] | Should Be 'turn-ended'
        ((Get-Content (Join-Path $fakeHome 'config.toml') | Where-Object { $_ -match '^notify\s*=' }).Count) | Should Be 1
    }

    It 'restores the original config bytes on uninstall' {
        $before = (Get-FileHash (Join-Path $fakeHome 'config.toml')).Hash
        & $installer -CodexHome $fakeHome -SourceDirectory $sourceDirectory
        & $installer -CodexHome $fakeHome -SourceDirectory $sourceDirectory -Uninstall
        (Get-FileHash (Join-Path $fakeHome 'config.toml')).Hash | Should Be $before
        Test-Path (Join-Path $fakeHome 'boo-notify') | Should Be $false
    }
}
```

- [ ] **Step 2: Run Pester and verify RED**

Expected: FAIL because the hook and installer do not exist.

- [ ] **Step 3: Implement the hook**

`Invoke-BooCompletionHook.ps1` must accept all remaining arguments as inert strings. Load `runtime.json` with `ConvertFrom-Json`, invoke the original executable with its stored fixed arguments plus the received payload, catch failures, and then launch `Show-BooCompletion.ps1` asynchronously using `Start-Process powershell.exe -WindowStyle Hidden`. Do not use `Invoke-Expression`, `cmd.exe`, or construct a command string from payload data. Always exit zero.

- [ ] **Step 4: Implement installer and uninstaller**

The installer must:

1. Resolve `$CodexHome` to `C:\Users\user\.codex` by default without repurposing `$HOME`.
2. Parse the existing top-level `notify = [...]` as a JSON-compatible string array; stop without changing files if parsing fails.
3. Write the original command to `runtime.json` with UTF-8 encoding.
4. Copy the module and two runtime scripts into `$CodexHome\boo-notify`.
5. Create the backup only when absent.
6. Replace `notify` with an array equivalent to:

```toml
notify = [ "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", "C:\\Users\\user\\.codex\\boo-notify\\Invoke-BooCompletionHook.ps1", "-RuntimeConfigPath", "C:\\Users\\user\\.codex\\boo-notify\\runtime.json" ]
```

Use a temporary sibling file plus `Move-Item` for atomic config replacement. `-Uninstall` restores the backup only after validating both paths are within the resolved Codex directory.

- [ ] **Step 5: Add the operational README**

Document exact commands:

```powershell
# Install
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\codex-notify\Install-BooCompletionNotification.ps1

# Preview for 20 seconds with sound
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\codex-notify\Show-BooCompletion.ps1 -Preview

# Restore original Codex notification configuration
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\codex-notify\Install-BooCompletionNotification.ps1 -Uninstall
```

State that Codex restart may be required after `config.toml` changes and that the internal pet-position state is not a public stable API, so right-bottom fallback is intentional.

- [ ] **Step 6: Run all tests and verify GREEN**

```powershell
powershell.exe -NoProfile -Command "Invoke-Pester '.\tools\codex-notify\tests\BooCompletionNotification.Tests.ps1'"
git diff --check -- tools/codex-notify
```

Expected: all tests PASS and `git diff --check` produces no output.

- [ ] **Step 7: Commit hook, installer, and documentation**

```powershell
git add -- tools/codex-notify/Invoke-BooCompletionHook.ps1 tools/codex-notify/Install-BooCompletionNotification.ps1 tools/codex-notify/tests/BooCompletionNotification.Tests.ps1 tools/codex-notify/README.md
git commit -m "feat: Codex終了通知をブーへ連携"
```

### Task 4: ローカル導入と実通知検証

**Files:**
- Modify: `C:\Users\user\.codex\config.toml`
- Create: `C:\Users\user\.codex\config.toml.boo-notify.bak`
- Create: `C:\Users\user\.codex\boo-notify\*`

**Interfaces:**
- Consumes: Task 3 installer and runtime scripts
- Produces: このWindowsユーザーのCodex終了通知連携

- [ ] **Step 1: Capture pre-install evidence**

Read only the current top-level `notify` line and confirm the original executable exists. Do not print `auth.json` or unrelated Codex state.

- [ ] **Step 2: Run the installer**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\codex-notify\Install-BooCompletionNotification.ps1
```

Expected: backup, installed directory, and updated single `notify` line are reported.

- [ ] **Step 3: Verify installed artifacts without exposing payloads**

Confirm that the backup hash differs from the updated config hash, `runtime.json` contains the prior command array, and all installed scripts match the repository source hashes.

- [ ] **Step 4: Run visible preview**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\.codex\boo-notify\Show-BooCompletion.ps1" -Preview
```

Expected: a yellow completion bubble appears near Boo, the standard notification sound plays once, and the bubble closes after 20 seconds or when clicked.

- [ ] **Step 5: Verify the hook path**

Invoke the installed hook once with a synthetic completion JSON argument. Confirm the original notification command returns without preventing the bubble process from launching. Do not include conversation text or credentials in the synthetic payload.

- [ ] **Step 6: Final regression checks**

```powershell
powershell.exe -NoProfile -Command "Invoke-Pester '.\tools\codex-notify\tests\BooCompletionNotification.Tests.ps1'"
git status --short
```

Expected: all notification tests PASS; unrelated pre-existing worktree changes remain untouched.

- [ ] **Step 7: Completion handoff**

Report the installed location, backup location, restore command, test count, and whether the visible/audio check was automated or requires the user's sensory confirmation. End the task normally so the configured Codex completion hook provides the first real completion notification.
