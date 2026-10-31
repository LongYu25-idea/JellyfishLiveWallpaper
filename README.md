# Jellyfish Ocean

Android 懸浮水母：半透明、發光的水母浮在所有 App 上方，不擋住點擊與操作。

- 水母傘體脈動推進、觸手隨水流擺動（純 Canvas 繪製，無外部相依套件）
- 透明背景，點擊會穿透到下面的 App
- 手機傾斜時水母順勢漂移（加速度感應器）
- 設定：水母數量、移動速度、透明度、省電模式
- 目標裝置：Xiaomi 14T Pro（minSdk 26 以上皆可）

## 使用方式

1. 到倉庫的 **Releases** 頁面，下載最新的 `JellyfishOcean-debug.apk` 並安裝。
2. 開啟 Jellyfish Ocean，按「啟動水母」。第一次會要求「顯示在其他應用程式上層」權限，允許後回來再按一次。
3. 通知列有「停止」按鈕，也可以回 App 按「停止水母」。

小米 / HyperOS 若水母一段時間後消失，請到系統設定把本 App 的「自啟動」打開，電池設定改為「無限制」。

## 專案結構

| 檔案 | 說明 |
| --- | --- |
| `OverlayService.kt` | 前景服務，建立全螢幕、不可觸控的透明懸浮視窗 |
| `OverlayView.kt` | 動畫迴圈、傾斜感應、設定即時套用 |
| `Jelly.kt` | 單隻水母的物理與繪製 |
| `SettingsActivity.kt` | 設定頁與啟動 / 停止按鈕 |
| `.github/workflows/build.yml` | 自動建置並發佈 APK |
