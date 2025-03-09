# ClaimVisualizer

## 簡介
ClaimVisualizer 是一個 Minecraft 伺服器插件，使用粒子效果視覺化顯示 GriefDefender 插件所建立的領地邊界。透過直觀的視覺效果，玩家可以清楚地看到領地的範圍和界限，避免誤闖他人領地或領地糾紛。

## 特色功能
- 使用不同顏色的粒子標記不同類型的領地 (基本、管理員、子區域、城鎮)
- 支援四種顯示模式：僅角落 (CORNERS)、僅輪廓 (OUTLINE)、完整邊界 (FULL)、牆面 (WALL)
- 完整的 3D 領地視覺化，包含頂部、底部、側面和玩家所在高度的水平線
- 每位玩家可以個別開啟或關閉視覺化效果並選擇顯示模式
- 高度可自訂化的設定，包括粒子類型、顏色、密度和更新頻率
- 效能優化：非同步處理、領地快取機制、智慧渲染距離限制、分批顯示粒子
- 智慧型顯示系統：只顯示玩家附近的領地部分，降低伺服器負擔

## 系統需求
- Minecraft 伺服器版本：Paper 1.21+
- 相依套件：GriefDefender

## 安裝方法
1. 下載最新版本的 ClaimVisualizer.jar 檔案
2. 將檔案放入伺服器的 `plugins` 資料夾中
3. 確保已安裝 GriefDefender 插件
4. 重新啟動伺服器或使用插件管理器載入插件
5. 完成！插件將自動建立預設配置檔案

## 指令列表
- `/claimvisual` 或 `/cv` 或 `/claimviz` - 切換領地視覺化效果的開關狀態
- `/claimvisual on` - 啟用領地視覺化效果
- `/claimvisual off` - 停用領地視覺化效果
- `/claimvisual mode <模式>` - 設定粒子顯示模式 (CORNERS, OUTLINE, FULL, WALL)
- `/claimvisual reload` - 重新載入插件配置
- `/claimvisual help` - 顯示幫助訊息

## 權限節點
- `claimvisualizer.use` - 允許使用基本視覺化功能 (預設授予所有玩家)
- `claimvisualizer.reload` - 允許重新載入插件配置 (預設僅授予管理員)
- `claimvisualizer.autoenable` - 登入伺服器時自動啟用領地視覺化效果 (預設不授予任何人)

## 配置檔案
插件提供了高度自訂的配置選項。配置檔案位於 `plugins/ClaimVisualizer/config.yml`。

### 主要配置區塊：

#### 粒子設定
```yaml
particles:
  # 更新頻率 (單位：刻)
  update-interval: 10
  # 渲染距離 (單位：方塊)
  render-distance: 20
  # 粒子間隔 (單位：方塊)
  spacing: 1.0
  # 粒子顯示高度 (相對於地面)
  height: 1.0
  # 粒子顯示間隔 (單位：刻) - 每隔多少刻顯示一批粒子
  display-interval: 1
```

#### 領地類型粒子設定
每種領地類型 (admin, basic, subdivision, town) 可以自訂不同部分的粒子效果：
- `bottom`: 底部邊框
- `top`: 頂部邊框
- `horizontal`: 玩家所在高度的水平線
- `vertical`: 垂直連接線

例如：
```yaml
claim-types:
  basic:
    bottom:
      particle: REDSTONE
      color:
        red: 0
        green: 255
        blue: 0
    top:
      particle: REDSTONE
      color:
        red: 0
        green: 200
        blue: 0
```

#### 性能設定
```yaml
performance:
  # 最大同時渲染的領地數量
  max-claims: 10
  # 使用非同步渲染
  async-rendering: true
  # 快取時間 (單位：秒)
  cache-time: 10
```

#### 顯示設定
```yaml
display:
  # 顯示模式: CORNERS (僅角落), OUTLINE (僅輪廓), FULL (完整邊界), WALL (牆面)
  mode: OUTLINE
  # 顯示自己的領地
  show-own-claims: true
  # 顯示他人的領地
  show-others-claims: true
  # 顯示管理員領地
  show-admin-claims: true
  # 顯示城鎮領地
  show-town-claims: true
  # 牆面模式顯示半徑
  wall-radius: 3.0
  # OUTLINE模式顯示半徑
  outline-radius: 4.0
```

## 顯示模式說明
- **CORNERS**: 只顯示領地的角落部分，最節省效能
- **OUTLINE**: 僅顯示玩家附近的領地輪廓，適合一般使用
- **FULL**: 顯示完整的領地邊界，包含所有邊緣
- **WALL**: 在玩家靠近領地時顯示牆面樣式的粒子，最直觀

## 常見問題
1. **粒子效果不顯示怎麼辦？**
   - 確認您已經使用 `/cv on` 啟用了視覺化效果
   - 檢查您是否有 `claimvisualizer.use` 權限
   - 確認您處於有 GriefDefender 領地的世界中
   - 確認領地在您的渲染距離 (`render-distance`) 範圍內

2. **插件影響伺服器效能怎麼辦？**
   - 在配置中增加 `update-interval` 的值
   - 減小 `render-distance` 渲染距離
   - 增加 `spacing` 粒子間隔
   - 減少 `max-claims` 最大同時渲染的領地數量
   - 使用 CORNERS 或 OUTLINE 顯示模式

3. **想要更改粒子顏色怎麼辦？**
   - 在配置檔案的 `claim-types` 部分修改對應領地類型的顏色值

4. **如何個人化粒子顯示？**
   - 使用 `/claimvisual mode <模式>` 命令設定個人偏好的顯示模式

## 效能優化特性
- 分批顯示粒子，避免一次性顯示大量粒子造成的延遲
- 智慧型渲染，只顯示玩家附近的領地部分
- 非同步處理領地資料計算
- 距離感知系統，根據玩家位置動態調整顯示內容

## 作者與貢獻
- 開發者: twme
- 如果您發現任何問題或有建議，歡迎提交 Issue 或 Pull Request

## 授權條款
本插件以 MIT 授權條款釋出。

---

感謝您使用 ClaimVisualizer！希望這個插件能為您的 Minecraft 伺服器增添實用的視覺化功能。
