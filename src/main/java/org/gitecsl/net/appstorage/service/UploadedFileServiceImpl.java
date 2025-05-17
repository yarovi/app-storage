package org.gitecsl.net.appstorage.service;

import lombok.AllArgsConstructor;
import org.gitecsl.net.appstorage.entity.DriverDocument;
import org.gitecsl.net.appstorage.repository.UploadedFileRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
@AllArgsConstructor
@Service
public class UploadedFileServiceImpl implements  UploadedFileService {

    private final UploadedFileRepository uploadedFileRepository;

    @Override
    public DriverDocument saveFileMetadata(DriverDocument file) {
        return uploadedFileRepository.save(file);
    }

    @Override
    public List<DriverDocument> getAllDocumentOfPostulant(int postulanteId) {
        return uploadedFileRepository.findByPostulantId(postulanteId);
    }

    @Override
    public Optional<DriverDocument> getFileById(Long id) {
        return uploadedFileRepository.findById(id);
    }
}
