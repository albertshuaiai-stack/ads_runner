package com.admire.cars.runner.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Normal 自动任务每日额度: 每天基础额度 + 点击增量换算的额度, 未用完自动结转。
 * Daily allowance for normal auto tasks: base allowance plus allowance earned from click deltas, carried over.
 */
@Component
@Getter
public class NormalShiftLinkDailyConfig {

    // 总开关: 关闭后不限额 / master switch, disables the cap
    @Value("${shift-link.normal.daily-limit.enabled:true}")
    private boolean enabled;

    // 每天先给的固定条数 / fixed number of links granted at the start of each day
    @Value("${shift-link.normal.daily-limit.base:20}")
    private long dailyBase;

    // 每个新增点击可换的条数 / links granted per newly reported click
    @Value("${shift-link.normal.daily-limit.click-multiplier:25}")
    private long clickMultiplier;
}
