package com.admire.cars.runner.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "HOUSE_KEEPING_LOG")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HouseKeepingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "HOUSE_KEEPING_DATE")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate houseKeepingDate;

    @Column(name = "PURGE_SHIFT_LINK_LOG")
    private Long purgeShiftLinkLog;

    @Column(name = "PURGE_NORMAL_SHIFT_LINK")
    private Long purgeNormalShiftLink;

    @Column(name = "PURGE_MATRIX_SHIFT_LING")
    private Long purgeMatrixShiftLing;

    @Column(name = "PURGE_ADS_TASK_LOG")
    private Long purgeAdsTaskLog;

    @Column(name = "START_DATE")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startDate;

    @Column(name = "END_DATE")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endDate;

    @Column(name = "DURATION")
    private Long duration;
}
