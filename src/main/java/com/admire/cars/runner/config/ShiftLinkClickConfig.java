package com.admire.cars.runner.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 换链点击阈值(全局): 同类型所有链共用, 点击达到该值即换下一条, 改配置重启后对所有链立即生效。
 * Global click threshold per ads type, shared by every link; takes effect for all links after restart.
 */
@Component
@Getter
public class ShiftLinkClickConfig {

    @Value("${shift-link.click-capacity.normal:1}")
    private long normalCapacity;

    @Value("${shift-link.click-capacity.matrix:10}")
    private long matrixCapacity;

    // 按类型取阈值, 未识别类型按 Matrix 处理 / capacity by ads type, unknown types use the matrix value
    public long capacityFor(String adsType) {
        long value = adsType != null && "NORMAL".equalsIgnoreCase(adsType.trim())
                ? normalCapacity : matrixCapacity;
        return Math.max(value, 1L);
    }
}
