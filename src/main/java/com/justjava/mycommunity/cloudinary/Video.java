package com.justjava.mycommunity.cloudinary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Setter
@Getter
@Entity
@Table(name = "videos")
public class Video {

    // Getters and Setters
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 500)
    private String cloudinaryUrl;

    @Column(nullable = false)
    private String publicId;

    private Long fileSize;

    private String contentType;

    private String duration; // You can extract this during upload if needed

    @Column(nullable = false)
    private String folder = "mycommunity/videos"; // Default folder

    @CreationTimestamp
    private LocalDateTime createdAt;

    // Constructors
    public Video() {}

    public Video(String title, String description, String cloudinaryUrl,
                 String publicId, Long fileSize, String contentType, String folder) {
        this.title = title;
        this.description = description;
        this.cloudinaryUrl = cloudinaryUrl;
        this.publicId = publicId;
        this.fileSize = fileSize;
        this.contentType = contentType;
        this.folder = folder;
    }

}