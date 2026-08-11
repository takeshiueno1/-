# Attendance Input, Role, and Workflow Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 勤務表の入力操作、氏名だけの再発行依頼、3権限の認可、二段階交通費承認を、既存データを保持したままUI・API・DBで一貫させる。

**Architecture:** 既存のReact/Spring Boot構成を維持し、純粋な入力正規化をフロント共通関数へ分離する。DB移行で再発行の未解決依頼、勤務表承認状態、交通費の管理者ソフト削除を追加し、サービス層で所有者・割当・ロールを最終判定する。旧総務精算履歴は保持し、新規フローだけを二次承認完了へ切り替える。

**Tech Stack:** React、TypeScript、Node.js組み込みテスト、Spring Boot、Java 21、Spring Security、JdbcClient、Flyway、PostgreSQL/H2デモ、Maven

## Global Constraints

- 再発行依頼の未認証画面は氏名だけを受け取り、該当件数を公開しない。
- 時刻欄のEnterはフォーム送信せず、確定・検証・勤務時間再計算を行う。
- 入力済み時刻欄へフォーカス直後に数字を入力した場合は既存値を置き換える。
- 操作文言は「下記に業務内容をコピー」「作成」「社員の勤務表を新規作成」「社員の勤務表を参照」を使用する。
- 保存成功通知は「保存しました」を約3秒表示してフェードアウトし、失敗時は表示しない。
- 作成済み月は作成ボタンを無効化し、赤字で「当月は作成済です。」と表示する。APIも409で拒否する。
- 全角数字の半角化は数値・時刻・社員コード系へ適用し、パスワードと自由記述へ適用しない。
- 権限はUSER、MANAGER、ADMINの3区分とし、ROLE_EXPENSE_ACCOUNTINGを廃止する。
- 一次・二次承認者は別人とし、申請者本人の承認を禁止する。
- 管理者は交通費の新規申請を行えず、全社員分の参照・更新・ソフト削除ができる。
- 既存の勤務表、交通費、承認イベント、印影、監査ログは削除しない。
- UIの非表示だけに依存せず、APIとサービス層で認可する。
- 新しい外部依存ライブラリを追加しない。

---

## File Structure

- Create: `frontend/src/numericInput.ts` — 全角数字から半角数字への純粋変換を担当する。
- Create: `frontend/src/numericInput.test.ts` — 数字正規化の境界を検証する。
- Modify: `frontend/src/timeInput.ts` — 時刻確定用の正規化を共通化する。
- Modify: `frontend/src/timeInput.test.ts` — 全角時刻と確定挙動を検証する。
- Modify: `frontend/src/App.tsx` — 再発行、勤務表入力、保存通知、作成済み制御、ロール別メニューを実装する。
- Modify: `frontend/src/App.css` — 3秒ポップアップ、赤字メッセージ、承認枠の表示を追加する。
- Modify: `frontend/src/attendanceEntry.ts` — 業務内容コピーの「下記」範囲を実装する。
- Modify: `frontend/src/attendanceEntry.test.ts` — コピー対象範囲を検証する。
- Create: `frontend/src/timesheetUi.ts` — 時刻キー入力、作成可否、保存通知タイミングの純粋判定を担当する。
- Create: `frontend/src/timesheetUi.test.ts` — 勤務表UI判定をDOM非依存で検証する。
- Modify: `frontend/package.json` — 新しいNode.jsテストファイルをテストスクリプトへ追加する。
- Modify: `frontend/src/TransportExpensePage.tsx` — 管理者モード、承認3枠、総務工程削除、数値変換を実装する。
- Modify: `frontend/src/transportExpense.ts` — 完了状態、ロール別操作判定を純粋関数化する。
- Modify: `frontend/src/transportExpense.test.ts` — 新フローと管理者制御を検証する。
- Create: `backend/src/main/resources/db/migration/V9__input_role_workflow_fixes.sql` — 本番向け互換移行を行う。
- Create: `backend/src/main/resources/db/demo/V3__input_role_workflow_fixes.sql` — H2デモ向け互換移行を行う。
- Modify: `backend/src/main/java/jp/co/query/attendance/auth/CredentialRecoveryController.java` — 氏名だけの受付と未解決依頼を実装する。
- Create: `backend/src/test/java/jp/co/query/attendance/auth/CredentialRecoveryControllerTest.java` — 0件・1件・複数件と情報非開示を検証する。
- Modify: `backend/src/main/java/jp/co/query/attendance/timesheet/Timesheet.java` — 承認状態と承認情報を公開する。
- Create: `backend/src/main/java/jp/co/query/attendance/timesheet/TimesheetStatus.java` — 勤務表状態を型安全に定義する。
- Modify: `backend/src/main/java/jp/co/query/attendance/timesheet/TimesheetRepository.java` — 重複判定、承認状態、競合更新を扱う。
- Modify: `backend/src/main/java/jp/co/query/attendance/timesheet/TimesheetService.java` — 409重複拒否とロール別操作を実装する。
- Modify: `backend/src/main/java/jp/co/query/attendance/timesheet/TimesheetController.java` — 本人の申請APIを追加する。
- Modify: `backend/src/main/java/jp/co/query/attendance/timesheet/AdminTimesheetController.java` — 役職者・管理者の承認APIを追加する。
- Modify: `backend/src/test/java/jp/co/query/attendance/timesheet/TimesheetServiceTest.java` — 重複、所有者、承認割当を検証する。
- Modify: `backend/src/main/java/jp/co/query/attendance/config/SecurityConfig.java` — 3権限のURL認可を明示する。
- Modify: `backend/src/main/java/jp/co/query/attendance/employee/AdminEmployeeController.java` — 総務担当入力を削除する。
- Modify: `backend/src/main/java/jp/co/query/attendance/employee/AdminEmployeeService.java` — 総務権限の付与・解除を削除する。
- Modify: `backend/src/main/java/jp/co/query/attendance/transportationexpense/TransportExpenseStatus.java` — 新規フローの最終状態を整理する。
- Modify: `backend/src/main/java/jp/co/query/attendance/transportationexpense/TransportExpenseController.java` — settle廃止と管理者更新・削除APIを追加する。
- Modify: `backend/src/main/java/jp/co/query/attendance/transportationexpense/TransportExpenseService.java` — 二次承認完了と管理者操作を実装する。
- Modify: `backend/src/main/java/jp/co/query/attendance/transportationexpense/TransportExpenseRepository.java` — ソフト削除と管理者検索を実装する。
- Modify: `backend/src/test/java/jp/co/query/attendance/transportationexpense/TransportExpenseServiceTest.java` — 二段階承認とロール境界を検証する。
- Modify: `backend/src/test/java/jp/co/query/attendance/transportationexpense/TransportExpenseMigrationTest.java` — 旧総務データの互換移行を検証する。
- Modify: `docs/02-requirements-draft.md`, `docs/05-system-design.md`, `docs/06-smartphone-access-and-test.md` — 確定仕様と確認結果を同期する。

### Task 1: 全角数字と時刻入力の共通基盤

**Files:**
- Create: `frontend/src/numericInput.ts`
- Create: `frontend/src/numericInput.test.ts`
- Modify: `frontend/src/timeInput.ts`
- Modify: `frontend/src/timeInput.test.ts`

**Interfaces:**
- Produces: `normalizeFullWidthDigits(value: string): string`
- Produces: `normalizeNumericCode(value: string): string`
- Produces: `commitTimeInput(value: string): { value: string; valid: boolean }`

- [ ] **Step 1: Write failing normalization tests**

```ts
import assert from 'node:assert/strict'
import test from 'node:test'
import { normalizeFullWidthDigits, normalizeNumericCode } from './numericInput.ts'

test('全角数字だけを半角へ変換する', () => {
  assert.equal(normalizeFullWidthDigits('１２３,４５０円'), '123,450円')
  assert.equal(normalizeFullWidthDigits('１２A'), '12A')
  assert.equal(normalizeNumericCode('ＵＥＮＯ００１'), 'ＵＥＮＯ001')
})
```

- [ ] **Step 2: Run RED**

First add `src/numericInput.test.ts` to the existing `package.json` test command, then run: `npm test`

Expected: FAIL because `numericInput.ts` does not exist.

- [ ] **Step 3: Implement the minimal conversion**

```ts
export function normalizeFullWidthDigits(value: string): string {
  return value.replace(/[０-９]/g, (digit) =>
    String.fromCharCode(digit.charCodeAt(0) - 0xFEE0))
}

export function normalizeNumericCode(value: string): string {
  return normalizeFullWidthDigits(value)
}
```

Extend `commitTimeInput` so `９：３０`, `９:３０`, and `0930` all become `09:30`; impossible hours or minutes return `{ value: normalized, valid: false }`.

- [ ] **Step 4: Run GREEN and affected tests**

```powershell
npm test
npm run lint
```

Expected: all selected tests and lint PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- frontend/package.json frontend/src/numericInput.ts frontend/src/numericInput.test.ts frontend/src/timeInput.ts frontend/src/timeInput.test.ts
git commit -m "feat: 数値と時刻入力の正規化を追加"
```

### Task 2: 氏名だけのログイン情報再発行

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__input_role_workflow_fixes.sql`
- Create: `backend/src/main/resources/db/demo/V3__input_role_workflow_fixes.sql`
- Modify: `backend/src/main/java/jp/co/query/attendance/auth/CredentialRecoveryController.java`
- Create: `backend/src/test/java/jp/co/query/attendance/auth/CredentialRecoveryControllerTest.java`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- HTTP: `POST /api/auth/credential-recovery` body `{ "displayName": "上野 豪" }`
- HTTP: `GET /api/admin/credential-recovery` returns `employeeId/username` nullable and `inputDisplayName`, `resolutionRequired`

- [ ] **Step 1: Add migration columns with a failing migration assertion**

Add tests that expect `credential_recovery_requests.employee_id` to be nullable and columns `input_display_name VARCHAR(100) NOT NULL`, `normalized_display_name VARCHAR(100) NOT NULL`. Also expect a non-unique index on pending normalized names.

```java
assertThat(columnNullable("credential_recovery_requests", "employee_id")).isTrue();
assertThat(columnExists("credential_recovery_requests", "input_display_name")).isTrue();
assertThat(columnExists("credential_recovery_requests", "normalized_display_name")).isTrue();
```

- [ ] **Step 2: Run RED**

Run: `.\mvnw.cmd -Dtest=CredentialRecoveryControllerTest test`

Expected: FAIL because the new request contract and columns do not exist.

- [ ] **Step 3: Implement migration and controller behavior**

Migration rules:

```sql
ALTER TABLE credential_recovery_requests ALTER COLUMN employee_id DROP NOT NULL;
ALTER TABLE credential_recovery_requests ADD COLUMN input_display_name VARCHAR(100);
ALTER TABLE credential_recovery_requests ADD COLUMN normalized_display_name VARCHAR(100);
UPDATE credential_recovery_requests r
   SET input_display_name = e.display_name,
       normalized_display_name = LOWER(REPLACE(REPLACE(e.display_name, ' ', ''), '　', ''))
  FROM employees e
 WHERE e.id = r.employee_id;
ALTER TABLE credential_recovery_requests ALTER COLUMN input_display_name SET NOT NULL;
ALTER TABLE credential_recovery_requests ALTER COLUMN normalized_display_name SET NOT NULL;
```

The controller must normalize with `Normalizer.normalize(value.strip(), Normalizer.Form.NFKC)`, remove ASCII/full-width spaces for matching, link only when exactly one enabled employee matches, and always return HTTP 202 with the same message. Zero/multiple matches insert an unresolved row with `employee_id = NULL`.

- [ ] **Step 4: Update UI and run GREEN**

Remove employee-code state, label, input, validation, and Slack text. Keep only `氏名`; request body contains only `displayName`. Assert the public response never includes username, employee code, or match count.

```powershell
.\mvnw.cmd -Dtest=CredentialRecoveryControllerTest test
npm test
```

Expected: backend recovery tests and frontend tests PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- backend/src/main/resources/db/migration/V9__input_role_workflow_fixes.sql backend/src/main/resources/db/demo/V3__input_role_workflow_fixes.sql backend/src/main/java/jp/co/query/attendance/auth/CredentialRecoveryController.java backend/src/test/java/jp/co/query/attendance/auth/CredentialRecoveryControllerTest.java frontend/src/App.tsx
git commit -m "feat: 氏名だけの再発行依頼に変更"
```

### Task 3: 勤務表入力、コピー、保存通知

**Files:**
- Modify: `frontend/src/attendanceEntry.ts`
- Modify: `frontend/src/attendanceEntry.test.ts`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.css`
- Create: `frontend/src/timesheetUi.ts`
- Create: `frontend/src/timesheetUi.test.ts`
- Modify: `frontend/package.json`

**Interfaces:**
- Produces: `copyWorkDetailBelow(entries, sourceDate, workDetail): DailyEntry[]`
- UI: time input `onKeyDown`, `onFocus`, `onBeforeInput`
- UI: success toast `role="status"` with 3000ms visible interval and fade class

- [ ] **Step 1: Write failing copy and keyboard tests**

```ts
import assert from 'node:assert/strict'
import test from 'node:test'

test('copies only to eligible rows below the source date', () => {
  const result = copyWorkDetailBelow(entries, '2026-08-12', '設計作業')
  assert.equal(result.find((v) => v.date === '2026-08-11')?.workDetail, '既存')
  assert.equal(result.find((v) => v.date === '2026-08-13')?.workDetail, '設計作業')
  assert.equal(result.find((v) => v.date === '2026-08-15')?.workDetail, '')
})
```

Add pure UI-decision tests: `commitTimeOnEnter('９３０')` returns `09:30` and `preventSubmit=true`; `nextTimeInput('09:30', '1', true)` returns `1`; `nextTimeInput('09:30', 'ArrowLeft', true)` keeps `09:30`; the toast state is visible through 2999ms and hidden at 3000ms. Put these functions in `timesheetUi.ts` and call them from `App.tsx`.

- [ ] **Step 2: Run RED**

Add `src/timesheetUi.test.ts` to the existing `package.json` test command, then run: `npm test`

Expected: FAIL because below-only copy and keyboard commit are missing.

- [ ] **Step 3: Implement UI behavior**

- Rename the button to `下記に業務内容をコピー`.
- Ask confirmation only when eligible rows already contain a different value; message includes target count.
- On Enter: `preventDefault()`, normalize, validate, update draft, call `event.currentTarget.blur()`.
- On focus: select all and arm a `replaceOnDigit` ref; the first numeric `beforeinput` replaces the value and disarms the flag.
- On successful batch save: set toast visible, start 2600ms fade class, remove at 3000ms. Clear timers on unmount and before a new save.
- Never show toast if any request fails or returns 409.

- [ ] **Step 4: Run GREEN, fake-timer toast test, lint**

```powershell
npm test
npm run lint
npm run build
```

Expected: frontend tests, lint, and build PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- frontend/package.json frontend/src/attendanceEntry.ts frontend/src/attendanceEntry.test.ts frontend/src/timesheetUi.ts frontend/src/timesheetUi.test.ts frontend/src/App.tsx frontend/src/App.css
git commit -m "feat: 勤務表の入力と保存通知を改善"
```

### Task 4: 勤務表重複作成とロール別操作

**Files:**
- Modify: `backend/src/main/resources/db/migration/V9__input_role_workflow_fixes.sql`
- Modify: `backend/src/main/resources/db/demo/V3__input_role_workflow_fixes.sql`
- Modify: `backend/src/main/java/jp/co/query/attendance/timesheet/Timesheet.java`
- Create: `backend/src/main/java/jp/co/query/attendance/timesheet/TimesheetStatus.java`
- Modify: `backend/src/main/java/jp/co/query/attendance/timesheet/TimesheetRepository.java`
- Modify: `backend/src/main/java/jp/co/query/attendance/timesheet/TimesheetService.java`
- Modify: `backend/src/main/java/jp/co/query/attendance/timesheet/TimesheetController.java`
- Modify: `backend/src/main/java/jp/co/query/attendance/timesheet/AdminTimesheetController.java`
- Modify: `backend/src/test/java/jp/co/query/attendance/timesheet/TimesheetServiceTest.java`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- `TimesheetStatus`: `DRAFT`, `SUBMITTED`, `APPROVED`, `RETURNED`
- HTTP: `POST /api/timesheets/{year}/{month}/submit` with `{ version }`
- HTTP: `POST /api/management/employees/{username}/timesheets/{year}/{month}/approve`
- HTTP: `POST /api/management/employees/{username}/timesheets/{year}/{month}/return` with `{ version, reason }`

- [ ] **Step 1: Write failing service tests**

```java
assertThatThrownBy(() -> service.initialize("ueno", 2026, 8, command))
    .isInstanceOf(ResponseStatusException.class)
    .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
    .isEqualTo(HttpStatus.CONFLICT);
```

Add tests: USER cannot access another employee; MANAGER can view/approve only employees returned by the existing management scope query; MANAGER cannot edit another employee entry; ADMIN can create/view/update/delete all; stale approval version returns 409.

- [ ] **Step 2: Run RED**

Run: `.\mvnw.cmd -Dtest=TimesheetServiceTest test`

Expected: FAIL on duplicate initialization and missing status workflow.

- [ ] **Step 3: Implement status and authorization**

Add migration columns `status`, `submitted_at`, `approved_at`, `approved_by`, `return_reason`; default existing rows to `DRAFT`. Remove overwrite behavior from normal create: if an active row exists, throw 409. Keep Excel import as its explicit import path.

Service rules:

```java
if (existsActive(username, year, month)) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, "当月は作成済です。");
}
```

MANAGER management methods may read and approve assigned employees but must receive 403 from entry update/delete/create methods. ADMIN retains all operations. Approval and return use optimistic version updates and audit events.

- [ ] **Step 4: Update UI and run GREEN**

When the selected month GET succeeds, disable create and show `当月は作成済です。`; when it returns 404, enable. Rename create labels exactly as specified. Do not infer availability only from local history; recheck the API when employee/month changes.

```powershell
.\mvnw.cmd -Dtest=TimesheetServiceTest test
npm test
```

Expected: focused backend and frontend tests PASS.

- [ ] **Step 5: Commit**

```powershell
git add -- backend/src/main/resources/db/migration/V9__input_role_workflow_fixes.sql backend/src/main/resources/db/demo/V3__input_role_workflow_fixes.sql backend/src/main/java/jp/co/query/attendance/timesheet backend/src/test/java/jp/co/query/attendance/timesheet/TimesheetServiceTest.java frontend/src/App.tsx
git commit -m "feat: 勤務表の重複作成と承認権限を整理"
```

### Task 5: 3権限への統一と総務権限廃止

**Files:**
- Modify: `backend/src/main/resources/db/migration/V9__input_role_workflow_fixes.sql`
- Modify: `backend/src/main/resources/db/demo/V3__input_role_workflow_fixes.sql`
- Modify: `backend/src/main/java/jp/co/query/attendance/config/SecurityConfig.java`
- Modify: `backend/src/main/java/jp/co/query/attendance/employee/AdminEmployeeController.java`
- Modify: `backend/src/main/java/jp/co/query/attendance/employee/AdminEmployeeService.java`
- Modify: `backend/src/test/java/jp/co/query/attendance/auth/TestUserInitializerTest.java`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Roles: `ROLE_USER`, `ROLE_MANAGER`, `ROLE_ADMIN`
- Removes: `ROLE_EXPENSE_ACCOUNTING`, `expenseAccounting`, `/api/admin/employees/{username}/expense-accounting`

- [ ] **Step 1: Write failing role-matrix tests**

Assert USER receives 403 for management/admin endpoints, MANAGER receives access only to `/api/management/**` and approval actions, ADMIN receives admin/management access, and an old `ROLE_EXPENSE_ACCOUNTING` row grants no endpoint.

```java
mockMvc.perform(put("/api/admin/employees/ueno/expense-accounting")
        .with(user("admin").roles("ADMIN")))
    .andExpect(status().isNotFound());
```

- [ ] **Step 2: Run RED**

Run: `.\mvnw.cmd -Dtest=TestUserInitializerTest test`

Expected: FAIL while the fourth authority and API still exist.

- [ ] **Step 3: Remove the fourth authority across layers**

Migration deletes only `authorities.authority = 'ROLE_EXPENSE_ACCOUNTING'`. Remove controller request fields, service projections, create/update logic, UI checkbox/button/text, and demo assignment. Security rules remain explicit: admin endpoints ADMIN, management endpoints MANAGER or ADMIN, own endpoints authenticated USER.

- [ ] **Step 4: Run GREEN and security regression**

```powershell
.\mvnw.cmd -Dtest=TestUserInitializerTest,TimesheetServiceTest test
npm test
```

Expected: role tests PASS and no source reference to `ROLE_EXPENSE_ACCOUNTING` remains outside historical migrations/docs.

- [ ] **Step 5: Commit**

```powershell
git add -- backend/src/main/resources/db backend/src/main/java/jp/co/query/attendance/config/SecurityConfig.java backend/src/main/java/jp/co/query/attendance/employee backend/src/test/java/jp/co/query/attendance/auth/TestUserInitializerTest.java frontend/src/App.tsx
git commit -m "feat: 権限を一般役職者管理者へ統一"
```

### Task 6: 交通費の二次承認完了、管理者操作、承認3枠

**Files:**
- Modify: `backend/src/main/resources/db/migration/V9__input_role_workflow_fixes.sql`
- Modify: `backend/src/main/resources/db/demo/V3__input_role_workflow_fixes.sql`
- Modify: `backend/src/main/java/jp/co/query/attendance/transportationexpense/TransportExpenseStatus.java`
- Modify: `backend/src/main/java/jp/co/query/attendance/transportationexpense/TransportExpenseController.java`
- Modify: `backend/src/main/java/jp/co/query/attendance/transportationexpense/TransportExpenseService.java`
- Modify: `backend/src/main/java/jp/co/query/attendance/transportationexpense/TransportExpenseRepository.java`
- Modify: `backend/src/test/java/jp/co/query/attendance/transportationexpense/TransportExpenseServiceTest.java`
- Modify: `backend/src/test/java/jp/co/query/attendance/transportationexpense/TransportExpenseMigrationTest.java`
- Modify: `frontend/src/TransportExpensePage.tsx`
- Modify: `frontend/src/TransportExpensePage.css`
- Modify: `frontend/src/transportExpense.ts`
- Modify: `frontend/src/transportExpense.test.ts`

**Interfaces:**
- Second approval transitions `PENDING_SECOND_APPROVAL -> SETTLED`
- Removes: `POST /api/transport-expenses/{claimId}/settle`
- Adds: `PUT /api/transport-expenses/admin/{claimId}` and `DELETE /api/transport-expenses/admin/{claimId}`
- Adds DB: `deleted_at TIMESTAMPTZ`, `deleted_by VARCHAR(100)`

- [ ] **Step 1: Write failing workflow and migration tests**

```java
verify(repository).transition(
    eq(100L), eq(4), eq(PENDING_SECOND_APPROVAL), eq(SETTLED),
    anyString(), anyString(), any(), any());
verify(auditLogs).record(
    "director", "TRANSPORT_EXPENSE_SECOND_APPROVED", "TRANSPORT_EXPENSE", "100", "");
```

Add tests: applicant cannot approve; same first/second ID rejected; assigned manager/admin can approve; ADMIN cannot create a new claim; ADMIN can update any claim with version check; delete sets `deleted_at/deleted_by` and list/get excludes it; old `PENDING_ACCOUNTING` rows migrate to `SETTLED`; old events remain.

- [ ] **Step 2: Run RED**

Run: `.\mvnw.cmd -Dtest=TransportExpenseServiceTest,TransportExpenseMigrationTest test`

Expected: FAIL because second approval still enters accounting and admin delete is missing.

- [ ] **Step 3: Implement backend workflow and migration**

- Transition second approval directly to `SETTLED`, set `settled_at` to current time, and retain the `SECOND_APPROVED` approval event.
- Remove settle controller/service path and accounting queue query.
- Convert existing `PENDING_ACCOUNTING` to `SETTLED` without deleting events.
- Add soft-delete columns and filter every normal repository query with `deleted_at IS NULL`.
- Admin update/delete require `ROLE_ADMIN`, version match, audit log, and 409 on stale versions.
- Create-month service rejects actors whose current user has `ROLE_ADMIN` with 403.

- [ ] **Step 4: Implement frontend workflow**

- Apply `normalizeFullWidthDigits` to every amount input before validation.
- ADMIN opens status/search view; do not render new-application tab or create button.
- Remove accounting queue, settle button, and accounting labels.
- Render three bordered cards labelled `申請者`, `一次承認`, `二次承認`, each with name/time/status/stamp.
- Keep legacy accounting events in the expandable raw history only.
- ADMIN sees update/delete controls with confirmation; USER/MANAGER do not.

- [ ] **Step 5: Run GREEN**

```powershell
.\mvnw.cmd -Dtest=TransportExpenseServiceTest,TransportExpenseMigrationTest test
npm test
npm run lint
npm run build
```

Expected: transport tests, lint, and build PASS.

- [ ] **Step 6: Commit**

```powershell
git add -- backend/src/main/resources/db backend/src/main/java/jp/co/query/attendance/transportationexpense backend/src/test/java/jp/co/query/attendance/transportationexpense frontend/src/TransportExpensePage.tsx frontend/src/TransportExpensePage.css frontend/src/transportExpense.ts frontend/src/transportExpense.test.ts
git commit -m "feat: 交通費承認と管理者操作を整理"
```

### Task 7: 文書同期、全体検証、デモ更新

**Files:**
- Modify: `docs/02-requirements-draft.md`
- Modify: `docs/05-system-design.md`
- Modify: `docs/06-smartphone-access-and-test.md`
- Modify: `README.md`
- Modify: `scripts/build-windows-demo.ps1`

**Interfaces:**
- Verification users: USER `ueno`, one non-conflicting MANAGER, ADMIN `admin`
- Runtime: `http://127.0.0.1:4280/`

- [ ] **Step 1: Update documents from the approved design**

Record the exact role matrix, name-only recovery, time-entry behavior, duplicate-month 409, second-approval completion, removed accounting role, admin transport restriction, migration/rollback steps, and manual confirmation cases. Remove current-state claims that still describe accounting settlement as active.

- [ ] **Step 2: Run static full gates**

```powershell
Push-Location backend
.\mvnw.cmd test
.\mvnw.cmd -DskipTests package
Pop-Location
Push-Location frontend
npm test
npm run lint
npm run build
Pop-Location
git diff --check
```

Expected: all commands exit 0. Record exact test counts from output.

- [ ] **Step 3: Build the Windows demo without deleting user data**

Stop only the confirmed demo Java process. Back up `dist/attendance-demo-windows/data/attendance-demo.mv.db` before replacing the executable. Run the existing build script in its documented mode, restore or migrate the preserved data, then start the package hidden and wait for `/actuator/health` to return `UP`.

- [ ] **Step 4: Verify DB migration and accounts before workflow tests**

Query the demo DB/API to confirm `ueno`, a distinct manager account, and `admin` exist and do not share the same employee. Confirm the selected months/claims are unused before mutation. Verify old recovery rows, transport events, and settled claims remain; `ROLE_EXPENSE_ACCOUNTING` count is zero; old pending-accounting claims are settled.

- [ ] **Step 5: Run real role/API/UI checks**

- USER: name-only recovery generic response; own timesheet create/edit/view; Enter commit; replacement typing; copy-below; save toast; duplicate-month disabled/409; own transport create.
- MANAGER: assigned timesheet/transport view and approve; forbidden employee edit; first/second approvers distinct.
- ADMIN: all employee timesheet create/view/update/delete; no transport create; all transport view/update/soft-delete; user/recovery/calendar management.
- Browser: toast remains about 3 seconds, red duplicate message appears, amount full-width digits become half-width, approval cards are visually separate at desktop and 390px.

- [ ] **Step 6: Update the packaged login/readme artifacts**

Ensure the package login-information file includes the verified USER/MANAGER/ADMIN test accounts without adding production secrets to Git. Record exact manual checks and remaining user-only visual decisions in docs.

- [ ] **Step 7: Final commit**

```powershell
git add -- README.md docs/02-requirements-draft.md docs/05-system-design.md docs/06-smartphone-access-and-test.md scripts/build-windows-demo.ps1
git commit -m "docs: 権限と入力改善の検証結果を反映"
```
