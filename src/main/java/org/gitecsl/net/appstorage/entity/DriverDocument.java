package org.gitecsl.net.appstorage.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "driver_documents")
public class DriverDocument {


    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "driver_document_seq")
    @SequenceGenerator(
            name = "driver_document_seq",
            sequenceName = "driver_document_seq",
            allocationSize = 1 // importante para que sea compatible con PostgreSQL
    )
    private Long id;

    private int postulanteId;
    private String filename;
    private String path;
    private String contentType;
    private LocalDateTime uploadDate;
}
