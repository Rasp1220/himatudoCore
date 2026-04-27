# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

Paper 1.21.1 向けの Spigot プラグイン2本を含むリポジトリ。どちらも独立した Maven プロジェクト。

| プラグイン | パッケージ | 説明 |
|---|---|---|
| `HimatsudoCore` | `com.himatsudo.core` | サーバー基盤機能（モジュール群） |
| `HimatsudoNpc` | `com.himatsudo.npc` | Citizens NPC 連携（ショップ・サーバー転送）|

2本は実行時に同一サーバーへロードされるが、コード上の依存関係はない。

---

## ビルドコマンド

```bash
# HimatsudoCore
mvn -B package --no-transfer-progress

# HimatsudoNpc
mvn -B package --no-transfer-progress -f himatsudo-npc/pom.xml
```

Java 21 必須。テストは存在しない。CI は `.github/workflows/build.yml` で両プロジェクトをビルドし、それぞれの JAR をアーティファクトとしてアップロードする。

---

## HimatsudoCore アーキテクチャ

### モジュールパターン

`HimatsudoCore.java` が全モジュールのライフサイクルを管理する。各モジュールは `loadModule()` の try-catch で独立ロードされ、1つが例外を出しても他のモジュールは継続動作する。

```
HimatsudoCore (JavaPlugin)
 └─ loadModules()
     ├─ DiscordModule
     ├─ AnnounceModule
     ├─ BoardModule       ← AfkModule の前にロードされる必要あり
     ├─ AfkModule         ← BoardModule・ChatModule に依存
     ├─ ChatModule        ← TabModule・ProfileModule の前にロードされる必要あり
     ├─ TabModule         ← ChatModule + AfkModule 両方に依存
     └─ ...（他8モジュール）
```

**ロード順序は重要**。`loadModules()` 内のコメントを参照すること。

### 新モジュールを追加する場合

1. モジュールクラスを作り `shutdown()` メソッドを持たせる
2. `HimatsudoCore` にフィールド追加 → `loadModules()` → `unloadModules()` → getter を追加
3. コマンドから触る場合は `MainCommand.java` にサブコマンドを追加

### `/hc` コマンド

`MainCommand.java` が単一クラスで全サブコマンドを dispatch する。管理者専用コマンドは `sender.hasPermission("himatsudo.admin")` で分岐し、非管理者の `/hc help` からは隠れる。`player.isOp()` チェックも admin 権限の判定に含まれる（LuckPerms 等と共存するため）。

### 設定

`config.yml` の各セクションが各モジュールに対応する。全モジュールに `enabled: true/false` トグルがある。チャットランク定義（`chat.ranks`）はタブリスト（TabModule）でも共有される。

---

## HimatsudoNpc アーキテクチャ

### 拡張ポイント（重要）

2つのインタフェースが拡張の要。

**`NpcAction`** — NPC を右クリックしたときの挙動
```
NpcAction
 ├─ ShopAction          (shop-id を保持し ShopMenu を開く)
 └─ ServerTransferAction(BungeeCord メッセージで Velocity へ転送)
```
新しいアクションタイプは `NpcActionFactory.register()` で登録する（`HimatsudoNpc.buildActionFactory()` 参照）。

**`Reward`** — ショップ購入時の報酬
```
Reward
 ├─ TitleReward   (TitleManager.grantTitle を呼ぶ)
 └─ CommandReward (コンソールコマンドを実行、{player} をプレイヤー名に置換)
```
`ShopRegistry.parseReward()` で type 文字列からインスタンスを生成する。新しい報酬タイプを追加した場合はここの switch に追加する。

### ショップの追加・変更

ショップ定義は `shops.yml`（YAML）で管理されており、コード変更不要。

```yaml
my-new-shop:
  display-name: "&e新しいショップ"
  items:
    - id: item_foo
      material: DIAMOND
      display-name: "&bダイヤ"
      description:
        - "&7説明文"
      cost: 500
      reward:
        type: command          # または title
        command: "some cmd {player}"
        display-name: "ダイヤ報酬"
```

`/hnpc reload` で反映（再起動不要）。

### NPC との紐づけ

`npcs.yml` で Citizens NPC ID とアクションを対応付ける。

```yaml
assignments:
  1:                       # Citizens /npc list で確認する NPC ID
    type: shop
    shop-id: pet-shop
  2:
    type: server_transfer
    server: bedwars
```

`/hnpc assign <id> shop <shop-id>` コマンドでもインゲームから設定可能（`npcs.yml` に自動保存される）。

### データ永続化

| ファイル | 管理クラス | 内容 |
|---|---|---|
| `npcs.yml` | `NpcManager` | NPC ID → アクション |
| `shops.yml` | `ShopRegistry` | ショップ・商品定義 |
| `currency.yml` | `CurrencyManager` | プレイヤー UUID → ポイント残高 |
| `titles.yml` | `TitleManager` | プレイヤー UUID → 所持称号リスト・有効称号 |

すべて Bukkit `YamlConfiguration` で読み書きする。

---

## よくある実装上の注意

**`runTaskLater` の曖昧参照エラー**
`Player.closeInventory()` は2つのオーバーロードを持つため、メソッド参照 `player::closeInventory` がコンパイルエラーになる。必ずラムダ形式を使うこと。

```java
// NG
Bukkit.getScheduler().runTaskLater(plugin, player::closeInventory, 1L);
// OK
Bukkit.getScheduler().runTaskLater(plugin, () -> player.closeInventory(), 1L);
```

**`onDisable()` 内での非同期処理**
`onDisable()` 時は Bukkit スケジューラが使用不可。Discord サーバー停止通知など、停止時に行うネットワーク処理は同期（ブロッキング）で実行すること。

**Adventure API のデコレーション**
`Component.decoration(TextDecoration, boolean)` は builder を返すため `Component` にキャストできない。`TextDecoration.State.FALSE` を使うこと。

```java
// NG: Component にキャストできない
component.decoration(TextDecoration.ITALIC, false)
// OK
component.decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
```
