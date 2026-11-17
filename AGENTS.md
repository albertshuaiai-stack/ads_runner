# ads_runner — 换链系统后端

Spring Boot 3 + Java 17，包根 `com.admire.cars.runner`。部署形态：Windows 服务器 `C:\ads_app\ads_runner-0.0.1-SNAPSHOT.jar`，端口 8080，profile=prod，MySQL + Quartz JDBC 存储（重启后任务不丢）。

## 系统全景（本仓库是其中一环）

广告联盟链接自动换链系统，共三个独立仓库（都在 GitHub，工作区通常放在 `TestTool/` 下并排）：
- **ads_runner（本仓库）**：后端服务，定时生成联盟链接、换链调度、点击/花费上报、postback 归因统计。
- **ads_front**：管理后台前端（React 19 + Vite，track.lunalvabeau.com），任务/链接/统计管理，并生成贴到 Google Ads 的脚本模板。
- **lunalvabeau_site**：对外联盟导购内容站（React 静态站，lunalvabeau.com），投放落地载体。

数据流：Google Ads 脚本（ads_front 生成）每小时拉 `/api/normal/ads`、`/api/matrix/ads` 模板并上报累计点击/花费到 `/api/track/report`；联盟订单 postback 回调归因；用户在 ads_front 看投入产出。

## 核心业务概念（设计意图）

### 1. 联盟链接池（ShiftLink）与换链
- 每条 `ShiftLink` = 一次联盟跳转的完整 URL（fullUrl 必须包含 landingPageUrl 片段，取模板时该片段替换为 `{lpurl}` 交给 Google Ads）。**换链池不看链接状态**（停投由任务层控制），生成时状态固定 RUNNING；实体字段保留只为兼容历史数据。
- **换链只由真实点击驱动**（`/api/track/report` 上报的 campaign 累计点击），脚本拉取模板、定时任务执行频率都不消耗。规则实现在 `ShiftLinkRotationHelper`（唯一实现）：
  - **最新优先（newest-first）**：在用链达到阈值后，换到"最新生成且从未启用过的链"（按 id 最大），中间被跳过的旧链由每日清理删除。
  - 链的关键字段：`CLICK_BASE`（启用该链时的累计点击数，最大的 base 即当前在用链）、`DISPLAY_TIMES`（该链在用期间消耗的点击数）。
  - 阈值是全局配置：`shift-link.click-capacity.normal`（当前 1）、`shift-link.click-capacity.matrix`（当前 10），类型判断在 `ShiftLinkClickConfig.capacityFor`。
- 点击上报入口 `TrackReportService.applyCampaignClicks`：换链 + 记录花费 + 累计 Normal 每日额度，同一事务。

### 2. 自动任务（Quartz）
- `NormalAdsAutoTaskJob` / `MatrixAdsAutoTaskJob`：每 N 分钟（任务配置 intervalTime）走代理访问联盟链接，跟完跳转拿到最终 URL 存为新 ShiftLink，同时 `AdsClickTrackService.recordIssued` 落点击 ID 台账（subid=clickId，UUID）。
- **任务执行前会读数据库状态，非 RUNNING 直接跳过**（`*_AUTO_TASK_PAUSED_SKIP`）——历史 bug：暂停任务后 Quartz 触发器残留导致继续生成，此为兜底，不要删。
- Normal 每日生成额度 `NormalShiftLinkQuotaService`：每天 base（`shift-link.normal.daily-limit.base`=20）+ 每次点击增量 × multiplier（=25）；Matrix 每日上限 `shift-link.matrix.daily-limit`=24。
- `HousekeepingJob`：每天 04:00 UTC（北京 12:00）清理——Normal 链超 24h 全删、Matrix 链当日前的删、shift_link_log 7 天、ads_task_log 1 天。**注意与 24h 滚动窗口的交互**：最新优先换链正是为了配合这个清理（旧链永远轮不到）。

### 3. 反爬容错（方案 B）
- `AdsHttpClientTool.isBlockedLandingPage`：最终 URL 匹配落地页前缀且返回 403 时判成功（商家 TLS 指纹反爬，换 IP 无效）。errMsg 写 "landing page returned 403, accepted by url match"。Normal/Matrix/联盟测试三处都接了。
- 跳转中间页（ojrq、partnercentric 等）被挡时无法套用，会正常失败。
- `IpProxyService` 基础 OkHttpClient 必须有 `callTimeout`（防代理慢速回传挂死 Quartz 线程 + `@DisallowConcurrentExecution` 导致任务永不再触发）。
- URL 清理 `sanitizeUriValue` 会转义 `{ } " < > ^ \` 空格 `|` `[]` 等非法字符（shein 的 `network={network}` 宏教训）。

### 4. Postback 归因与投入产出统计
- 联盟订单回调 `/api/postback|/api/yp/postback|/api/pb/postback` 落 `ADS_NORMAL_POST_BACK`，`PostBackAttributionService` 按 subId→clickId→normalAdsId 归因（MATCHED/PARTIAL/UNMATCHED）。
- 统计入口 `PostBackAttributionService.stats`（Conversions 页）：每任务 Orders/Refunded/Conversion/Ad Cost(USD)/Profit/ROAS。
  - 广告花费：脚本上报的 `cost`（账户币种）存 `ADS_NORMAL_INFO.LAST_REPORT_COST/COST_CURRENCY`，用 `EXCHANGE_RATE` 表最新汇率折 USD；佣金按 USD 口径；ROAS=佣金÷花费。
  - 退单识别：status 命中关键词 reject/refund/cancel/void/decline/revers（不区分大小写）。
- Click 页 `clickSummary`：返回"已启用过的链 + 最新 500 条"合并，否则最新链全是排队未用的恒为 0。

### 5. Normal Task 的 subid 占位符
- 保存（create/update 走 `validateAndNormalize`）时 `ensureSubIdPlaceholder` 规范化 affiliate URL：`subid=`/`sub_id=`（大小写不敏感）统一成 `subid={subid}`，多处只留一处，缺失则追加；`sub2id=` 不动；超 128 字符放弃追加。

## 安全与对外接口

- `AuthInterceptor`（JWT，header `AMtoken` 或 `Authorization: Bearer`）拦截 `/api/**`，白名单在 `WebConfig`：register/health/login/postback 系列/click/`/api/normal/ads`/`/api/matrix/ads`/`/api/track/report`。**新对外接口必须加白名单**（`/api/track/report` 忘加导致 401 的教训）。
- 对外接口用 `api_key`（query param）自校验：`UserService.getEnabledUserByApiKey`，遗留 key 是手机号 Base64。

## 目录速查

```
controller/   REST 入口（AdsApiConsumeController 拉模板+postback、TrackReportController 上报）
service/      业务（换链、额度、归因统计、汇率 CurrencyExchange*）
service/proxy/IpProxyService（SOCKS5 代理 + IP 国家校验）
job/          Quartz 任务（两个 AutoTask、Housekeeping）
event/        AdsAutoTaskSchedulerService（Quartz 注册/暂停，upsert 会先重建触发器再暂停——有竞态，靠 job 内状态检查兜底）
config/       ShiftLinkClickConfig 等阈值配置
entity/repository/  JPA
resources/db/changelog/  Liquibase（新变更递增编号 + master 注册）
```

## 测试与坑

- 测试用 H2：`src/test/resources/application.properties` 已配 `MODE=MySQL`（否则 `INSERT IGNORE` 语法挂）。全量 `mvnw test` 改动后必跑。
- 日志只能英文；关键分支要打日志（本项目的排查高度依赖日志）。
- `AdsNormalInfo`/`ShiftLink` 等实体字段新增后，`@AllArgsConstructor` 无调用方，但测试可能手工 new 构造器——改构造器参数要同步测试。
- 已知未修：`CurrencyExchangeService.toUsd` 的 `findLatestRate` 无 LIMIT，多天汇率后会抛异常（统计侧未用它，用的是 `findTopBy...` 最新一条）。
