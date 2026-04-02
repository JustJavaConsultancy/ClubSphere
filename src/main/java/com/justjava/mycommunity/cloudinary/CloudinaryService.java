package com.justjava.mycommunity.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class CloudinaryService {

    @Resource
    private Cloudinary cloudinary;

    public String uploadFile(MultipartFile file, String folderName) {
        try {
            HashMap<Object, Object> options = new HashMap<>();

            // Add folder name to options if provided
            if (folderName != null && !folderName.trim().isEmpty()) {
                options.put("folder", folderName);
            }

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), options);
            System.out.println("File uploaded to Cloudinary: " + uploadResult);

            String secureUrl = (String) uploadResult.get("secure_url");
            return secureUrl;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Optional: Add a method specifically for certificate images with optimizations
    public String uploadCertificateImage(MultipartFile file, String type) {
        try {
            HashMap<Object, Object> options = new HashMap<>();

            // Set folder based on type
            String folderName;
            if ("signature".equals(type)) {
                folderName = "certificates/signatures";
                // Add signature-specific optimizations
                options.put("transformation", new Object[]{
                        ObjectUtils.asMap("width", 400, "height", 200, "crop", "limit"),
                        ObjectUtils.asMap("effect", "make_transparent:100")
                });
            } else {
                folderName = "certificates/logos";
                // Add logo-specific optimizations
                options.put("transformation", new Object[]{
                        ObjectUtils.asMap("width", 300, "height", 150, "crop", "limit")
                });
            }

            options.put("folder", folderName);

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), options);
            System.out.println("Certificate image uploaded to: " + folderName);
            System.out.println("Upload result: " + uploadResult);

            return (String) uploadResult.get("secure_url");

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String uploadVideo(MultipartFile file, String folderName) {
        try {
            // SIMPLE AND WORKING APPROACH - No eager transformations
            Map<String, Object> options = new HashMap<>();

            // Set resource type to video
            options.put("resource_type", "video");

            // Add folder name to options if provided
            if (folderName != null && !folderName.trim().isEmpty()) {
                options.put("folder", folderName);
            }

            // Optional: Add chunk size for better upload performance
            options.put("chunk_size", 6000000);

            // REMOVED ALL EAGER TRANSFORMATIONS - they are causing the ClassCastException

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), options);

            String secureUrl = (String) uploadResult.get("secure_url");
            System.out.println("Video uploaded successfully: " + secureUrl);
            System.out.println("Full upload result: " + uploadResult);

            return secureUrl;

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to upload video: " + e.getMessage());
        }
    }

    // Alternative method using ObjectUtils for cleaner syntax
    public String uploadVideoWithObjectUtils(MultipartFile file, String folderName) {
        try {
            // Use ObjectUtils for cleaner option building
            Map options = ObjectUtils.asMap(
                    "resource_type", "video",
                    "folder", folderName != null && !folderName.trim().isEmpty() ? folderName : "videos",
                    "chunk_size", 6000000
                    // Note: No eager transformations to avoid ClassCastException
            );

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), options);

            String secureUrl = (String) uploadResult.get("secure_url");
            System.out.println("Video uploaded successfully: " + secureUrl);

            return secureUrl;

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to upload video: " + e.getMessage());
        }
    }

    // If you need video transformations, use this method AFTER upload
    public String createVideoTransformations(String publicId, Map<String, Object> transformationParams) {
        try {
            // Generate URL with transformations
            String transformedUrl = cloudinary.url()
                    .resourceType("video")
                    .transformation(new com.cloudinary.Transformation()
                            .width((Integer) transformationParams.get("width"))
                            .height((Integer) transformationParams.get("height"))
                            .crop("scale"))
                    .generate(publicId);

            return transformedUrl;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Add method to extract public ID from URL
    public String extractPublicIdFromUrl(String url) {
        try {
            if (url == null || url.trim().isEmpty()) {
                return null;
            }

            // Cloudinary URL format: https://res.cloudinary.com/cloud_name/video/upload/v1234567/folder/public_id.mp4
            String[] parts = url.split("/upload/");
            if (parts.length > 1) {
                String path = parts[1];
                // Remove version prefix if present
                path = path.replaceFirst("v\\d+/", "");
                // Remove file extension
                int lastDotIndex = path.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    path = path.substring(0, lastDotIndex);
                }
                return path;
            }
            return null;
        } catch (Exception e) {
            System.err.println("Error extracting public ID from URL: " + url);
            return null;
        }
    }

    // Add method to delete video
    public void deleteVideo(String publicId) {
        try {
            if (publicId == null || publicId.trim().isEmpty()) {
                throw new IllegalArgumentException("Public ID cannot be null or empty");
            }

            Map options = ObjectUtils.asMap("resource_type", "video");
            Map result = cloudinary.uploader().destroy(publicId, options);
            System.out.println("Video deleted successfully: " + result);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to delete video: " + e.getMessage());
        }
    }

    // Add method to check if file is a video
    public boolean isVideoFile(MultipartFile file) {
        if (file == null || file.getContentType() == null) {
            return false;
        }
        return file.getContentType().startsWith("video/");
    }

    // Add method to validate file size
    public boolean isValidFileSize(MultipartFile file, long maxSizeInBytes) {
        if (file == null) {
            return false;
        }
        return file.getSize() <= maxSizeInBytes;
    }

    // Add method to get video information
    public Map getVideoInfo(String publicId) {
        try {
            Map options = ObjectUtils.asMap("resource_type", "video");
            return cloudinary.api().resource(publicId, options);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to get video info: " + e.getMessage());
        }
    }
}