package com.admire.cars.runner.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class QrtzJobDetailId implements Serializable {
    private String schedName;
    private String jobName;
    private String jobGroup;
}
