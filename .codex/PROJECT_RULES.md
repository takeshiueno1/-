# 勤怠管理システム固有ルール

## プロジェクト概要と技術構成

- Excel勤務表の入力、15分単位計算、案件別集計、帳票、交通費申請・二段階承認を再現する社内向けWebシステムである。
- `backend/` はJava 21、Spring Boot 4.1、Spring Security、Spring JDBC、Flyway、PostgreSQLを使用し、WARとしてTomcat 11へ配置する。
- `frontend/` はReact 19、TypeScript 6、Vite 8を使用する。
- `compose.yaml` の開発・結合環境はPostgreSQL 17、backend、frontendで構成し、PCでは `http://localhost:4173` を使用する。
- 本番はDockerではなく社内サーバーのJava 21、Tomcat 11、PostgreSQL 17を使用する。デプロイ、本番設定、VPN/TLS、課金を伴う外部連携はマスターの事前承認境界に従う。

## 優先して参照する実在資料

1. `README.md` と変更対象の実コード・テスト・Flyway migration
2. `docs/02-requirements-draft.md` の現行要件
3. `docs/03-excel-fidelity-acceptance.md` のExcel照合基準
4. `docs/05-system-design.md` の画面、API、DB、セキュリティ、外部連携設計
5. `docs/04-implementation-status.md` の実装済み範囲と残る制約
6. `docs/deliverables/` の承認済み成果物
7. `docs/01-current-workflow-analysis.md` と受領Excelの解析結果

- Excelから要件を導出する場合は、実在するブック、シート、入力規則、数式、保護、VBAを先に読み取り専用で確認する。暗号化された `.xlsm` はZIPとして扱わず、マクロ自動実行を無効にしたExcel COMを使用する。
- 要件、README、成果物、コードに差異がある場合は、推測で一方へ合わせず差異と影響を示す。

## 業務・セキュリティ上の不変条件

- 勤務表と交通費の業務ルールは画面だけでなくAPI/serviceでも検証する。
- 全日休暇は始業・終業・休憩・業務内容をクリアして入力不可にし、AM半休・PM半休は実入力を保持する。
- 時刻はUIの分精度とAPI/DB値の差を考慮し、`HH:mm:00` だけを `HH:mm` として受け入れ、非0秒を黙って丸めない。
- 一般、役職者、管理者の本人・割当・代理操作・承認段階をAPIで再検証し、画面非表示だけを認可にしない。
- 勤務表の同一社員・年月、楽観ロック、二重申請・二重承認、交通費の本人承認と同一人物の二段階承認を防ぐ。
- 削除は仕様どおり論理削除と監査証跡を維持し、物理削除や破壊的migrationへ変更しない。
- 電子印画像または表示上の印影だけを本人性の証拠にしない。再認証、操作者、日時、内容ハッシュ、履歴を保持する。
- `.env`、実パスワード、Outlook/Slack認証情報、実在社員の個人情報をGit、ログ、デモデータ、外部AIへ保存・送信しない。
- スマートフォン変更は実際の390px表示を確認し、44px以上の操作領域、エラー文言・色・表示時間、横スクロール、時刻入力を検証する。

## 固有の検証コマンド

変更箇所に応じて狭いテストから実行し、完了前に必要な範囲をまとめて確認する。

```powershell
# Backend
Set-Location backend
.\mvnw.cmd test

# Frontend
Set-Location ..\frontend
npm install
npm test
npm run build
npm run lint

# Compose定義
Set-Location ..
docker compose config
```

- 実動作確認が必要な場合は、秘密値をGitへ保存せず環境変数へ設定して `docker compose up -d --build` を実行し、DB/backend health、`http://localhost:4173`、対象のログイン/API/UI経路を確認する。
- 本番WAR生成コマンドは `README.md` の「本番用WARの生成と配置」を正とするが、実デプロイは事前承認なしに行わない。
