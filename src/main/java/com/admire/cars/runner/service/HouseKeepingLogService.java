package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.HouseKeepingLog;
import com.admire.cars.runner.repository.HouseKeepingLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class HouseKeepingLogService {

    private final HouseKeepingLogRepository houseKeepingLogRepository;

    public HouseKeepingLogService(HouseKeepingLogRepository houseKeepingLogRepository) {
        this.houseKeepingLogRepository = houseKeepingLogRepository;
    }

    /**
     * Get all house keeping logs with pagination, ordered by house keeping date in descending order
     *
     * @param pageable pagination parameters with sort order
     * @return page of house keeping logs sorted by date desc
     */
    @Transactional(readOnly = true)
    public Page<HouseKeepingLog> getAllHouseKeepingLogs(Pageable pageable) {
        return houseKeepingLogRepository.findAll(pageable);
    }

    /**
     * Get all house keeping logs ordered by house keeping date in descending order
     *
     * @return list of all house keeping logs sorted by date desc
     */
    @Transactional(readOnly = true)
    public List<HouseKeepingLog> getAllHouseKeepingLogsUnpaged() {
        return houseKeepingLogRepository.findAllOrderByHouseKeepingDateDesc();
    }
}
