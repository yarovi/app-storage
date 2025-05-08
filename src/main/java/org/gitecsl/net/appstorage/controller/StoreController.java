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
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/store")
@CrossOrigin(origins = "*")
public class StoreController {

    Logger logger = LoggerFactory.getLogger(StoreController.class);

    private final UploadedFileService uploadedFileService;
    public StoreController(UploadedFileService uploadedFileService) {
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

    @PostMapping("/{postulanteId}")
    public ResponseEntity<String> uploadFile(
            @PathVariable String postulanteId,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("El archivo no puede estar vacío");
        }
        if (file.getOriginalFilename().contains("..")) {
            return ResponseEntity.badRequest().body("Nombre de archivo inválido (Path Traversal)");
        }

        // Verificar tipo de archivo (ej: solo PDF)
        if (!file.getContentType().equals("application/pdf")) {
            return ResponseEntity.badRequest().body("Solo se permiten archivos PDF");
        }

        SMBClient client = new SMBClient();

        try (Connection connection = client.connect(this.smbHost)) {
            AuthenticationContext ac = new AuthenticationContext(smbUser, smbPassword.toCharArray(), smbDomain);
            Session session = connection.authenticate(ac);

            try (DiskShare share = (DiskShare) session.connectShare(this.smbShare)) {
                String directoryPath = "postulante-" + postulanteId;
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
            driverDocument.setPath("smb://" + this.smbHost + "/" + this.smbShare + "/" + "postulante-" + postulanteId + "/" + file.getOriginalFilename());
            driverDocument.setPostulanteId(Integer.parseInt(postulanteId));
            driverDocument.setContentType(file.getContentType());
            driverDocument.setUploadDate(LocalDateTime.now());

            this.uploadedFileService.saveFileMetadata(driverDocument);
            return ResponseEntity.ok("Archivo subido exitosamente.");

        } catch (IOException | SMBApiException e) {
            logger.error("Error al subir archivo", e);
            return ResponseEntity.internalServerError().body("Error al subir archivo: " + e.getMessage());
        }
    }

    @GetMapping("/{postulanteId}/{filename:.+}")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable String postulanteId,
            @PathVariable String filename) {

        try (SMBClient client = new SMBClient()) {
            URI smbUri = URI.create("smb://134.122.125.240/public"); // ejemplo: smb://134.122.125.240/public
            String host = smbUri.getHost(); // 134.122.125.240
            String shareName = smbUri.getPath().replaceFirst("/", ""); // public

            AuthenticationContext auth = new AuthenticationContext(smbUser, smbPassword.toCharArray(), smbDomain);
            try (Connection connection = client.connect(host);
                 Session session = connection.authenticate(auth);
                 DiskShare share = (DiskShare) session.connectShare(shareName)) {

                String remotePath = "postulante-" + postulanteId + "\\" + filename;

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


    //contra path traversal
}
