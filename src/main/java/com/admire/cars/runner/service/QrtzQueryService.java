package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.QrtzJobDetail;
import com.admire.cars.runner.entity.QrtzTrigger;
import com.admire.cars.runner.entity.User;
import com.admire.cars.runner.repository.QrtzJobDetailRepository;
import com.admire.cars.runner.repository.QrtzTriggerRepository;
import com.admire.cars.runner.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class QrtzQueryService {

    private final QrtzTriggerRepository qrtzTriggerRepository;
    private final QrtzJobDetailRepository qrtzJobDetailRepository;
    private final UserRepository userRepository;

    public QrtzQueryService(
            QrtzTriggerRepository qrtzTriggerRepository,
            QrtzJobDetailRepository qrtzJobDetailRepository,
            UserRepository userRepository) {
        this.qrtzTriggerRepository = qrtzTriggerRepository;
        this.qrtzJobDetailRepository = qrtzJobDetailRepository;
        this.userRepository = userRepository;
    }

    public Page<QrtzTrigger> searchTriggers(
            String schedName,
            String triggerName,
            String triggerGroup,
            String jobName,
            String jobGroup,
            String triggerState,
            String triggerType,
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        String ownerPrefix = currentUser.getUserPhoneNumber().toLowerCase(Locale.ROOT) + "-";

        Specification<QrtzTrigger> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            addContainsPredicate(predicates, root.get("schedName"), schedName, criteriaBuilder);
            addContainsPredicate(predicates, root.get("triggerName"), triggerName, criteriaBuilder);
            addContainsPredicate(predicates, root.get("triggerGroup"), triggerGroup, criteriaBuilder);
            addContainsPredicate(predicates, root.get("jobName"), jobName, criteriaBuilder);
            addContainsPredicate(predicates, root.get("jobGroup"), jobGroup, criteriaBuilder);
            addEqualsIgnoreCasePredicate(predicates, root.get("triggerState"), triggerState, criteriaBuilder);
            addEqualsIgnoreCasePredicate(predicates, root.get("triggerType"), triggerType, criteriaBuilder);
            if (!admin) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("triggerGroup")), ownerPrefix + "%"));
            }
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
            Long currentUserId,
            Pageable pageable) {
        User currentUser = getCurrentUser(currentUserId);
        boolean admin = isAdmin(currentUser);
        String ownerPrefix = currentUser.getUserPhoneNumber().toLowerCase(Locale.ROOT) + "-";

        Specification<QrtzJobDetail> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            addContainsPredicate(predicates, root.get("schedName"), schedName, criteriaBuilder);
            addContainsPredicate(predicates, root.get("jobName"), jobName, criteriaBuilder);
            addContainsPredicate(predicates, root.get("jobGroup"), jobGroup, criteriaBuilder);
            addContainsPredicate(predicates, root.get("jobClassName"), jobClassName, criteriaBuilder);
            addContainsPredicate(predicates, root.get("description"), description, criteriaBuilder);
            if (!admin) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("jobGroup")), ownerPrefix + "%"));
            }
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

    private User getCurrentUser(Long currentUserId) {
        return userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("ADS_USER not found: " + currentUserId));
    }

    private boolean isAdmin(User user) {
        return user.getUserRole() != null
                && Arrays.stream(user.getUserRole().split(","))
                .map(String::trim)
                .anyMatch(role -> "admin".equalsIgnoreCase(role));
    }
}
