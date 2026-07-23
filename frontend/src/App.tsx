import { useCallback, useEffect, useMemo, useState } from 'react'
import './App.css'

type Session = {
  authenticated: boolean
  username: string
  roles: string[]
  mustChangePassword: boolean
  csrfToken: string
  csrfHeaderName: string
}

type Employee = {
  id: number
  username: string
  department: string
  displayName: string
  positionName: string
  employeeCode: string
  workScheduleType: string
  standardStart: string
  standardEnd: string
  standardBreakMinutes: number
  defaultSystemCode: string
}

type DailyEntry = {
  id: number
  workDate: string
  dayType: 'WORKDAY' | 'SATURDAY' | 'SUNDAY' | 'HOLIDAY'
  startTime: string | null
  endTime: string | null
  breakMinutes: number | null
  leaveType: string | null
  workDetail: string
  systemCode: string
  weekdayMinutes: number | null
  holidayMinutes: number | null
  warnings: string[]
}

type MonthlyTotals = {
  weekdayMinutes: number
  holidayMinutes: number
  halfDayCount: number
  requiredDays: number
  requiredMinutes: number
  differenceMinutes: number
  systemTotals: Array<{ systemCode: string; days: number; minutes: number }>
}

type TimesheetHistoryItem = {
  year: number
  month: number
}

type Timesheet = {
  id: number
  year: number
  month: number
  standardStart: string
  standardEnd: string
  standardBreakMinutes: number
  defaultSystemCode: string
  wgParticipation: string | null
  pmarkConfirmationDate: string
  employee: Employee
  entries: DailyEntry[]
  totals: MonthlyTotals
}

type EmployeeAccount = Employee & { enabled: boolean; mustChangePassword: boolean }

type CalendarImportResult = {
  importId: number
  pageCount: number
  importedDates: number
  holidays: Array<{ date: string; name: string }>
}

type ExcelImportResult = {
  importId: number
  targetUsername: string
  sourceEmployeeName: string
  sourceEmployeeCode: string
  year: number
  month: number
  importedRows: number
  warnings: string[]
  timesheet: Timesheet
}

type PendingRecovery = {
  id: number
  username: string
  employeeCode: string
  displayName: string
  requestedAt: string
}

type IntegrationStatus = {
  outlookConfigured: boolean
  slackConfigured: boolean
}

let csrf: Pick<Session, 'csrfToken' | 'csrfHeaderName'> | null = null

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (csrf && options.method && options.method !== 'GET') headers.set(csrf.csrfHeaderName, csrf.csrfToken)
  const response = await fetch(path, { ...options, headers, credentials: 'same-origin' })
  if (!response.ok) {
    const body = await response.json().catch(() => ({ message: '通信に失敗しました。' }))
    const error = new Error(body.message ?? body.detail ?? '通信に失敗しました。') as Error & { status?: number }
    error.status = response.status
    throw error
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

function formatMinutes(value: number | null): string {
  if (value === null) return ''
  const sign = value < 0 ? '-' : ''
  const absolute = Math.abs(value)
  return `${sign}${Math.floor(absolute / 60)}:${String(absolute % 60).padStart(2, '0')}`
}

function formatBreakTime(value: number | null): string {
  if (value === null) return ''
  return `${Math.floor(value / 60)}:${String(value % 60).padStart(2, '0')}`
}

function parseBreakTime(value: string): number | null | undefined {
  const normalized = value.trim().replace(/[０-９]/g, (digit) =>
    String.fromCharCode(digit.charCodeAt(0) - 0xfee0),
  )
  if (!normalized) return null
  const match = normalized.match(/^(\d{1,2}):([0-5]\d)$/)
  if (!match) return undefined
  const minutes = Number(match[1]) * 60 + Number(match[2])
  return minutes <= 1440 ? minutes : undefined
}

function timeValue(value: string | null): string {
  return value?.slice(0, 5) ?? ''
}

function normalizeTimeInput(value: string): string {
  const normalizedDigits = value.trim().replace(/[０-９]/g, (digit) =>
    String.fromCharCode(digit.charCodeAt(0) - 0xfee0),
  )
  if (/^\d{4}$/.test(normalizedDigits)) {
    const hour = Number(normalizedDigits.slice(0, 2))
    const minute = Number(normalizedDigits.slice(2))
    if (hour <= 23 && minute <= 59) {
      return `${normalizedDigits.slice(0, 2)}:${normalizedDigits.slice(2)}`
    }
  }
  return normalizedDigits
}

function generateStrongPassword(): string {
  const groups = [
    'ABCDEFGHJKLMNPQRSTUVWXYZ',
    'abcdefghijkmnopqrstuvwxyz',
    '23456789',
    '!@#$%&*+-=?',
  ]
  const randomIndex = (length: number) => {
    const random = new Uint32Array(1)
    const limit = Math.floor(0x1_0000_0000 / length) * length
    do {
      crypto.getRandomValues(random)
    } while (random[0] >= limit)
    return random[0] % length
  }
  const characters = groups.map((group) => group[randomIndex(group.length)])
  const all = groups.join('')
  while (characters.length < 20) characters.push(all[randomIndex(all.length)])
  for (let index = characters.length - 1; index > 0; index--) {
    const target = randomIndex(index + 1)
    ;[characters[index], characters[target]] = [characters[target], characters[index]]
  }
  return characters.join('')
}

function dayLabel(dateText: string): string {
  const date = new Date(`${dateText}T00:00:00`)
  const weekdays = ['日', '月', '火', '水', '木', '金', '土']
  return `${date.getDate()}(${weekdays[date.getDay()]})`
}

function Login({ onLogin }: { onLogin: (session: Session) => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<{ username?: string; password?: string }>({})
  const [busy, setBusy] = useState(false)
  const [showRecovery, setShowRecovery] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError('')
    const normalizedUsername = username.trim().toLowerCase()
    const nextErrors: { username?: string; password?: string } = {}
    if (!normalizedUsername) nextErrors.username = 'ユーザーIDを入力してください。'
    else if (!/^[a-z0-9._-]{3,50}$/.test(normalizedUsername)) {
      nextErrors.username = '半角英数字と . _ - を使い、3～50文字で入力してください。'
    }
    if (!password) nextErrors.password = 'パスワードを入力してください。'
    else if (password.length > 128) nextErrors.password = 'パスワードは128文字以内で入力してください。'
    setFieldErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return
    setBusy(true)
    try {
      const body = new URLSearchParams({ username: normalizedUsername, password })
      await request<void>('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body,
      })
      const next = await request<Session>('/api/auth/session')
      csrf = next
      onLogin(next)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'ログインできませんでした。')
    } finally {
      setBusy(false)
    }
  }

  if (showRecovery) {
    return <CredentialRecovery onBack={() => { setShowRecovery(false); setError('') }} />
  }

  return (
    <main className="login-page">
      <section className="login-panel" aria-labelledby="login-title">
        <div className="brand-mark">勤</div>
        <p className="eyebrow">社内用</p>
        <h1 id="login-title">勤怠管理システム</h1>
        <p className="muted">勤務表を安全に入力・確認します。</p>
        <form onSubmit={submit} noValidate>
          <label>
            ユーザーID
            <input
              autoComplete="username"
              maxLength={50}
              aria-invalid={Boolean(fieldErrors.username)}
              aria-describedby={fieldErrors.username ? 'username-error' : undefined}
              value={username}
              onChange={(event) => {
                setUsername(event.target.value)
                setFieldErrors((current) => ({ ...current, username: undefined }))
              }}
            />
            {fieldErrors.username && <span id="username-error" className="validation-message">{fieldErrors.username}</span>}
          </label>
          <label>
            パスワード
            <input
              type="password"
              autoComplete="current-password"
              maxLength={128}
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={fieldErrors.password ? 'password-error' : undefined}
              value={password}
              onChange={(event) => {
                setPassword(event.target.value)
                setFieldErrors((current) => ({ ...current, password: undefined }))
              }}
            />
            {fieldErrors.password && <span id="password-error" className="validation-message">{fieldErrors.password}</span>}
          </label>
          {error && <p className="error-message" role="alert">{error}</p>}
          <button className="primary-button" type="submit" disabled={busy}>{busy ? '確認中…' : 'ログイン'}</button>
          <button className="text-button" type="button" onClick={() => setShowRecovery(true)}>
            ユーザーID・パスワードをお忘れの方はこちら
          </button>
        </form>
      </section>
    </main>
  )
}

function CredentialRecovery({ onBack }: { onBack: () => void }) {
  const [employeeCode, setEmployeeCode] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError('')
    setNotice('')
    if (!employeeCode.trim() || !displayName.trim()) {
      setError('社員コードと氏名を入力してください。')
      return
    }
    setBusy(true)
    try {
      const result = await request<{ message: string }>('/api/auth/credential-recovery', {
        method: 'POST',
        body: JSON.stringify({ employeeCode: employeeCode.trim(), displayName: displayName.trim() }),
      })
      setNotice(result.message)
      setEmployeeCode('')
      setDisplayName('')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '再発行依頼を送信できませんでした。')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="password-page">
      <section className="password-panel" aria-labelledby="recovery-title">
        <div className="brand-mark">再</div>
        <p className="eyebrow">ACCOUNT RECOVERY</p>
        <h1 id="recovery-title">ログイン情報の再発行依頼</h1>
        <p className="muted">登録情報を確認し、管理者へ依頼を送ります。この画面ではユーザーIDやパスワードを表示しません。</p>
        <form onSubmit={submit} noValidate>
          <label>社員コード
            <input maxLength={50} value={employeeCode} onChange={(event) => setEmployeeCode(event.target.value)} />
          </label>
          <label>氏名
            <input maxLength={100} autoComplete="name" value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
          </label>
          {error && <p className="error-message" role="alert">{error}</p>}
          {notice && <p className="success-message" role="status">{notice}</p>}
          <div className="form-actions">
            <button className="secondary-button" type="button" onClick={onBack}>ログインへ戻る</button>
            <button className="primary-button" type="submit" disabled={busy}>{busy ? '送信中…' : '管理者へ再発行を依頼'}</button>
          </div>
        </form>
      </section>
    </main>
  )
}

function PasswordChange({
  forced,
  onChanged,
  onCancel,
}: {
  forced: boolean
  onChanged: (session: Session) => void
  onCancel?: () => void
}) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError('')
    if (newPassword !== confirmation) {
      setError('新しいパスワードと確認用の入力が一致しません。')
      return
    }
    setBusy(true)
    try {
      await request<void>('/api/auth/password', {
        method: 'PUT',
        body: JSON.stringify({ currentPassword, newPassword }),
      })
      const next = await request<Session>('/api/auth/session')
      csrf = next
      onChanged(next)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'パスワードを変更できませんでした。')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="password-page">
      <section className="password-panel" aria-labelledby="password-title">
        <div className="brand-mark">鍵</div>
        <p className="eyebrow">{forced ? '初回ログイン' : 'SECURITY'}</p>
        <h1 id="password-title">パスワード変更</h1>
        <p className="muted">
          {forced
            ? '管理者が発行した仮パスワードを、ご本人だけが知るパスワードへ変更してください。変更するまで勤怠データにはアクセスできません。'
            : '現在のパスワードを確認してから、新しいパスワードへ更新します。'}
        </p>
        <form onSubmit={submit}>
          <label>現在のパスワード
            <input type="password" autoComplete="current-password" required maxLength={128} value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} />
          </label>
          <label>新しいパスワード
            <input type="password" autoComplete="new-password" required minLength={12} maxLength={128} value={newPassword} onChange={(event) => setNewPassword(event.target.value)} />
          </label>
          <label>新しいパスワード（確認）
            <input type="password" autoComplete="new-password" required minLength={12} maxLength={128} value={confirmation} onChange={(event) => setConfirmation(event.target.value)} />
          </label>
          <p className="password-hint">12文字以上。長いパスフレーズを推奨します。ユーザー名や推測されやすい文字列は使用できません。</p>
          {error && <p className="error-message" role="alert">{error}</p>}
          <div className="form-actions">
            {!forced && onCancel && <button type="button" className="secondary-button" onClick={onCancel}>キャンセル</button>}
            <button className="primary-button" type="submit" disabled={busy}>{busy ? '変更中…' : 'パスワードを変更'}</button>
          </div>
        </form>
      </section>
    </main>
  )
}

function DeleteTimesheetDialog({
  timesheet,
  apiBase,
  onClose,
  onDeleted,
}: {
  timesheet: Timesheet
  apiBase: string
  onClose: () => void
  onDeleted: () => void
}) {
  const [confirmation, setConfirmation] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setError('')
    if (confirmation !== '削除する') {
      setError('確認欄へ「削除する」と入力してください。')
      return
    }
    if (!password) {
      setError('現在ログイン中のユーザーのパスワードを入力してください。')
      return
    }
    setBusy(true)
    try {
      await request<void>(`${apiBase}/${timesheet.year}/${timesheet.month}`, {
        method: 'DELETE',
        body: JSON.stringify({ confirmation, password }),
      })
      onDeleted()
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '勤務表を削除できませんでした。')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" role="presentation">
      <section className="delete-dialog" role="dialog" aria-modal="true" aria-labelledby="delete-title">
        <p className="eyebrow">DANGER ZONE</p>
        <h2 id="delete-title">{timesheet.year}年{timesheet.month}月の勤務表を削除</h2>
        <p>この勤務表を履歴と通常画面から削除します。監査・復旧のため、DB内のデータは保管されます。</p>
        <p className="delete-target"><strong>対象社員</strong><span>{timesheet.employee.displayName}（{timesheet.employee.employeeCode}）</span></p>
        <form onSubmit={submit} noValidate>
          <label>確認のため「削除する」と入力
            <input value={confirmation} onChange={(event) => setConfirmation(event.target.value)} autoComplete="off" />
          </label>
          <label>現在ログイン中のユーザーのパスワード
            <input type="password" maxLength={128} autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} />
          </label>
          {error && <p className="error-message" role="alert">{error}</p>}
          <div className="form-actions">
            <button className="secondary-button" type="button" onClick={onClose} disabled={busy}>キャンセル</button>
            <button className="danger-button" type="submit" disabled={busy}>{busy ? '削除中…' : '勤務表を削除'}</button>
          </div>
        </form>
      </section>
    </div>
  )
}

function Initializer({ employee, year, month, apiBase, onCreated }: { employee: Employee; year: number; month: number; apiBase: string; onCreated: (value: Timesheet) => void }) {
  const [start, setStart] = useState(timeValue(employee.standardStart))
  const [end, setEnd] = useState(timeValue(employee.standardEnd))
  const [breakText, setBreakText] = useState(formatBreakTime(employee.standardBreakMinutes))
  const [systemCode, setSystemCode] = useState(employee.defaultSystemCode)
  const [error, setError] = useState('')

  async function initialize() {
    setError('')
    const breakMinutes = parseBreakTime(breakText)
    if (breakMinutes === undefined || breakMinutes === null) {
      setError('休憩時間は「1:00」の形式で入力してください。')
      return
    }
    try {
      const result = await request<Timesheet>(`${apiBase}/${year}/${month}/initialize`, {
        method: 'POST',
        body: JSON.stringify({
          standardStart: start,
          standardEnd: end,
          standardBreakMinutes: breakMinutes,
          defaultSystemCode: systemCode,
          overwrite: false,
        }),
      })
      onCreated(result)
    } catch (reason) {
      const typed = reason as Error & { status?: number }
      setError(typed.status === 409
        ? 'この月の勤務表は既にあります。「過去分を参照・編集」から開いてください。'
        : typed.message)
    }
  }

  return (
    <section className="empty-state">
      <p className="eyebrow">{year}年{month}月</p>
      <h2>勤務表を作成</h2>
      <p className="muted">平日は標準時刻と案件番号で自動入力されます。差異がある日だけ修正できます。</p>
      <div className="settings-grid">
        <label>標準始業<input type="time" value={start} onChange={(event) => setStart(event.target.value)} /></label>
        <label>標準終業<input type="time" value={end} onChange={(event) => setEnd(event.target.value)} /></label>
        <label>休憩時間<input type="text" inputMode="numeric" placeholder="1:00" value={breakText} onChange={(event) => setBreakText(event.target.value)} onBlur={() => { const parsed = parseBreakTime(breakText); if (parsed !== undefined && parsed !== null) setBreakText(formatBreakTime(parsed)) }} /></label>
        <label>標準システムNo.<input value={systemCode} maxLength={50} onChange={(event) => setSystemCode(event.target.value)} /></label>
      </div>
      {error && <p className="error-message">{error}</p>}
      <button className="primary-button" onClick={() => initialize()}>Excelの「適用」と同じ内容で作成</button>
    </section>
  )
}

type AdminSection = 'users' | 'excel' | 'calendar' | 'recovery' | 'integrations'

function AdminMenu({ onSelect }: { onSelect: (section: AdminSection) => void }) {
  const items: Array<{ section: AdminSection; number: string; title: string; description: string }> = [
    { section: 'users', number: '01', title: '利用者管理', description: '社員検索・新規登録・仮パスワード再発行' },
    { section: 'excel', number: '02', title: 'Excel勤務表取込', description: '過去の勤務表を社員ごとにDBへ登録' },
    { section: 'calendar', number: '03', title: '会社カレンダー', description: '会社独自の休日をPDFから取り込む' },
    { section: 'recovery', number: '04', title: 'ログイン情報再発行', description: '未処理の再発行依頼を確認する' },
    { section: 'integrations', number: '05', title: 'Outlook・Slack連携', description: '勤務表共有と社内連絡の設定状況を確認する' },
  ]

  return (
    <section className="workspace-home admin-menu-page">
      <div className="home-intro">
        <p className="eyebrow">ADMIN MENU</p>
        <h1>管理者メニュー</h1>
        <p>実行する管理作業を選択してください。</p>
      </div>
      <div className="work-choice-grid admin-choice-grid">
        {items.map((item) => (
          <button className="work-choice admin-menu-choice" key={item.section} onClick={() => onSelect(item.section)}>
            <span className="choice-number">{item.number}</span>
            <strong>{item.title}</strong>
            <small>{item.description}</small>
            <span className="choice-arrow">→</span>
          </button>
        ))}
      </div>
    </section>
  )
}

function IntegrationAdminCard() {
  const [status, setStatus] = useState<IntegrationStatus | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    request<IntegrationStatus>('/api/integrations/status')
      .then(setStatus)
      .catch((reason) => setError(reason instanceof Error ? reason.message : '連携状態を確認できませんでした。'))
  }, [])

  return (
    <article className="admin-card integration-admin-card">
      <h2>連携状態</h2>
      {error && <p className="error-message" role="alert">{error}</p>}
      {!status
        ? <p className="muted">設定状態を確認しています。</p>
        : <div className="integration-status-grid">
          <section>
            <div><strong>Outlook</strong><span className={status.outlookConfigured ? 'status-enabled' : 'status-disabled'}>{status.outlookConfigured ? '利用可能' : '未設定'}</span></div>
            <p>勤務表の月次集計と確認用リンクをMicrosoft Graph経由でメール送信します。</p>
            <small>必要な環境変数: OUTLOOK_TENANT_ID / OUTLOOK_CLIENT_ID / OUTLOOK_CLIENT_SECRET / OUTLOOK_SENDER</small>
          </section>
          <section>
            <div><strong>Slack</strong><span className={status.slackConfigured ? 'status-enabled' : 'status-disabled'}>{status.slackConfigured ? '利用可能' : '未設定'}</span></div>
            <p>設定済みのSlackチャンネルへ勤務表に関する連絡を投稿します。</p>
            <small>必要な環境変数: SLACK_WEBHOOK_URL</small>
          </section>
        </div>}
      <p className="security-note">秘密情報は画面やDBへ保存しません。社内サーバーの環境変数で設定し、設定後にコンテナを再起動してください。</p>
    </article>
  )
}

function AdminPanel({
  currentUsername,
  section,
  onBack,
}: {
  currentUsername: string
  section: AdminSection
  onBack: () => void
}) {
  const [accounts, setAccounts] = useState<EmployeeAccount[]>([])
  const [accountSearch, setAccountSearch] = useState('')
  const [accountSuggestionsOpen, setAccountSuggestionsOpen] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [calendarFile, setCalendarFile] = useState<File | null>(null)
  const [calendarResult, setCalendarResult] = useState<CalendarImportResult | null>(null)
  const [calendarBusy, setCalendarBusy] = useState(false)
  const [excelFile, setExcelFile] = useState<File | null>(null)
  const [excelPassword, setExcelPassword] = useState('')
  const [excelTarget, setExcelTarget] = useState('')
  const [excelBusy, setExcelBusy] = useState(false)
  const [excelResult, setExcelResult] = useState<ExcelImportResult | null>(null)
  const [recoveryRequests, setRecoveryRequests] = useState<PendingRecovery[]>([])
  const [issuedCredential, setIssuedCredential] = useState<{ username: string; password: string } | null>(null)
  const [form, setForm] = useState({
    username: '', password: '', department: '', displayName: '', positionName: '', employeeCode: '',
    workScheduleType: '正社員（8時間）', standardStart: '09:30', standardEnd: '18:30',
    standardBreakTime: '1:00', defaultSystemCode: '',
  })
  const normalizedAccountSearch = accountSearch.trim().toLocaleLowerCase('ja-JP')
  const visibleAccounts = useMemo(() => {
    if (!normalizedAccountSearch) return accounts
    return accounts.filter((account) => [
      account.displayName,
      account.employeeCode,
      account.department,
      account.username,
    ].some((value) => value.toLocaleLowerCase('ja-JP').includes(normalizedAccountSearch)))
  }, [accounts, normalizedAccountSearch])
  const accountSuggestions = normalizedAccountSearch ? visibleAccounts.slice(0, 8) : []

  const load = useCallback(async (query = '') => {
    try {
      const [result, recoveries] = await Promise.all([
        request<EmployeeAccount[]>(`/api/admin/employees?query=${encodeURIComponent(query)}&limit=100`),
        request<PendingRecovery[]>('/api/admin/credential-recovery'),
      ])
      setAccounts(result)
      setRecoveryRequests(recoveries)
      setExcelTarget((current) => current || result.find((account) => account.username !== currentUsername)?.username || result[0]?.username || '')
    }
    catch (reason) { setError(reason instanceof Error ? reason.message : '利用者一覧を取得できませんでした。') }
  }, [currentUsername])

  useEffect(() => { void load() }, [load])

  async function create(event: React.FormEvent) {
    event.preventDefault()
    setError(''); setNotice('')
    const standardBreakMinutes = parseBreakTime(form.standardBreakTime)
    if (standardBreakMinutes === undefined || standardBreakMinutes === null) {
      setError('休憩時間は「1:00」の形式で入力してください。')
      return
    }
    try {
      const { standardBreakTime: _standardBreakTime, ...account } = form
      await request<EmployeeAccount>('/api/admin/employees', {
        method: 'POST',
        body: JSON.stringify({ ...account, standardBreakMinutes }),
      })
      setIssuedCredential({ username: form.username, password: form.password })
      setNotice('利用者を作成しました。仮パスワードは下欄で一度だけ確認し、安全な方法で本人へ伝えてください。')
      setForm({ ...form, username: '', password: '', displayName: '', employeeCode: '' })
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '利用者を作成できませんでした。') }
  }

  async function uploadCalendar(event: React.FormEvent) {
    event.preventDefault()
    if (!calendarFile) {
      setError('会社カレンダーのPDFを選択してください。')
      return
    }
    setCalendarBusy(true); setError(''); setNotice(''); setCalendarResult(null)
    try {
      const body = new FormData()
      body.append('file', calendarFile)
      const result = await request<CalendarImportResult>('/api/admin/company-calendar', {
        method: 'POST',
        body,
      })
      setCalendarResult(result)
      setNotice(`${result.importedDates}日分の会社休日を登録しました。新しく作成または再初期化する勤務表へ反映されます。`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '会社カレンダーを登録できませんでした。')
    } finally {
      setCalendarBusy(false)
    }
  }

  async function importExcel(event: React.FormEvent, overwrite = false) {
    event.preventDefault()
    if (!excelFile || !excelTarget) {
      setError('取込先社員とExcelファイルを選択してください。')
      return
    }
    setExcelBusy(true); setError(''); setNotice(''); setExcelResult(null)
    try {
      const body = new FormData()
      body.append('file', excelFile)
      body.append('password', excelPassword)
      body.append('overwrite', String(overwrite))
      const result = await request<ExcelImportResult>(
        `/api/admin/excel-timesheets/${encodeURIComponent(excelTarget)}`,
        { method: 'POST', body },
      )
      setExcelResult(result)
      setNotice(`${result.year}年${result.month}月の勤務表を${result.importedRows}日分取り込みました。`)
    } catch (reason) {
      const typed = reason as Error & { status?: number }
      if (typed.status === 409 && !overwrite
        && window.confirm('取込先には同じ月の勤務表があります。既存データをExcelの内容で上書きしますか？')) {
        await importExcel(event, true)
      } else {
        setError(typed.message || 'Excel勤務表を取り込めませんでした。')
      }
    } finally {
      setExcelBusy(false)
    }
  }

  async function toggle(account: EmployeeAccount) {
    setError(''); setNotice('')
    try {
      await request<void>(`/api/admin/employees/${encodeURIComponent(account.username)}/enabled`, {
        method: 'PUT', body: JSON.stringify({ enabled: !account.enabled }),
      })
      setNotice(`${account.displayName}を${account.enabled ? '無効' : '有効'}にしました。`)
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : '状態を変更できませんでした。') }
  }

  async function resetPassword(account: Pick<EmployeeAccount, 'username' | 'displayName'>) {
    if (!window.confirm(`${account.displayName}の仮パスワードを再発行しますか？本人は次回ログイン時に変更が必要になります。`)) return
    const password = generateStrongPassword()
    setError(''); setNotice('')
    try {
      await request<void>(`/api/admin/employees/${encodeURIComponent(account.username)}/password`, {
        method: 'PUT', body: JSON.stringify({ password }),
      })
      setIssuedCredential({ username: account.username, password })
      setNotice(`${account.displayName}の仮パスワードを再発行しました。`)
      await load()
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'パスワードを変更できませんでした。') }
  }

  const sectionDetails: Record<AdminSection, { title: string; description: string }> = {
    users: { title: '利用者管理', description: '社員の検索・登録・アカウント状態を管理します。' },
    excel: { title: 'Excel勤務表取込', description: '過去の勤務表を選択した社員のデータとして登録します。' },
    calendar: { title: '会社カレンダー', description: '会社独自の休日をPDFから登録します。' },
    recovery: { title: 'ログイン情報再発行', description: '利用者から届いた再発行依頼を処理します。' },
    integrations: { title: 'Outlook・Slack連携', description: '勤務表共有と社員連絡の連携状態を確認します。' },
  }

  return (
    <section className="admin-page">
      <div className="section-title admin-section-title">
        <div><p className="eyebrow">管理者専用</p><h1>{sectionDetails[section].title}</h1><p className="muted">{sectionDetails[section].description}</p></div>
        <button type="button" onClick={onBack}>← 管理者メニューへ戻る</button>
      </div>
      {error && <p className="error-message" role="alert">{error}</p>}
      {notice && <p className="success-message">{notice}</p>}
      {issuedCredential && (section === 'users' || section === 'recovery') && <aside className="credential-box" aria-live="polite">
        <div><strong>仮ログイン情報（一度だけ表示）</strong><button type="button" onClick={() => setIssuedCredential(null)}>表示を閉じる</button></div>
        <dl><dt>ユーザー名</dt><dd>{issuedCredential.username}</dd><dt>仮パスワード</dt><dd><code>{issuedCredential.password}</code></dd></dl>
        <p>チャットや平文メールへの貼り付けは避け、本人確認後に安全な経路で伝えてください。</p>
      </aside>}
      {section === 'recovery' && <article className="admin-card featured-admin-card">
        <div className="card-heading">
          <div><p className="eyebrow">アカウント復旧</p><h2>再発行依頼</h2></div>
          <span className="permission-badge">{recoveryRequests.length}件</span>
        </div>
        {recoveryRequests.length === 0
          ? <p className="muted">未処理の再発行依頼はありません。</p>
          : <div className="account-list">
            {recoveryRequests.map((recovery) => <div className="account-row" key={recovery.id}>
              <div><strong>{recovery.displayName}</strong><span>{recovery.employeeCode} · 受付 {new Date(recovery.requestedAt).toLocaleString('ja-JP')}</span></div>
              <span className="status-disabled">未処理</span>
              <div className="account-actions">
                <button onClick={() => resetPassword(recovery)}>仮パスワードを再発行</button>
              </div>
            </div>)}
          </div>}
      </article>}
      {section === 'excel' && <article className="admin-card featured-admin-card">
        <div className="card-heading">
          <div><p className="eyebrow">過去データ移行</p><h2>Excel勤務表を取り込む</h2></div>
          <span className="permission-badge">管理者のみ</span>
        </div>
        <p className="muted">提供済みの勤務表形式（.xlsx / .xlsm）を解析し、選択した社員の過去勤務表としてDBへ登録します。ファイル本体とパスワードは保存しません。</p>
        <form className="excel-import-form" onSubmit={(event) => void importExcel(event)}>
          <label>取込先社員
            <select value={excelTarget} required onChange={(event) => setExcelTarget(event.target.value)}>
              <option value="">社員を選択</option>
              {accounts.map((account) => <option key={account.username} value={account.username}>{account.displayName}（{account.employeeCode}）</option>)}
            </select>
          </label>
          <label>勤務表Excel
            <input type="file" accept=".xlsx,.xlsm,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel.sheet.macroEnabled.12" onChange={(event) => setExcelFile(event.target.files?.[0] ?? null)} required />
          </label>
          <label>Excelパスワード
            <input type="password" maxLength={128} value={excelPassword} onChange={(event) => setExcelPassword(event.target.value)} placeholder="パスワードなしの場合は空欄" />
          </label>
          <button className="primary-button" type="submit" disabled={excelBusy}>{excelBusy ? '解析・登録中…' : 'Excelを解析して登録'}</button>
        </form>
        {excelResult && <div className="import-result">
          <strong>{excelResult.year}年{String(excelResult.month).padStart(2, '0')}月・{excelResult.importedRows}日</strong>
          <span>Excel記載: {excelResult.sourceEmployeeName || '氏名なし'} / {excelResult.sourceEmployeeCode || 'コードなし'}</span>
          {excelResult.warnings.map((warning) => <p className="warning-message" key={warning}>{warning}</p>)}
        </div>}
      </article>}
      {section === 'calendar' && <article className="admin-card">
        <h2>会社カレンダーPDF</h2>
        <p className="muted">休日名と日付が文字として記載されたPDFを取り込みます。PDF本体は保存せず、抽出した会社休日だけを登録します。</p>
        <form className="calendar-upload" onSubmit={uploadCalendar}>
          <label>カレンダーPDF<input type="file" accept="application/pdf,.pdf" onChange={(event) => setCalendarFile(event.target.files?.[0] ?? null)} /></label>
          <button className="primary-button" type="submit" disabled={calendarBusy}>{calendarBusy ? '解析中…' : '休日を抽出して登録'}</button>
        </form>
        {calendarResult && <div className="calendar-result">
          <strong>抽出結果: {calendarResult.importedDates}日</strong>
          <ul>{calendarResult.holidays.map((holiday) => <li key={holiday.date}>{holiday.date}　{holiday.name}</li>)}</ul>
        </div>}
      </article>}
      {section === 'users' && <article className="admin-card">
        <h2>新しい利用者</h2>
        <form className="account-form" onSubmit={create}>
          <label>ユーザー名<input value={form.username} pattern="[a-z0-9._-]{3,50}" required onChange={(event) => setForm({ ...form, username: event.target.value })} /></label>
          <label>初期パスワード
            <span className="password-input-row">
              <input type="text" autoComplete="off" minLength={12} maxLength={128} required value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} />
              <button type="button" onClick={() => setForm({ ...form, password: generateStrongPassword() })}>自動生成</button>
            </span>
          </label>
          <label>氏名<input value={form.displayName} required maxLength={100} onChange={(event) => setForm({ ...form, displayName: event.target.value })} /></label>
          <label>社員コード<input value={form.employeeCode} required maxLength={50} onChange={(event) => setForm({ ...form, employeeCode: event.target.value })} /></label>
          <label>所属<input value={form.department} maxLength={100} onChange={(event) => setForm({ ...form, department: event.target.value })} /></label>
          <label>役職<input value={form.positionName} maxLength={100} onChange={(event) => setForm({ ...form, positionName: event.target.value })} /></label>
          <label>勤務表種別<select value={form.workScheduleType} onChange={(event) => setForm({ ...form, workScheduleType: event.target.value })}><option>正社員（8時間）</option><option>6時間勤務</option><option>7時間勤務</option></select></label>
          <label>標準システムNo.<input value={form.defaultSystemCode} maxLength={50} onChange={(event) => setForm({ ...form, defaultSystemCode: event.target.value })} /></label>
          <label>標準始業<input type="time" value={form.standardStart} required onChange={(event) => setForm({ ...form, standardStart: event.target.value })} /></label>
          <label>標準終業<input type="time" value={form.standardEnd} required onChange={(event) => setForm({ ...form, standardEnd: event.target.value })} /></label>
          <label>休憩時間<input type="text" inputMode="numeric" placeholder="1:00" value={form.standardBreakTime} onChange={(event) => setForm({ ...form, standardBreakTime: event.target.value })} /></label>
          <button className="primary-button" type="submit">利用者を作成</button>
        </form>
      </article>}
      {section === 'users' && <article className="admin-card">
        <div className="card-heading"><h2>登録済み利用者</h2><span>{visibleAccounts.length}件表示</span></div>
        <form className="account-search" onSubmit={(event) => event.preventDefault()}>
          <label>社員検索
            <span className="predictive-search">
              <input
                value={accountSearch}
                autoComplete="off"
              onChange={(event) => { setAccountSearch(event.target.value); setAccountSuggestionsOpen(true) }}
              onFocus={() => setAccountSuggestionsOpen(true)}
                placeholder="氏名・社員コード・所属・ユーザー名"
                aria-autocomplete="list"
                aria-controls="account-search-suggestions"
              />
              {accountSuggestionsOpen && accountSuggestions.length > 0 && <span className="search-suggestions" id="account-search-suggestions" role="listbox" aria-label="社員検索候補">
                {accountSuggestions.map((account) => (
                  <button type="button" role="option" key={account.username} onClick={() => { setAccountSearch(account.displayName); setAccountSuggestionsOpen(false) }}>
                    <strong>{account.displayName}</strong>
                    <small>{account.employeeCode} · {account.department || '所属未設定'} · {account.username}</small>
                  </button>
                ))}
              </span>}
            </span>
          </label>
          {accountSearch && <button type="button" onClick={() => { setAccountSearch(''); setAccountSuggestionsOpen(false) }}>クリア</button>}
        </form>
        <div className="account-list">
          {visibleAccounts.map((account) => <div className="account-row" key={account.username}>
            <div><strong>{account.displayName}</strong><span>{account.employeeCode} · {account.department || '所属未設定'} · {account.username}</span></div>
            <span className={account.enabled ? 'status-enabled' : 'status-disabled'}>{account.enabled ? '有効' : '無効'}</span>
            <div className="account-actions">
              {account.mustChangePassword && <span className="password-change-pending">初回変更待ち</span>}
              <button onClick={() => resetPassword(account)}>仮パスワード再発行</button>
              <button disabled={account.username === currentUsername} onClick={() => toggle(account)}>{account.enabled ? '無効化' : '有効化'}</button>
            </div>
          </div>)}
        </div>
      </article>}
      {section === 'integrations' && <IntegrationAdminCard />}
    </section>
  )
}

function EntryRow({ entry, onSaved, year, month, apiBase }: { entry: DailyEntry; onSaved: (value: Timesheet) => void; year: number; month: number; apiBase: string }) {
  const [draft, setDraft] = useState(entry)
  const [breakText, setBreakText] = useState(formatBreakTime(entry.breakMinutes))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    setDraft(entry)
    setBreakText(formatBreakTime(entry.breakMinutes))
  }, [entry])

  async function save() {
    setSaving(true)
    setError('')
    const breakMinutes = parseBreakTime(breakText)
    if (breakMinutes === undefined) {
      setError('休憩時間は1:00形式')
      setSaving(false)
      return
    }
    try {
      const result = await request<Timesheet>(`${apiBase}/${year}/${month}/entries/${entry.workDate}`, {
        method: 'PUT',
        body: JSON.stringify({
          startTime: draft.startTime || null,
          endTime: draft.endTime || null,
          breakMinutes,
          leaveType: draft.leaveType || null,
          workDetail: draft.workDetail,
          systemCode: draft.systemCode,
        }),
      })
      onSaved(result)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '保存できませんでした。')
    } finally {
      setSaving(false)
    }
  }

  function normalizeDraftTime(field: 'startTime' | 'endTime', value: string) {
    const normalized = normalizeTimeInput(value)
    setDraft((current) => ({ ...current, [field]: normalized || null }))
  }

  const rowClass = draft.dayType === 'WORKDAY' ? '' : draft.dayType.toLowerCase()
  return (
    <tr className={rowClass}>
      <th scope="row" className="date-cell">{dayLabel(draft.workDate)}</th>
      <td><input aria-label={`${dayLabel(draft.workDate)} 始業`} title="4桁入力できます（例: 1800 → 18:00）" type="text" inputMode="numeric" placeholder="--:--" maxLength={5} value={timeValue(draft.startTime)} onChange={(event) => setDraft({ ...draft, startTime: event.target.value || null })} onBlur={(event) => normalizeDraftTime('startTime', event.target.value)} /></td>
      <td><input aria-label={`${dayLabel(draft.workDate)} 終業`} title="4桁入力できます（例: 1800 → 18:00）" type="text" inputMode="numeric" placeholder="--:--" maxLength={5} value={timeValue(draft.endTime)} onChange={(event) => setDraft({ ...draft, endTime: event.target.value || null })} onBlur={(event) => normalizeDraftTime('endTime', event.target.value)} /></td>
      <td><input className="break-input" aria-label={`${dayLabel(draft.workDate)} 休憩時間`} type="text" inputMode="numeric" placeholder="--:--" maxLength={5} value={breakText} onChange={(event) => setBreakText(event.target.value)} onBlur={() => { const parsed = parseBreakTime(breakText); if (parsed !== undefined) setBreakText(formatBreakTime(parsed)) }} /></td>
      <td className="calculated">{formatMinutes(draft.weekdayMinutes)}</td>
      <td className="calculated">{formatMinutes(draft.holidayMinutes)}</td>
      <td>
        <select aria-label={`${dayLabel(draft.workDate)} 休暇種別`} value={draft.leaveType ?? ''} onChange={(event) => setDraft({ ...draft, leaveType: event.target.value || null })}>
          <option value="">—</option>
          {['AM半休', 'PM半休', '有給休暇', '特別休暇', '慶弔休暇', '振替休日', '欠勤'].map((value) => <option key={value}>{value}</option>)}
        </select>
      </td>
      <td><input className="detail-input" aria-label={`${dayLabel(draft.workDate)} 業務内容`} value={draft.workDetail} maxLength={500} onChange={(event) => setDraft({ ...draft, workDetail: event.target.value })} /></td>
      <td><input className="code-input" aria-label={`${dayLabel(draft.workDate)} システム番号`} value={draft.systemCode} maxLength={50} onChange={(event) => setDraft({ ...draft, systemCode: event.target.value })} /></td>
      <td className="action-cell">
        <button className="save-button" onClick={save} disabled={saving}>{saving ? '保存中' : '保存'}</button>
        {draft.warnings.length > 0 && <span className="warning-dot" title={draft.warnings.join('\n')} aria-label={draft.warnings.join(' ')}>!</span>}
        {error && <span className="row-error" title={error}>×</span>}
      </td>
    </tr>
  )
}

function CommunicationPanel({ value }: { value: Timesheet }) {
  const [status, setStatus] = useState<IntegrationStatus | null>(null)
  const [recipient, setRecipient] = useState('')
  const [slackMessage, setSlackMessage] = useState('')
  const [busy, setBusy] = useState<'outlook' | 'slack' | null>(null)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    request<IntegrationStatus>('/api/integrations/status')
      .then(setStatus)
      .catch((reason) => setError(reason instanceof Error ? reason.message : '連携状態を確認できませんでした。'))
  }, [])

  async function sendOutlook(event: React.FormEvent) {
    event.preventDefault()
    setBusy('outlook'); setError(''); setNotice('')
    try {
      await request<void>('/api/integrations/outlook', {
        method: 'POST',
        body: JSON.stringify({ recipient, year: value.year, month: value.month }),
      })
      setRecipient('')
      setNotice('Outlookへ勤務表の連絡を送信しました。')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Outlookへ送信できませんでした。')
    } finally {
      setBusy(null)
    }
  }

  async function sendSlack(event: React.FormEvent) {
    event.preventDefault()
    setBusy('slack'); setError(''); setNotice('')
    try {
      await request<void>('/api/integrations/slack', {
        method: 'POST',
        body: JSON.stringify({ message: slackMessage, year: value.year, month: value.month }),
      })
      setSlackMessage('')
      setNotice('Slackへ勤務表の連絡を投稿しました。')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Slackへ投稿できませんでした。')
    } finally {
      setBusy(null)
    }
  }

  return (
    <details className="communication-panel">
      <summary>Outlook・Slackで共有</summary>
      {error && <p className="error-message" role="alert">{error}</p>}
      {notice && <p className="success-message" role="status">{notice}</p>}
      <div className="communication-grid">
        <form onSubmit={sendOutlook}>
          <div className="integration-heading">
            <strong>Outlook</strong>
            <span className={status?.outlookConfigured ? 'status-enabled' : 'status-disabled'}>{status?.outlookConfigured ? '利用可能' : '未設定'}</span>
          </div>
          <p>月次集計と確認用リンクをメールで送ります。</p>
          <label>送信先メールアドレス
            <input type="email" autoComplete="off" required maxLength={254} value={recipient} onChange={(event) => setRecipient(event.target.value)} placeholder="recipient@example.co.jp" />
          </label>
          <button className="primary-button" type="submit" disabled={!status?.outlookConfigured || busy !== null}>{busy === 'outlook' ? '送信中…' : 'Outlookへ送信'}</button>
        </form>
        <form onSubmit={sendSlack}>
          <div className="integration-heading">
            <strong>Slack</strong>
            <span className={status?.slackConfigured ? 'status-enabled' : 'status-disabled'}>{status?.slackConfigured ? '利用可能' : '未設定'}</span>
          </div>
          <p>設定済みの社内チャンネルへ連絡します。</p>
          <label>メッセージ（任意）
            <input type="text" autoComplete="off" maxLength={500} value={slackMessage} onChange={(event) => setSlackMessage(event.target.value)} placeholder="確認をお願いします" />
          </label>
          <button className="primary-button" type="submit" disabled={!status?.slackConfigured || busy !== null}>{busy === 'slack' ? '投稿中…' : 'Slackへ投稿'}</button>
        </form>
      </div>
    </details>
  )
}

function TimesheetView({ value, apiBase, onChange }: { value: Timesheet; apiBase: string; onChange: (value: Timesheet) => void }) {
  const [wgBusy, setWgBusy] = useState(false)

  async function updateWg(value: string) {
    setWgBusy(true)
    try {
      onChange(await request<Timesheet>(`${apiBase}/${valueYear}/${valueMonth}/wg-participation`, {
        method: 'PUT',
        body: JSON.stringify({ value: value || null }),
      }))
    } finally {
      setWgBusy(false)
    }
  }

  const valueYear = value.year
  const valueMonth = value.month
  const systemRows = [
    ...value.totals.systemTotals,
    ...Array.from({ length: Math.max(0, 5 - value.totals.systemTotals.length) }, (_, index) => ({
      systemCode: '',
      days: 0,
      minutes: 0,
      placeholder: true,
      index,
    })),
  ]
  const totalSystemDays = value.totals.systemTotals.reduce((sum, total) => sum + total.days, 0)
  const totalSystemMinutes = value.totals.systemTotals.reduce((sum, total) => sum + total.minutes, 0)
  return (
    <section className="excel-sheet" aria-label={`${value.year}年${value.month}月 勤務表`}>
      <section className="sheet-heading">
        <h1><span>勤</span><span>務</span><span>表</span><small>（{value.employee.workScheduleType.replace('（8時間）', '')}）</small></h1>
        <strong>{value.year}年{String(value.month).padStart(2, '0')}月分</strong>
      </section>
      <section className="identity-grid">
        <dl><dt>所属</dt><dd>{value.employee.department || '—'}</dd><dt>氏名</dt><dd>{value.employee.displayName}</dd></dl>
        <dl><dt>役職</dt><dd>{value.employee.positionName || '—'}</dd><dt>コード</dt><dd>{value.employee.employeeCode}</dd></dl>
        <dl><dt>Pマーク運用確認日</dt><dd>{value.pmarkConfirmationDate}</dd><dt>WG参加可否</dt><dd><select disabled={wgBusy} value={value.wgParticipation ?? ''} onChange={(event) => updateWg(event.target.value)}><option value="">未選択</option><option>参加</option><option>不参加</option><option>当月未開催</option></select></dd></dl>
      </section>
      {apiBase === '/api/timesheets' && <CommunicationPanel value={value} />}
      <section className="table-card">
        <div className="table-scroll">
          <table className="attendance-table">
            <thead>
              <tr>
                <th rowSpan={2}>日付</th>
                <th colSpan={3}>勤務時間</th>
                <th colSpan={2}>労働時間</th>
                <th rowSpan={2}>休暇種別</th>
                <th rowSpan={2}>業務内容</th>
                <th rowSpan={2}>システムNo.</th>
                <th className="utility-column" rowSpan={2}>保存</th>
              </tr>
              <tr><th>始業</th><th>終業</th><th>休憩</th><th>平日</th><th>休日</th></tr>
            </thead>
            <tbody>{value.entries.map((entry) => <EntryRow key={entry.id} entry={entry} year={value.year} month={value.month} apiBase={apiBase} onSaved={onChange} />)}</tbody>
            <tfoot>
              <tr><th colSpan={4}>計</th><td>{formatMinutes(value.totals.weekdayMinutes)}</td><td>{formatMinutes(value.totals.holidayMinutes)}</td><td colSpan={4}></td></tr>
            </tfoot>
          </table>
        </div>
      </section>
      <section className="summary-grid">
        <article className="summary-card">
          <table>
            <thead><tr><th>No.</th><th>システムNo.</th><th>日数</th><th>時間</th></tr></thead>
            <tbody>
              {systemRows.map((total, index) => (
                <tr key={'placeholder' in total ? `blank-${index}` : total.systemCode}>
                  <td>{index + 1}</td>
                  <td>{'placeholder' in total ? '' : total.systemCode}</td>
                  <td>{'placeholder' in total ? '' : `${total.days.toFixed(1)} 日`}</td>
                  <td>{'placeholder' in total ? '' : formatMinutes(total.minutes)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot><tr><th colSpan={2}>計</th><td>{totalSystemDays.toFixed(1)} 日</td><td>{formatMinutes(totalSystemMinutes)}</td></tr></tfoot>
          </table>
        </article>
        <article className="metric-card">
          <table>
            <thead><tr><th>半休回数</th><th>所定日数</th><th>所定時間</th><th>過不足時間</th></tr></thead>
            <tbody><tr>
              <td>{value.totals.halfDayCount} 回</td>
              <td>{value.totals.requiredDays.toFixed(1)} 日</td>
              <td>{formatMinutes(value.totals.requiredMinutes)}</td>
              <td className={value.totals.differenceMinutes < 0 ? 'negative' : ''}>{formatMinutes(value.totals.differenceMinutes)}</td>
            </tr></tbody>
          </table>
          <div className="signatures">
            <div><strong>取締役</strong><span></span></div>
            <div><strong>上長 or 担当リーダー</strong><span></span></div>
            <div><strong>総務部（備考）</strong><span></span></div>
          </div>
        </article>
      </section>
    </section>
  )
}

function OwnExcelImport({
  onImported,
  onCancel,
}: {
  onImported: (result: ExcelImportResult) => void
  onCancel: () => void
}) {
  const [file, setFile] = useState<File | null>(null)
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function importExcel(overwrite = false) {
    if (!file) {
      setError('過去の勤務表Excelを選択してください。')
      return
    }
    setBusy(true)
    setError('')
    try {
      const body = new FormData()
      body.append('file', file)
      body.append('password', password)
      body.append('overwrite', String(overwrite))
      const result = await request<ExcelImportResult>('/api/excel-timesheets', {
        method: 'POST',
        body,
      })
      onImported(result)
    } catch (reason) {
      const typed = reason as Error & { status?: number }
      if (typed.status === 409 && !overwrite
        && window.confirm('同じ月の勤務表が登録されています。Excelの内容で上書きしますか？')) {
        await importExcel(true)
      } else {
        setError(typed.message || 'Excel勤務表を取り込めませんでした。')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="import-page">
      <div className="section-title">
        <p className="eyebrow">PAST DATA</p>
        <h1>過去のExcel勤務表を取り込む</h1>
        <p className="muted">自分の勤務表をDBへ登録し、登録後すぐにこのアプリ上で参照・編集できます。</p>
      </div>
      <article className="admin-card featured-admin-card">
        <h2>Excelファイルを選択</h2>
        <p className="muted">対応形式は提供済み勤務表と同じ .xlsx / .xlsm、上限10MBです。ファイル本体とパスワードは保存しません。</p>
        <form className="own-import-form" onSubmit={(event) => { event.preventDefault(); void importExcel() }}>
          <label>過去の勤務表Excel
            <input
              type="file"
              accept=".xlsx,.xlsm,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel.sheet.macroEnabled.12"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
          </label>
          <label>Excelパスワード
            <input type="password" maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="パスワードなしの場合は空欄" />
          </label>
          {error && <p className="error-message" role="alert">{error}</p>}
          <div className="form-actions">
            <button className="secondary-button" type="button" onClick={onCancel}>キャンセル</button>
            <button className="primary-button" type="submit" disabled={busy}>{busy ? '解析・登録中…' : 'Excelを解析して登録'}</button>
          </div>
        </form>
      </article>
    </section>
  )
}

function WorkspaceHome({
  employee,
  historyCount,
  isAdmin,
  onNew,
  onHistory,
  onImport,
  onAdmin,
}: {
  employee: Employee
  historyCount: number
  isAdmin: boolean
  onNew: () => void
  onHistory: () => void
  onImport: () => void
  onAdmin: () => void
}) {
  return (
    <section className="workspace-home">
      <div className="home-intro">
        <p className="eyebrow">WORK MENU</p>
        <h1>{employee.displayName}さん、勤務表の操作を選択してください</h1>
        <p>新しい月の勤務表を作成するか、登録済みの過去データを参照・編集します。</p>
      </div>
      <div className="work-choice-grid">
        <button className="work-choice primary-choice" onClick={onNew}>
          <span className="choice-number">01</span>
          <strong>新規で勤務表を作成</strong>
          <small>今月の勤務表を作成・入力します</small>
          <span className="choice-arrow">→</span>
        </button>
        <button className="work-choice" onClick={onHistory}>
          <span className="choice-number">02</span>
          <strong>過去分を参照・編集</strong>
          <small>登録済み {historyCount}か月分から選択します</small>
          <span className="choice-arrow">→</span>
        </button>
        <button className="work-choice import-choice" onClick={onImport}>
          <span className="choice-number">03</span>
          <strong>過去のExcelを取り込む</strong>
          <small>以前の勤務表をDBへ登録して反映します</small>
          <span className="choice-arrow">→</span>
        </button>
        {isAdmin && <button className="work-choice admin-choice" onClick={onAdmin}>
          <span className="choice-number">ADMIN</span>
          <strong>管理者メニュー</strong>
          <small>社員検索・Excel取込・利用者管理</small>
          <span className="choice-arrow">→</span>
        </button>}
      </div>
    </section>
  )
}

function App() {
  type Screen = 'home' | 'new' | 'history' | 'import' | 'admin-menu'
    | 'admin-users' | 'admin-excel' | 'admin-calendar' | 'admin-recovery' | 'admin-integrations' | 'password'
  const today = useMemo(() => new Date(), [])
  const [session, setSession] = useState<Session | null>(null)
  const [employee, setEmployee] = useState<Employee | null>(null)
  const [year, setYear] = useState(today.getFullYear())
  const [month, setMonth] = useState(today.getMonth() + 1)
  const [timesheet, setTimesheet] = useState<Timesheet | null>(null)
  const [notFound, setNotFound] = useState(false)
  const [error, setError] = useState('')
  const [screen, setScreen] = useState<Screen>('home')
  const [history, setHistory] = useState<TimesheetHistoryItem[]>([])
  const [editableEmployees, setEditableEmployees] = useState<EmployeeAccount[]>([])
  const [selectedUsername, setSelectedUsername] = useState('')
  const [employeeSearch, setEmployeeSearch] = useState('')
  const [importNotice, setImportNotice] = useState<ExcelImportResult | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Timesheet | null>(null)

  const isAdmin = session?.roles.includes('ROLE_ADMIN') ?? false
  const targetUsername = selectedUsername || session?.username || ''
  const targetEmployee = targetUsername === session?.username
    ? employee
    : editableEmployees.find((account) => account.username === targetUsername) ?? null
  const normalizedEmployeeSearch = employeeSearch.trim().toLocaleLowerCase('ja-JP')
  const employeeSuggestions = normalizedEmployeeSearch
    ? editableEmployees.filter((account) => [
      account.displayName,
      account.employeeCode,
      account.department,
      account.username,
    ].some((value) => value.toLocaleLowerCase('ja-JP').includes(normalizedEmployeeSearch))).slice(0, 8)
    : []
  const timesheetApiBase = targetUsername && targetUsername !== session?.username
    ? `/api/admin/employees/${encodeURIComponent(targetUsername)}/timesheets`
    : '/api/timesheets'

  const searchEmployees = useCallback(async (query: string) => {
    if (!isAdmin) return
    try {
      const result = await request<EmployeeAccount[]>(
        `/api/admin/employees?query=${encodeURIComponent(query)}&limit=100`,
      )
      setEditableEmployees((current) => {
        const selected = current.find((account) => account.username === targetUsername)
        return selected && !result.some((account) => account.username === selected.username)
          ? [selected, ...result]
          : result
      })
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '社員を検索できませんでした。')
    }
  }, [isAdmin, targetUsername])

  useEffect(() => {
    request<Session>('/api/auth/session').then((value) => {
      csrf = value
      setSession(value)
    }).catch((reason) => setError(String(reason)))
  }, [])

  useEffect(() => {
    if (!session?.authenticated || session.mustChangePassword) return
    request<Employee>('/api/me')
      .then((value) => {
        setEmployee(value)
        setSelectedUsername((current) => current || value.username)
      })
      .catch((reason) => setError(reason.message))
  }, [session])

  useEffect(() => {
    if (!session?.authenticated || session.mustChangePassword || !isAdmin) {
      setEditableEmployees([])
      return
    }
    request<EmployeeAccount[]>('/api/admin/employees?limit=100')
      .then(setEditableEmployees)
      .catch((reason) => setError(reason.message))
  }, [session, isAdmin])

  useEffect(() => {
    setEmployeeSearch('')
  }, [screen])

  useEffect(() => {
    if (!isAdmin || (screen !== 'new' && screen !== 'history')) return
    const timer = window.setTimeout(
      () => { void searchEmployees(employeeSearch) },
      employeeSearch.trim() ? 250 : 0,
    )
    return () => window.clearTimeout(timer)
  }, [employeeSearch, isAdmin, screen, searchEmployees])

  useEffect(() => {
    if (!session?.authenticated || session.mustChangePassword || !targetEmployee) return
    request<TimesheetHistoryItem[]>(`${timesheetApiBase}/history`)
      .then(setHistory)
      .catch((reason) => setError(reason.message))
  }, [session, targetEmployee, timesheetApiBase])

  useEffect(() => {
    if (!session?.authenticated || session.mustChangePassword || !targetEmployee || screen !== 'history') return
    setError('')
    request<Timesheet>(`${timesheetApiBase}/${year}/${month}`)
      .then((value) => { setTimesheet(value); setNotFound(false) })
      .catch((reason: Error & { status?: number }) => {
        if (reason.status === 404) { setTimesheet(null); setNotFound(true) }
        else setError(reason.message)
      })
  }, [session, targetEmployee, timesheetApiBase, year, month, screen])

  function selectEmployee(username: string) {
    setSelectedUsername(username)
    setEmployeeSearch('')
    setTimesheet(null)
    setNotFound(screen === 'new')
    setHistory([])
    setError('')
  }

  function openAdminSection(section: AdminSection) {
    setScreen(`admin-${section}` as Screen)
    setError('')
  }

  function moveMonth(offset: number) {
    const next = new Date(year, month - 1 + offset, 1)
    setYear(next.getFullYear())
    setMonth(next.getMonth() + 1)
    setTimesheet(null)
    setNotFound(true)
  }

  function selectHistory(target: string) {
    if (!target) return
    const [selectedYear, selectedMonth] = target.split('-').map(Number)
    setYear(selectedYear)
    setMonth(selectedMonth)
  }

  function rememberMonth(value: Timesheet) {
    setHistory((current) => {
      if (current.some((item) => item.year === value.year && item.month === value.month)) return current
      return [...current, { year: value.year, month: value.month }]
        .sort((left, right) => right.year - left.year || right.month - left.month)
    })
  }

  async function logout() {
    await request<void>('/api/auth/logout', { method: 'POST' })
    const next = await request<Session>('/api/auth/session')
    csrf = next
    setSession(next)
    setEmployee(null)
    setTimesheet(null)
    setHistory([])
    setEditableEmployees([])
    setSelectedUsername('')
    setEmployeeSearch('')
    setImportNotice(null)
    setDeleteTarget(null)
    setScreen('home')
  }

  if (error && !session) return <main className="center-message"><p className="error-message">{error}</p></main>
  if (!session) return <main className="center-message">読み込み中…</main>
  if (!session.authenticated) return <Login onLogin={setSession} />
  if (session.mustChangePassword) return <PasswordChange forced onChanged={(next) => { setSession(next); setScreen('home') }} />
  if (!employee) return <main className="center-message">従業員情報を読み込み中…</main>
  if (screen === 'password') return <PasswordChange forced={false} onChanged={(next) => { setSession(next); setScreen('home') }} onCancel={() => setScreen('home')} />

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="header-brand">
          <img className="company-logo" src="/query-logo-header.png" alt="株式会社クエリ" />
          <strong>勤怠管理システム</strong>
          <span className="internal-badge">社内用</span>
        </div>
        <div className="header-actions">
          <nav>
            <button onClick={() => { setScreen('home'); setError('') }}>ホーム</button>
            {isAdmin && <button onClick={() => { setScreen('admin-menu'); setError('') }}>管理者メニュー</button>}
            <button onClick={() => { setScreen('password'); setError('') }}>パスワード変更</button>
            {(screen === 'new' || screen === 'history') && <button onClick={() => window.print()}>印刷</button>}
            <button onClick={logout}>ログアウト</button>
          </nav>
          <div className="current-user">
            <span>ログイン中</span>
            <strong>{employee.displayName}</strong>
            <small>ユーザーID: {session.username}・{employee.employeeCode}{employee.department ? `・${employee.department}` : ''}</small>
          </div>
        </div>
      </header>
      <main className="content">
        {screen === 'home' && <WorkspaceHome
          employee={employee}
          historyCount={history.length}
          isAdmin={isAdmin}
          onNew={() => {
            setYear(today.getFullYear())
            setMonth(today.getMonth() + 1)
            setTimesheet(null)
            setNotFound(true)
            setScreen('new')
            setError('')
          }}
          onHistory={() => {
            const latest = history[0]
            if (latest) {
              setYear(latest.year)
              setMonth(latest.month)
            }
            setTimesheet(null)
            setNotFound(false)
            setScreen('history')
            setError(latest ? '' : '登録済みの勤務表がありません。')
          }}
          onImport={() => {
            setImportNotice(null)
            setScreen('import')
            setError('')
          }}
          onAdmin={() => { setScreen('admin-menu'); setError('') }}
        />}
        {screen === 'admin-menu' && <AdminMenu onSelect={openAdminSection} />}
        {screen === 'admin-users' && <AdminPanel currentUsername={session.username} section="users" onBack={() => setScreen('admin-menu')} />}
        {screen === 'admin-excel' && <AdminPanel currentUsername={session.username} section="excel" onBack={() => setScreen('admin-menu')} />}
        {screen === 'admin-calendar' && <AdminPanel currentUsername={session.username} section="calendar" onBack={() => setScreen('admin-menu')} />}
        {screen === 'admin-recovery' && <AdminPanel currentUsername={session.username} section="recovery" onBack={() => setScreen('admin-menu')} />}
        {screen === 'admin-integrations' && <AdminPanel currentUsername={session.username} section="integrations" onBack={() => setScreen('admin-menu')} />}
        {screen === 'import' && <OwnExcelImport
          onCancel={() => setScreen('home')}
          onImported={(result) => {
            setImportNotice(result)
            setYear(result.year)
            setMonth(result.month)
            setTimesheet(result.timesheet)
            setNotFound(false)
            rememberMonth(result.timesheet)
            setScreen('history')
          }}
        />}
        {(screen === 'new' || screen === 'history') && <>
          <div className="month-toolbar">
            {isAdmin && <label className="employee-picker">
              <span>対象社員（管理者のみ）</span>
              <div className="employee-search-box predictive-search">
                <input
                  aria-label="社員を検索"
                  value={employeeSearch}
                  autoComplete="off"
                  aria-autocomplete="list"
                  aria-controls="employee-search-suggestions"
                  onChange={(event) => setEmployeeSearch(event.target.value)}
                  placeholder="氏名・コード・所属"
                  onKeyDown={(event) => { if (event.key === 'Enter') void searchEmployees(employeeSearch) }}
                />
                <button type="button" onClick={() => void searchEmployees(employeeSearch)}>検索</button>
                {employeeSuggestions.length > 0 && <span className="search-suggestions compact-suggestions" id="employee-search-suggestions" role="listbox" aria-label="社員検索候補">
                  {employeeSuggestions.map((account) => (
                    <button type="button" role="option" key={account.username} onClick={() => selectEmployee(account.username)}>
                      <strong>{account.displayName}</strong>
                      <small>{account.employeeCode} · {account.department || '所属未設定'}</small>
                    </button>
                  ))}
                </span>}
              </div>
              <select aria-label="編集する社員を選択" value={targetUsername} onChange={(event) => selectEmployee(event.target.value)}>
                {editableEmployees.map((account) => <option key={account.username} value={account.username}>{account.displayName}（{account.employeeCode}）</option>)}
              </select>
            </label>}
            {screen === 'new' && <button onClick={() => moveMonth(-1)}>◀ 前月</button>}
            <strong>{year}年{String(month).padStart(2, '0')}月</strong>
            {screen === 'new' && <button onClick={() => moveMonth(1)}>翌月 ▶</button>}
            {screen === 'history' && <label className="history-picker">
              <span>過去の勤怠</span>
              <select aria-label="過去の勤怠を選択" value="" disabled={history.length === 0} onChange={(event) => selectHistory(event.target.value)}>
                <option value="">{history.length === 0 ? '登録なし' : '登録済み月を選択'}</option>
                {history.map((item) => (
                  <option key={`${item.year}-${item.month}`} value={`${item.year}-${item.month}`}>
                    {item.year}年{String(item.month).padStart(2, '0')}月
                  </option>
                ))}
              </select>
            </label>}
            {timesheet && <button className="toolbar-danger-button" type="button" onClick={() => setDeleteTarget(timesheet)}>勤務表を削除</button>}
          </div>
          {error && <p className="error-message">{error}</p>}
          {importNotice && screen === 'history' && <div className="import-result" role="status">
            <strong>{importNotice.year}年{String(importNotice.month).padStart(2, '0')}月の勤務表を{importNotice.importedRows}日分取り込みました。</strong>
            <span>Excel記載: {importNotice.sourceEmployeeName || '氏名なし'} / {importNotice.sourceEmployeeCode || 'コードなし'}</span>
            {importNotice.warnings.map((warning) => <p className="warning-message" key={warning}>{warning}</p>)}
            <button type="button" onClick={() => setImportNotice(null)}>通知を閉じる</button>
          </div>}
          {targetUsername !== session.username && <p className="delegated-edit-note">管理者権限で <strong>{targetEmployee?.displayName}</strong> さんの勤務表を参照・編集しています。操作は監査ログに記録されます。</p>}
          {notFound && screen === 'history' && <section className="empty-state"><h2>勤務表がありません</h2><p className="muted">「過去の勤怠」から登録済みの月を選択してください。</p></section>}
          {notFound && screen === 'new' && targetEmployee && <Initializer employee={targetEmployee} year={year} month={month} apiBase={timesheetApiBase} onCreated={(value) => { setTimesheet(value); setNotFound(false); rememberMonth(value) }} />}
          {timesheet && <TimesheetView value={timesheet} apiBase={timesheetApiBase} onChange={setTimesheet} />}
        </>}
      </main>
      {deleteTarget && <DeleteTimesheetDialog
        timesheet={deleteTarget}
        apiBase={timesheetApiBase}
        onClose={() => setDeleteTarget(null)}
        onDeleted={() => {
          setHistory((current) => current.filter((item) =>
            item.year !== deleteTarget.year || item.month !== deleteTarget.month))
          setTimesheet(null)
          setDeleteTarget(null)
          setImportNotice(null)
          setNotFound(false)
          setScreen('home')
        }}
      />}
    </div>
  )
}

export default App
