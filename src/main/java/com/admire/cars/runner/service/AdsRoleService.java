package com.admire.cars.runner.service;

import com.admire.cars.runner.entity.AdsRole;
import com.admire.cars.runner.repository.AdsRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AdsRoleService {

    private final AdsRoleRepository adsRoleRepository;

    public AdsRoleService(AdsRoleRepository adsRoleRepository) {
        this.adsRoleRepository = adsRoleRepository;
    }

    public AdsRole create(AdsRole adsRole) {
        normalizeAndValidate(adsRole);
        if (adsRoleRepository.existsByRoleNameIgnoreCase(adsRole.getRoleName())) {
            throw new IllegalArgumentException("ROLE_NAME already exists");
        }
        return adsRoleRepository.save(adsRole);
    }

    @Transactional(readOnly = true)
    public AdsRole getById(Long id) {
        return adsRoleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ADS_ROLE not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<AdsRole> getAll() {
        return adsRoleRepository.findAll();
    }

    public AdsRole update(Long id, AdsRole updateData) {
        AdsRole existing = getById(id);
        if (updateData.getRoleName() != null) {
            existing.setRoleName(updateData.getRoleName());
        }
        normalizeAndValidate(existing);

        boolean duplicate = adsRoleRepository.findByRoleNameIgnoreCase(existing.getRoleName())
                .filter(role -> !role.getId().equals(id))
                .isPresent();
        if (duplicate) {
            throw new IllegalArgumentException("ROLE_NAME already exists");
        }
        return adsRoleRepository.save(existing);
    }

    public void delete(Long id) {
        AdsRole existing = getById(id);
        adsRoleRepository.delete(existing);
    }

    private void normalizeAndValidate(AdsRole adsRole) {
        if (adsRole == null || adsRole.getRoleName() == null || adsRole.getRoleName().isBlank()) {
            throw new IllegalArgumentException("roleName is required");
        }

        String normalized = switch (adsRole.getRoleName().trim().toLowerCase()) {
            case "admin" -> "Admin";
            case "matrix" -> "Matrix";
            case "normal" -> "Normal";
            case "both" -> "Both";
            default -> throw new IllegalArgumentException("roleName must be Admin, Matrix, Normal, or Both");
        };
        adsRole.setRoleName(normalized);
    }
}
