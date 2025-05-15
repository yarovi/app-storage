package org.gitecsl.net.appstorage.service;

import org.gitecsl.net.appstorage.entity.DriverDocument;

import java.util.List;
import java.util.Optional;

public interface UploadedFileService {

    DriverDocument saveFileMetadata(DriverDocument file);

     List<DriverDocument> getAllDocumentOfPostulant(int postulanteId);

    Optional<DriverDocument> getFileById(Long id) ;
}
