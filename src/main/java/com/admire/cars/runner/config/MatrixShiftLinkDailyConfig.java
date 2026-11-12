package com.admire.cars.runner.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Matrix 自动任务每日配额: 每个 Campaign 每天最多生成 N 条 ShiftLink, 次日清理后重新生成。
 * Daily quota for matrix auto tasks: at most N shift links per campaign per day, purged and regenerated the next day.
 */
@Component
@Getter
public class MatrixShiftLinkDailyConfig {

    // 总开关: 关闭后既不限额也不做日切清理 / master switch, disables both the cap and the daily purge
    @Value("${shift-link.matrix.daily-limit.enabled:true}")
    private boolean enabled;

    // 每天上限, <=0 表示暂停自动生成 / daily cap, <=0 pauses auto generation
    @Value("${shift-link.matrix.daily-limit:24}")
    private int dailyLimit;

    // 当日已生成数是否达到上限, dailyLimit<=0 时恒为 true / whether today's count reached the cap
    public boolean isLimitReached(long todayCount) {
        return todayCount >= dailyLimit;
    }
}
