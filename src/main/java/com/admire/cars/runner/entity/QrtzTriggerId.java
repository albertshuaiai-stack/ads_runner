package com.admire.cars.runner.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class QrtzTriggerId implements Serializable {
    private String schedName;
    private String triggerName;
    private String triggerGroup;
}
