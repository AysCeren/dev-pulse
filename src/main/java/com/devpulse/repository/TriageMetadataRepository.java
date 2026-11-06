package com.devpulse.repository;

import com.devpulse.domain.entity.TriageMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TriageMetadataRepository extends JpaRepository<TriageMetadata, UUID> {

}
