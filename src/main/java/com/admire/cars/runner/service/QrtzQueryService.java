package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.QrtzJobDetail;
import com.admire.cars.runner.entity.QrtzTrigger;
import com.admire.cars.runner.repository.QrtzJobDetailRepository;
import com.admire.cars.runner.repository.QrtzTriggerRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class QrtzQueryService {

    private final QrtzTriggerRepository qrtzTriggerRepository;
    private final QrtzJobDetailRepository qrtzJobDetailRepository;

    public QrtzQueryService(
            QrtzTriggerRepository qrtzTriggerRepository,
            QrtzJobDetailRepository qrtzJobDetailRepository) {
        this.qrtzTriggerRepository = qrtzTriggerRepository;
        this.qrtzJobDetailRepository = qrtzJobDetailRepository;
    }

    public Page<QrtzTrigger> searchTriggers(
            String schedName,
            String triggerName,
            String triggerGroup,
            String jobName,
            String jobGroup,
            String triggerState,
            String triggerType,
            Pageable pageable) {
        Specification<QrtzTrigger> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            addContainsPredicate(predicates, root.get("schedName"), schedName, criteriaBuilder);
            addContainsPredicate(predicates, root.get("triggerName"), triggerName, criteriaBuilder);
            addContainsPredicate(predicates, root.get("triggerGroup"), triggerGroup, criteriaBuilder);
            addContainsPredicate(predicates, root.get("jobName"), jobName, criteriaBuilder);
            addContainsPredicate(predicates, root.get("jobGroup"), jobGroup, criteriaBuilder);
            addEqualsIgnoreCasePredicate(predicates, root.get("triggerState"), triggerState, criteriaBuilder);
            addEqualsIgnoreCasePredicate(predicates, root.get("triggerType"), triggerType, criteriaBuilder);
            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return qrtzTriggerRepository.findAll(specification, pageable);
    }

    public Page<QrtzJobDetail> searchJobs(
            String schedName,
            String jobName,
            String jobGroup,
            String jobClassName,
            String description,
            Pageable pageable) {
        Specification<QrtzJobDetail> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            addContainsPredicate(predicates, root.get("schedName"), schedName, criteriaBuilder);
            addContainsPredicate(predicates, root.get("jobName"), jobName, criteriaBuilder);
            addContainsPredicate(predicates, root.get("jobGroup"), jobGroup, criteriaBuilder);
            addContainsPredicate(predicates, root.get("jobClassName"), jobClassName, criteriaBuilder);
            addContainsPredicate(predicates, root.get("description"), description, criteriaBuilder);
            return predicates.isEmpty()
                    ? criteriaBuilder.conjunction()
                    : criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return qrtzJobDetailRepository.findAll(specification, pageable);
    }

    private void addContainsPredicate(
            List<Predicate> predicates,
            jakarta.persistence.criteria.Expression<String> field,
            String value,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        predicates.add(criteriaBuilder.like(
                criteriaBuilder.lower(field),
                "%" + value.trim().toLowerCase(Locale.ROOT) + "%"));
    }

    private void addEqualsIgnoreCasePredicate(
            List<Predicate> predicates,
            jakarta.persistence.criteria.Expression<String> field,
            String value,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        predicates.add(criteriaBuilder.equal(
                criteriaBuilder.lower(field),
                value.trim().toLowerCase(Locale.ROOT)));
    }
}
