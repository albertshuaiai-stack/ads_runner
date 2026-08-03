package com.admire.cars.runner.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Lob;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "QRTZ_JOB_DETAILS")
@IdClass(QrtzJobDetailId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QrtzJobDetail {

    @Id
    @Column(name = "SCHED_NAME", nullable = false, length = 120)
    private String schedName;

    @Id
    @Column(name = "JOB_NAME", nullable = false, length = 200)
    private String jobName;

    @Id
    @Column(name = "JOB_GROUP", nullable = false, length = 200)
    private String jobGroup;

    @Column(name = "DESCRIPTION", length = 250)
    private String description;

    @Column(name = "JOB_CLASS_NAME", nullable = false, length = 250)
    private String jobClassName;

    @Column(name = "IS_DURABLE", nullable = false)
    private Boolean isDurable;

    @Column(name = "IS_NONCONCURRENT", nullable = false)
    private Boolean isNonconcurrent;

    @Column(name = "IS_UPDATE_DATA", nullable = false)
    private Boolean isUpdateData;

    @Column(name = "REQUESTS_RECOVERY", nullable = false)
    private Boolean requestsRecovery;

    @JsonIgnore
    @Lob
    @Column(name = "JOB_DATA", columnDefinition = "LONGBLOB")
    private byte[] jobData;
}
