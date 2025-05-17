package org.gitecsl.net.appstorage.repository;

import org.gitecsl.net.appstorage.entity.DriverDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UploadedFileRepository extends JpaRepository<DriverDocument, Long> {
    List<DriverDocument> findByPostulantId(int postulanteId);
}
