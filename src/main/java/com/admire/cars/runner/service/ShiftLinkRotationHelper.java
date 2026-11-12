package com.admire.cars.runner.service;

import com.admire.cars.runner.config.ShiftLinkClickConfig;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 换链规则的唯一实现: 点击上报和取模板都用这里判断哪条链在用, 阈值统一取配置, 不看每条链的 DISPLAY_NUMBER。
 * Single source of the rotation rule; the threshold comes from config, not from each link's DISPLAY_NUMBER.
 */
@Component
public class ShiftLinkRotationHelper {

    private static final String RUNNING_STATUS = "RUNNING";

    private final ShiftLinkRepository shiftLinkRepository;
    private final ShiftLinkClickConfig shiftLinkClickConfig;

    public ShiftLinkRotationHelper(ShiftLinkRepository shiftLinkRepository,
                                   ShiftLinkClickConfig shiftLinkClickConfig) {
        this.shiftLinkRepository = shiftLinkRepository;
        this.shiftLinkClickConfig = shiftLinkClickConfig;
    }

    public long capacity(String adsType) {
        return shiftLinkClickConfig.capacityFor(adsType);
    }

    // 运行中的链按 seqNumber、id 升序, 即消耗顺序 / running links in consumption order
    public List<ShiftLink> loadRunning(String adsOwner, String campaignName, String adsType) {
        return shiftLinkRepository
                .findByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberAsc(adsOwner, campaignName, adsType)
                .stream()
                .filter(link -> RUNNING_STATUS.equalsIgnoreCase(link.getStatus()))
                .sorted(Comparator.comparing((ShiftLink link) ->
                                link.getSeqNumber() == null ? 0L : link.getSeqNumber())
                        .thenComparing(ShiftLink::getId))
                .toList();
    }

    // 已用完: 点击达到阈值, 或者已经换到后面的链(下一条有了换链基准), 后者保证调大配置时不会回头用旧链
    // exhausted when clicks reached the threshold, or rotation already moved past it
    public boolean isExhausted(List<ShiftLink> links, int index, long capacity) {
        ShiftLink link = links.get(index);
        long used = link.getDisplayTimes() == null ? 0L : link.getDisplayTimes();
        if (used >= capacity) {
            return true;
        }
        return index + 1 < links.size() && links.get(index + 1).getClickBase() != null;
    }

    // 第一条未用完的链即当前在用的链, 没有则返回 -1 / index of the active link, -1 when all are exhausted
    public int activeIndex(List<ShiftLink> links, long capacity) {
        for (int i = 0; i < links.size(); i++) {
            if (!isExhausted(links, i, capacity)) {
                return i;
            }
        }
        return -1;
    }
}
