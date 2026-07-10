package com.admire.cars.runner.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "SHIFT_LINK_AUD")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShiftLinkAud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUD_ID")
    private Long audId;

    @Column(name = "SHIFT_LINK_ID", nullable = false)
    private Long shiftLinkId;

    @Column(name = "ADS_OWNER", length = 32)
    private String adsOwner;

    @Column(name = "ADS_NAME", length = 32)
    private String adsName;

    @Column(name = "ADS_TYPE", length = 16)
    private String adsType;

    @Column(name = "SEQ_NUMBER")
    private Long seqNumber;

    @Column(name = "OPERATION", nullable = false, length = 50)
    private String operation;

    @Column(name = "OPERATION_DATE", nullable = false)
    private LocalDateTime operationDate;

    @Column(name = "OLD_VALUE", columnDefinition = "LONGTEXT")
    private String oldValue;

    @Column(name = "NEW_VALUE", columnDefinition = "LONGTEXT")
    private String newValue;

    @Column(name = "OPERATOR", length = 100)
    private String operator;

    @PrePersist
    protected void onCreate() {
        operationDate = LocalDateTime.now();
    }
}
