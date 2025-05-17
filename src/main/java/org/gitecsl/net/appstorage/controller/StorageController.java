package org.gitecsl.net.appstorage.controller;


import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import org.gitecsl.net.appstorage.entity.DriverDocument;
import org.gitecsl.net.appstorage.service.UploadedFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class StorageController {

    Logger logger = LoggerFactory.getLogger(StorageController.class);

    private final UploadedFileService uploadedFileService;

    public StorageController(UploadedFileService uploadedFileService) {
        this.uploadedFileService = uploadedFileService;
    }

    @Value("${samba.hots}")
    private String smbHost;

    @Value("${samba.share}")
    private String smbShare;

    @Value("${samba.username}")
    private String smbUser;

    @Value("${samba.password}")
    private String smbPassword;

    @Value("${samba.domain:}")
    private String smbDomain;

    @PostMapping("/upload/{postulantId}")
    public ResponseEntity<Map<String, String>> uploadFile(
            @PathVariable String postulantId,
            @RequestParam("file") MultipartFile file) {

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "El archivo no puede estar vacío"));
            }
            if (file.getOriginalFilename().contains("..")) {
                return ResponseEntity.badRequest().body(Map.of("message", "Nombre de archivo inválido (Path Traversal)"));
            }

            // Verificar tipo de archivo (ej: solo PDF)
            if (!file.getContentType().equals("application/pdf")) {
                return ResponseEntity.badRequest().body(Map.of("message", "Solo se permiten archivos PDF"));
            }

            SMBClient client = new SMBClient();

            try (Connection connection = client.connect(this.smbHost)) {
                AuthenticationContext ac = new AuthenticationContext(smbUser, smbPassword.toCharArray(), smbDomain);
                Session session = connection.authenticate(ac);

                try (DiskShare share = (DiskShare) session.connectShare(this.smbShare)) {
                    String directoryPath = "postulante-" + postulantId;
                    if (!share.folderExists(directoryPath)) {
                        share.mkdir(directoryPath);
                    }

                    String filename = Paths.get(file.getOriginalFilename()).getFileName().toString(); // prevenir path traversal
                    String remotePath = directoryPath + "\\" + filename;

                    try (File f = share.openFile(remotePath,
                            EnumSet.of(AccessMask.GENERIC_WRITE),
                            null,
                            SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_OVERWRITE_IF,
                            null)) {

                        try (OutputStream os = f.getOutputStream();
                             InputStream is = file.getInputStream()) {

                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                os.write(buffer, 0, bytesRead);
                            }
                        }
                    }
                }


                DriverDocument driverDocument = new DriverDocument();
                driverDocument.setFilename(file.getOriginalFilename());
                driverDocument.setPath("smb://" + this.smbHost + "/" + this.smbShare + "/" + "postulante-" + postulantId + "/" + file.getOriginalFilename());
                driverDocument.setPostulantId(Integer.parseInt(postulantId));
                driverDocument.setContentType(file.getContentType());
                driverDocument.setUploadDate(LocalDateTime.now());

                this.uploadedFileService.saveFileMetadata(driverDocument);
                logger.info("Archivo subido: " + file.getOriginalFilename() + " a la ruta: " + driverDocument.getPath());
                return ResponseEntity.ok(Map.of("message", "Archivo subido exitosamente."));

            } catch (IOException | SMBApiException e) {
                logger.error("Error al subir archivo", e);
                return ResponseEntity.internalServerError().body(Map.of("message", "Error al subir archivo: " + e.getMessage()));
            }
        } catch (Exception e) {
            logger.error("Error inesperado: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Error inesperado: " + e.getMessage()));
        }
    }

    @GetMapping("/{documentId}/{filename:.+}")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable int documentId,
            @PathVariable String filename) {

        logger.info("Los Parametros son: documentId: " + documentId + " filename: " + filename);
        try (SMBClient client = new SMBClient()) {
            URI smbUri = URI.create("smb://134.122.125.240/public"); // ejemplo: smb://134.122.125.240/public
            String host = smbUri.getHost(); // 134.122.125.240
            String shareName = smbUri.getPath().replaceFirst("/", ""); // public

            AuthenticationContext auth = new AuthenticationContext(smbUser, smbPassword.toCharArray(), smbDomain);
            try (Connection connection = client.connect(host);
                 Session session = connection.authenticate(auth);
                 DiskShare share = (DiskShare) session.connectShare(shareName)) {

                String remotePath = "postulante-" + documentId + "\\" + filename;

                if (!share.fileExists(remotePath)) {
                    return ResponseEntity.notFound().build();
                }

                try (File file = share.openFile(remotePath,
                        EnumSet.of(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null);
                     InputStream inputStream = file.getInputStream()) {

                    byte[] fileBytes = inputStream.readAllBytes();

                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                            .body(fileBytes);
                }
            }

        } catch (IOException e) {
            logger.error("Error al descargar archivo: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{postulantId}")
    private ResponseEntity<List<DriverDocument>> getDocuments(@PathVariable int postulantId) {
        List<DriverDocument> documents = this.uploadedFileService.getAllDocumentOfPostulant(postulantId);
        return ResponseEntity.ok(documents);
    }

    //contra path traversal
}
