package com.inuwara.pdf_ink_cost_estimator.repository;

import com.inuwara.pdf_ink_cost_estimator.model.printerSettings;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PrinterSettingsRepository extends JpaRepository<printerSettings, Long> {
    Optional<printerSettings> findByUserId(String userId);
    // Spring Data JPA automatically provides methods like save(), findById(), and findAll()
}
