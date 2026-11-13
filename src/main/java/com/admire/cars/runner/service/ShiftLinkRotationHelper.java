package com.admire.cars.runner.service;

import com.admire.cars.runner.config.ShiftLinkClickConfig;
import com.admire.cars.runner.entity.ShiftLink;
import com.admire.cars.runner.repository.ShiftLinkRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 换链规则的唯一实现(最新优先): 在用的链点击达到阈值后, 换成最新生成且没用过的链; 阈值统一取配置。
 * Single source of the rotation rule (newest first): once the active link reaches the threshold, switch to the newest unused link.
 */
@Component
public class ShiftLinkRotationHelper {

    // 按生成先后排序, id 自增即生成顺序 / creation order, the auto-increment id follows creation
    private static final Comparator<ShiftLink> BY_CREATION = Comparator.comparing(ShiftLink::getId);

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

    // 该 campaign 的全部链(不看状态): 停不停投由任务(AdsNormalInfo/AdsMatrixInfo)控制, 链级状态无意义
    // All links of the campaign regardless of status; pausing is controlled at the ads task level, not here
    public List<ShiftLink> loadPool(String adsOwner, String campaignName, String adsType) {
        return shiftLinkRepository
                .findByAdsOwnerAndAdsNameAndAdsTypeOrderBySeqNumberAsc(adsOwner, campaignName, adsType)
                .stream()
                .filter(link -> link.getId() != null)
                .toList();
    }

    // 最近一次被启用的链: 每次启用都以当时累计点击为基准, 基准最大的就是最近启用的, 调大阈值也不会回头用旧链
    // The most recently activated link: each activation stores the current lifetime clicks, so the largest base wins
    public Optional<ShiftLink> current(List<ShiftLink> links) {
        return links.stream()
                .filter(link -> link.getClickBase() != null)
                .max(Comparator.comparing(ShiftLink::getClickBase).thenComparing(BY_CREATION));
    }

    // 最新生成且从未启用过的链 / the newest link that has never been activated
    public Optional<ShiftLink> newestUnused(List<ShiftLink> links) {
        return links.stream()
                .filter(link -> link.getClickBase() == null)
                .max(BY_CREATION);
    }

    public boolean isUsedUp(ShiftLink link, long capacity) {
        long used = link.getDisplayTimes() == null ? 0L : link.getDisplayTimes();
        return used >= capacity;
    }

    // 取模板用的链: 在用且未用完的链优先, 否则最新未用过的链(下次点击上报时正式启用), 都没有返回空
    // Link for the template: the active link if not used up, otherwise the newest unused one, else empty
    public Optional<ShiftLink> selectForTemplate(List<ShiftLink> links, long capacity) {
        Optional<ShiftLink> active = current(links).filter(link -> !isUsedUp(link, capacity));
        return active.isPresent() ? active : newestUnused(links);
    }
}
