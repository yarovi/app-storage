package org.gitecsl.net.appstorage.service;

import org.gitecsl.net.appstorage.entity.DriverDocument;
import org.gitecsl.net.appstorage.repository.UploadedFileRepository;

import java.util.List;
import java.util.Optional;

public interface UploadedFileService {

    DriverDocument saveFileMetadata(DriverDocument file);

     List<DriverDocument> listFilesByPostulante(int postulanteId);

    Optional<DriverDocument> getFileById(Long id) ;
}
