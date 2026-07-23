# 勤怠管理システム

Excel勤務表の入力、計算、案件別集計、帳票表示を忠実に再現する社内向けWebシステムです。初版は正社員・8時間勤務の提供済みExcelを基準にしています。

## 構成

- Frontend: React 19 + TypeScript + Vite
- Backend: Java 21 + Spring Boot 4.1
- Database: PostgreSQL 17
- Local runtime: Docker Compose

すべて無償利用可能なソフトウェアで構成しています。本番では社内サーバー上に配置し、外出先からはVPN経由でアクセスする前提です。

## 必要環境

- Docker EngineまたはDocker Desktop互換環境
- Docker Compose

個別開発する場合はJava 21とNode.js 20.19以降も使用します。

## ローカル起動

PowerShellで推測されにくい値を環境変数へ設定して起動します。

```powershell
$env:DB_PASSWORD='<32文字以上のランダムな値>'
$env:APP_INITIAL_ADMIN_USERNAME='admin'
$env:APP_INITIAL_ADMIN_PASSWORD='<16文字以上のランダムな値>'
docker compose up -d --build
```

起動後、PCでは <http://localhost:4173> を開きます。初回起動時だけ管理者アカウントが作成されます。パスワードはGit管理対象へ保存しないでください。

同じWi-Fiに接続したスマートフォンから確認する場合は、Windows PCのIPv4アドレスを使って `http://<PCのIPv4アドレス>:4173` を開きます。例えばPCのアドレスが `192.168.11.14` なら、スマートフォンでは <http://192.168.11.14:4173> を開きます。

- PCとスマートフォンを同じWi-Fiへ接続してください。
- Windows Defender ファイアウォールの確認が表示された場合は「プライベート ネットワーク」だけを許可してください。
- 公衆Wi-Fiやインターネットへ直接公開しないでください。
- Wi-Fiルーターの「端末間通信禁止」や「プライバシーセパレーター」が有効な環境では接続できません。
- PCのIPv4アドレスは `ipconfig` または `Get-NetIPAddress -AddressFamily IPv4` で確認できます。

管理者は画面右上の「管理者メニュー」から作業を選択し、「利用者管理」で従業員アカウントを作成できます。社員検索では氏名・社員コード・所属・ユーザー名の入力中に候補を表示します。各利用者はログイン後、「パスワード変更」から本人のパスワードを変更できます。

新規利用者と管理者による再設定では、安全な仮パスワードを画面で自動生成できます。仮パスワードでログインした利用者は、勤怠情報へ進む前に本人用パスワードへの変更が必須です。定期的な強制変更は行わず、漏えいが疑われる場合や管理者が再発行した場合に変更します。

管理者は勤務表画面の「対象社員」から社員を切り替え、他社員の勤怠を参照・更新できます。代理操作は監査ログへ記録されます。「利用者管理」の会社カレンダーPDF取込では、日付と休日名を文字として抽出できるPDFから会社休日を登録できます。登録した休日は新規作成または確認付き再初期化を行う勤務表へ反映されます。

ログイン後は「新規で勤務表を作成」「過去分を参照・編集」「過去のExcelを取り込む」から作業を選択します。一般利用者は自分のExcel勤務表を取り込めます。管理者は他社員を取込先に指定できます。

ログイン画面ではユーザーIDとパスワードの未入力・形式を検証し、日本語エラーを表示します。ログイン情報を忘れた場合は社員コードと氏名から管理者へ再発行依頼を送れます。アカウントの存在有無やユーザーIDは未認証画面へ表示しません。

勤務表画面の「勤務表を削除」では、確認欄へ `削除する` と入力し、現在ログイン中のユーザー本人のパスワードで再認証します。管理者が他社員の勤務表を削除する場合も、対象社員ではなく操作中の管理者本人のパスワードが必要です。削除は復旧可能な論理削除で、監査・復旧用データはDBに保持します。

画面はスマートフォン幅にも対応しています。勤務表はExcelとの列構成を維持するため横スクロールで操作し、それ以外のメニューや管理画面は画面幅に合わせて1列表示になります。

## Outlook・Slack連携

勤務表画面の「Outlook・Slackで共有」から、本人の勤務表について連絡できます。管理者が代理表示している他社員の勤務表では誤送信防止のため共有欄を表示しません。

- Outlook: Microsoft Graphのアプリケーション権限 `Mail.Send` で、指定した送信元メールボックスから月次集計と確認リンクを送ります。
- Slack: Incoming Webhookで、設定時に選択した社内チャンネルへ勤務表の連絡を投稿します。
- メールアドレスやSlackのメッセージ本文はDBへ保存しません。監査ログには送信手段、勤務表ID、対象年月だけを記録します。
- Microsoft 365とSlackの既存契約・ワークスペースは別途必要です。このシステム側で追加の有料ライブラリは使用しません。

社内サーバーの環境変数で設定します。秘密情報は `.env` やGitへ登録せず、サーバーのシークレット管理機能またはアクセス制限した環境変数として設定してください。

```powershell
$env:APP_PUBLIC_BASE_URL='https://社内で利用する勤怠管理システムのURL'

$env:OUTLOOK_INTEGRATION_ENABLED='true'
$env:OUTLOOK_TENANT_ID='<Microsoft EntraテナントID>'
$env:OUTLOOK_CLIENT_ID='<アプリケーションID>'
$env:OUTLOOK_CLIENT_SECRET='<クライアントシークレット>'
$env:OUTLOOK_SENDER='attendance@example.co.jp'

$env:SLACK_INTEGRATION_ENABLED='true'
$env:SLACK_WEBHOOK_URL='<Slack Incoming Webhook URL>'
docker compose up -d --build
```

Microsoft Graphの `Mail.Send` アプリケーション権限は管理者同意が必要です。送信可能範囲は専用メールボックスへ制限してください。Slack Webhook URLは投稿権限を持つ秘密情報として扱ってください。設定値は「管理者メニュー」→「Outlook・Slack連携」で確認できますが、秘密情報そのものは画面へ表示しません。

## 過去Excel勤務表の取込

ホーム画面または管理者メニューから、`.xlsx` または `.xlsm`、必要な場合はExcelパスワードを指定します。提供済みの「勤務表」シートから対象年月、勤務時間、休憩、休暇、業務内容、システム番号、WG参加可否を読み取り、DBへ登録します。

- ファイル本体とパスワードは保存しません。
- ファイル名、SHA-256、取込先、対象年月、取込件数、操作者を監査目的で保存します。
- Excelの氏名・コードと取込先社員が異なる場合は警告します。
- 同じ月の勤務表がある場合は、確認なしに上書きしません。

## 50人規模のローカル検証データ

検証データは通常起動では作成されません。ローカル検証時だけ、次の環境変数を追加して起動します。

```powershell
$env:APP_DEMO_SEED_ENABLED='true'
$env:APP_DEMO_SEED_PASSWORD='<12文字以上の検証用パスワード>'
$env:APP_DEMO_SEED_COUNT='50'
$env:APP_DEMO_SEED_MONTHS='6'
docker compose up -d --build
```

`demo001`～`demo050` と、各社員の直近6か月分の勤務表が冪等に作成されます。本番環境では `APP_DEMO_SEED_ENABLED=false` のまま使用してください。

上野 豪のローカル確認用アカウント `test` を新規DBにも作る場合は、次の値も設定します。作成後は初回パスワード変更が必須です。

```powershell
$env:APP_TEST_USER_ENABLED='true'
$env:APP_TEST_USER_PASSWORD='<12文字以上の検証用仮パスワード>'
```

## 停止

```powershell
docker compose down
```

データベースを含めて削除する `docker compose down -v` は通常運用では実行しないでください。

## 開発時の確認

Backend:

```powershell
cd backend
.\mvnw.cmd test
```

Frontend:

```powershell
cd frontend
npm install
npm run build
npm run lint
```

## セキュリティ上の前提

- 本番サーバーをインターネットへ直接公開しない。
- VPNとTLSを使用する。
- 本番では `SESSION_COOKIE_SECURE=true` を設定する。
- 初期管理者パスワードとDBパスワードは十分に長いランダム値にする。
- 削除用の共通固定パスワードは使用せず、操作中ユーザーのログインパスワードで再認証する。
- 本番ではOS、コンテナ、データベースの更新と日次バックアップを運用する。
- `.env`、バックアップ、ログをGitへ登録しない。
- 本番では検証データ投入を有効にしない。
- ID・パスワード入力を省略するパスキーまたは社内SSOは、本番のホスト名、TLS、社内ID基盤を確定してから導入する。端末指紋や共有端末の記憶だけでは本人識別を行わない。

## 設計資料

- `docs/01-current-workflow-analysis.md`: Excelの業務・マクロ分析
- `docs/02-requirements-draft.md`: 現時点の確定要件と未確認事項
- `docs/03-excel-fidelity-acceptance.md`: Excelとの照合合格基準
- `docs/04-implementation-status.md`: 実装済み機能、Excel照合結果、残る制約
