# Jellyfish Ocean

Android 動態桌布：藍紫色深海中，半透明、發光的水母自然漂浮。

- 水母傘體脈動推進、觸手隨水流擺動（純 Canvas 繪製，無外部相依套件）
- 觸控會產生漣漪並驚動附近的水母
- 手機傾斜時水母順勢漂移（加速度感應器）
- 設定：水母數量、移動速度、亮度、省電模式
- 目標裝置：Xiaomi 14T Pro（minSdk 26 以上皆可）

## 取得 APK（只用手機）

1. 把這個專案推到 GitHub 的 `main` 分支，GitHub Actions 會自動建置。
2. 到倉庫的 **Releases** 頁面，下載最新的 `JellyfishOcean-debug.apk`。
3. 安裝時允許瀏覽器「安裝未知應用程式」。
4. 開啟 Jellyfish Ocean → 按「設為動態桌布」→ 套用。

也可以在 **Actions** 分頁開啟最新一次執行，從 Artifacts 下載。

## 專案結構

| 檔案 | 說明 |
| --- | --- |
| `JellyfishWallpaperService.kt` | WallpaperService、繪製迴圈、觸控、傾斜感應 |
| `Jelly.kt` | 單隻水母的物理與繪製 |
| `Ocean.kt` | 背景漸層、光束、浮游微粒 |
| `SettingsActivity.kt` | 設定頁與「設為動態桌布」按鈕 |
| `.github/workflows/build.yml` | 自動建置並發佈 APK |

## 本機建置

需要 JDK 17 與 Android SDK，再使用 Gradle 8.9：

```
gradle assembleDebug
```
