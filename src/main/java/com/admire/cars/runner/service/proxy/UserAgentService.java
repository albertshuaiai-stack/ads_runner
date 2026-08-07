package com.admire.cars.runner.service.proxy;

import com.admire.cars.runner.constant.Constant;
import com.admire.cars.runner.service.ReferUserAgentService;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserAgentService {


    @Autowired
    private ReferUserAgentService referUserAgentService;


    public String getUserAgent() {
        List<String> userAgentList = referUserAgentService.getUserAgentListByDevice(Constant.DEVICE_TYPE_DESK);
        if (userAgentList.isEmpty()) {
            userAgentList = List.of(Constant.DEFAULT_DESKTOP_USER_AGENT);
        }
        String userAgent = userAgentList.get(RandomUtils.nextInt(0, userAgentList.size()));
        if (!StringUtils.hasText(userAgent) || !userAgent.contains("Mozilla/5.0")) {
            userAgent = Constant.DEFAULT_DESKTOP_USER_AGENT;
        }
        return userAgent;
    }


}
