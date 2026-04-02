package com.justjava.mycommunity.cloudinary;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class VideoService {

    private final VideoRepository videoRepository;
    private final CloudinaryService cloudinaryService;

    @Autowired
    public VideoService(VideoRepository videoRepository, CloudinaryService cloudinaryService) {
        this.videoRepository = videoRepository;
        this.cloudinaryService = cloudinaryService;
    }

    public Video uploadVideo(MultipartFile file, String title, String description, String folder) {
        try {
            // Validate file
            if (file.isEmpty()) {
                throw new IllegalArgumentException("File cannot be empty");
            }

            if (!isVideoFile(file)) {
                throw new IllegalArgumentException("Only video files are allowed");
            }

            // Use provided folder or default
            String targetFolder = (folder != null && !folder.trim().isEmpty())
                    ? folder : "mycommunity/videos";

            // Upload to Cloudinary
            String cloudinaryUrl = cloudinaryService.uploadVideo(file, targetFolder);

            if (cloudinaryUrl == null) {
                throw new RuntimeException("Failed to upload video to Cloudinary");
            }

            // Extract public ID from URL
            String publicId = cloudinaryService.extractPublicIdFromUrl(cloudinaryUrl);

            // Create and save video entity
            Video video = new Video(
                    title,
                    description,
                    cloudinaryUrl,
                    publicId,
                    file.getSize(),
                    file.getContentType(),
                    targetFolder
            );

            return videoRepository.save(video);

        } catch (Exception e) {
            throw new RuntimeException("Failed to upload video: " + e.getMessage(), e);
        }
    }

    public List<Video> getAllVideos() {
        return videoRepository.findAll();
    }

    public Optional<Video> getVideoById(Long id) {
        return videoRepository.findById(id);
    }

    public List<Video> getVideosByFolder(String folder) {
        return videoRepository.findByFolder(folder);
    }

    public List<Video> searchVideosByTitle(String title) {
        return videoRepository.findByTitleContainingIgnoreCase(title);
    }

    public void deleteVideo(Long id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Video not found with id: " + id));

        try {
            // Delete from Cloudinary
            cloudinaryService.deleteVideo(video.getPublicId());

            // Delete from database
            videoRepository.delete(video);

        } catch (Exception e) {
            throw new RuntimeException("Failed to delete video: " + e.getMessage(), e);
        }
    }

    public Video updateVideo(Long id, String title, String description) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Video not found with id: " + id));

        if (title != null && !title.trim().isEmpty()) {
            video.setTitle(title);
        }

        if (description != null) {
            video.setDescription(description);
        }

        return videoRepository.save(video);
    }

    private boolean isVideoFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("video/");
    }
}