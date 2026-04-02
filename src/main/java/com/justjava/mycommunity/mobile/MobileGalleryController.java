package com.justjava.mycommunity.mobile;

import com.justjava.mycommunity.gallery.FileFeignClient;
import com.justjava.mycommunity.gallery.FileInfo;
import com.justjava.mycommunity.gallery.FileInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Controller
@RequestMapping("/mobile")
public class MobileGalleryController {

    private final FileFeignClient fileFeignClient;
    private final FileInfoRepository fileInfoRepository;

    @Autowired
    public MobileGalleryController(FileFeignClient fileFeignClient, FileInfoRepository fileInfoRepository) {
        this.fileFeignClient = fileFeignClient;
        this.fileInfoRepository = fileInfoRepository;
    }

    @GetMapping("/gallery")
    public String mobileGallery(Model model,
                                @RequestParam(value = "type", defaultValue = "all") String type,
                                HttpServletRequest request) {

        System.out.println("=== MOBILE USER ACTION: Accessing Gallery ===");
        System.out.println("Filter type: " + type);
        System.out.println("User Agent: " + request.getHeader("User-Agent"));

        List<FileInfo> files;

        if ("all".equals(type)) {
            files = fileInfoRepository.findAllByOrderByDateAddedDesc();
        } else {
            files = fileInfoRepository.findByTypeOrderByDateAddedDesc(type);
        }

        // Convert to DTOs for the view
        List<MobileFileInfoDTO> fileDTOs = files.stream()
                .map(this::convertToMobileDTO)
                .collect(Collectors.toList());

        model.addAttribute("files", fileDTOs);
        model.addAttribute("activeTab", type);

        System.out.println("Displaying " + fileDTOs.size() + " files to mobile user");
        return "mobile-gallery";
    }

    @GetMapping("/gallery/add")
    public String mobileUploadFile(Model model, HttpServletRequest request) {
        System.out.println("=== MOBILE USER ACTION: Accessing Upload Page ===");
        System.out.println("User Agent: " + request.getHeader("User-Agent"));
        return "mobile-add-file";
    }

    @PostMapping("/gallery/upload")
    public String handleMobileFileUpload(@RequestParam("file") MultipartFile[] files,
                                         @RequestParam(value = "description", required = false) String description,
                                         Model model,
                                         HttpServletRequest request) {

        System.out.println("=== MOBILE USER ACTION: Uploading Files ===");
        System.out.println("Number of files: " + (files != null ? files.length : 0));
        System.out.println("Description: " + description);
        System.out.println("User Agent: " + request.getHeader("User-Agent"));

        if (files != null) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    System.out.println("Mobile file to upload: " + file.getOriginalFilename() + " (" + formatFileSize(file.getSize()) + ")");
                }
            }
        }

        List<String> uploadResults = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                try {
                    String result = uploadSingleFile(file, description);
                    uploadResults.add("Successfully uploaded: " + file.getOriginalFilename());
                } catch (Exception e) {
                    System.out.println("Mobile upload failed for " + file.getOriginalFilename() + ": " + e.getMessage());
                    errors.add("Failed to upload " + file.getOriginalFilename() + ": " + e.getMessage());
                }
            }
        }

        model.addAttribute("uploadResults", uploadResults);
        model.addAttribute("errors", errors);

        System.out.println("=== MOBILE UPLOAD SUMMARY ===");
        System.out.println("Successful uploads: " + uploadResults.size());
        System.out.println("Failed uploads: " + errors.size());

        if (errors.isEmpty()) {
            System.out.println("All mobile uploads successful - redirecting to gallery");
            return "redirect:/mobile/gallery";
        } else {
            System.out.println("Some mobile uploads failed - staying on upload page");
            return "mobile-add-file";
        }
    }

    private String uploadSingleFile(MultipartFile file, String description) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // Upload file to microservice using working endpoint
        String uploadResponse;
        try {
            uploadResponse = fileFeignClient.upload(file, "{}");

            if (uploadResponse == null || uploadResponse.trim().isEmpty()) {
                throw new RuntimeException("Failed to upload file to storage service - no response");
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("413")) {
                throw new RuntimeException("File is too large");
            }
            throw new RuntimeException("Failed to upload file to microservice: " + e.getMessage());
        }

        // Extract ID from response
        String fileId = uploadResponse;
        if (uploadResponse != null && uploadResponse.startsWith("File stored with ID: ")) {
            fileId = uploadResponse.substring("File stored with ID: ".length()).trim();
        }

        // Create file info entity
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(UUID.randomUUID().toString());
        fileInfo.setName(originalFilename);
        fileInfo.setMicroserviceFileId(fileId);
        fileInfo.setType(getFileType(fileExtension));
        fileInfo.setSize(formatFileSize(file.getSize()));
        fileInfo.setDateAdded(LocalDateTime.now());

        // Save to database
        fileInfoRepository.save(fileInfo);

        return fileId;
    }

    @PostMapping("/api/files/uploadWithMetaData")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadMobileFileWithMetadata(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request) {

        System.out.println("=== MOBILE USER ACTION: API File Upload ===");
        System.out.println("File: " + (file != null ? file.getOriginalFilename() : "null"));
        System.out.println("File size: " + (file != null ? formatFileSize(file.getSize()) : "null"));
        System.out.println("Description: " + description);
        System.out.println("User Agent: " + request.getHeader("User-Agent"));

        Map<String, Object> response = new HashMap<>();

        try {
            if (file == null || file.isEmpty()) {
                response.put("status", "error");
                response.put("message", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // Upload file to microservice using working endpoint
            String uploadResponse;
            try {
                uploadResponse = fileFeignClient.upload(file, "{}");

                if (uploadResponse == null || uploadResponse.trim().isEmpty()) {
                    response.put("status", "error");
                    response.put("message", "Failed to upload file to storage service - no response");
                    return ResponseEntity.status(500).body(response);
                }
            } catch (Exception e) {
                String errorMessage = "Failed to upload file to microservice. ";
                if (e.getMessage() != null && e.getMessage().contains("413")) {
                    errorMessage = "File is too large";
                } else if (e.getMessage() != null) {
                    errorMessage += "Error: " + e.getMessage();
                }
                response.put("status", "error");
                response.put("message", errorMessage);
                return ResponseEntity.status(500).body(response);
            }

            // Extract ID from response
            String fileId = uploadResponse;
            if (uploadResponse != null && uploadResponse.startsWith("File stored with ID: ")) {
                fileId = uploadResponse.substring("File stored with ID: ".length()).trim();
            }

            // Create file info entity
            FileInfo fileInfo = new FileInfo();
            fileInfo.setId(UUID.randomUUID().toString());
            fileInfo.setName(originalFilename);
            fileInfo.setMicroserviceFileId(fileId);
            fileInfo.setType(getFileType(fileExtension));
            fileInfo.setSize(formatFileSize(file.getSize()));
            fileInfo.setDateAdded(LocalDateTime.now());

            try {
                fileInfo = fileInfoRepository.save(fileInfo);
            } catch (Exception e) {
                response.put("status", "error");
                response.put("message", "Failed to save file info to database: " + e.getMessage());
                return ResponseEntity.status(500).body(response);
            }

            response.put("status", "success");
            response.put("message", "File uploaded successfully");
            response.put("fileInfo", convertToMobileDTO(fileInfo));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Failed to upload file: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping(value = "/api/files/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> uploadMobileMultipleFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "description", required = false) String description,
            HttpServletRequest request) {

        System.out.println("=== MOBILE USER ACTION: API Multiple File Upload ===");
        System.out.println("Number of files: " + (files != null ? files.length : 0));
        System.out.println("Description: " + description);
        System.out.println("User Agent: " + request.getHeader("User-Agent"));

        Map<String, Object> response = new HashMap<>();

        try {
            List<FileInfo> uploadedFiles = new ArrayList<>();

            for (MultipartFile file : files) {
                String originalFilename = file.getOriginalFilename();
                String fileExtension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }

                // Upload to microservice using working endpoint
                String uploadResponse;
                try {
                    uploadResponse = fileFeignClient.upload(file, "{}");
                    if (uploadResponse == null) {
                        throw new RuntimeException("Microservice upload failed for: " + originalFilename);
                    }
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("413")) {
                        throw new RuntimeException("File '" + originalFilename + "' is too large for the storage service");
                    }
                    throw new RuntimeException("Microservice upload failed for: " + originalFilename + " - " + e.getMessage());
                }

                // Extract file ID from response
                String fileId = uploadResponse;
                if (uploadResponse != null && uploadResponse.startsWith("File stored with ID: ")) {
                    fileId = uploadResponse.substring("File stored with ID: ".length()).trim();
                }

                FileInfo fileInfo = new FileInfo();
                fileInfo.setId(UUID.randomUUID().toString());
                fileInfo.setName(originalFilename);
                fileInfo.setMicroserviceFileId(fileId);
                fileInfo.setType(getFileType(fileExtension));
                fileInfo.setSize(formatFileSize(file.getSize()));
                fileInfo.setDateAdded(LocalDateTime.now());

                fileInfo = fileInfoRepository.save(fileInfo);
                uploadedFiles.add(fileInfo);
            }

            response.put("status", "success");
            response.put("message", files.length + " files uploaded successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Upload failed: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/api/files/search")
    @ResponseBody
    public ResponseEntity<List<MobileFileInfoDTO>> searchMobileFiles(
            @RequestParam(value = "query", defaultValue = "") String query,
            @RequestParam(value = "type", defaultValue = "all") String type,
            HttpServletRequest request) {

        System.out.println("=== MOBILE USER ACTION: Searching Files ===");
        System.out.println("Search query: '" + query + "'");
        System.out.println("Filter type: " + type);
        System.out.println("User Agent: " + request.getHeader("User-Agent"));

        List<FileInfo> files;

        if (query.isEmpty()) {
            if ("all".equals(type)) {
                files = fileInfoRepository.findAllByOrderByDateAddedDesc();
            } else {
                files = fileInfoRepository.findByTypeOrderByDateAddedDesc(type);
            }
        } else {
            String actualType = "all".equalsIgnoreCase(type) ? null : type;
            files = fileInfoRepository.searchFiles(query, actualType);
        }

        List<MobileFileInfoDTO> fileDTOs = files.stream()
                .map(this::convertToMobileDTO)
                .collect(Collectors.toList());

        System.out.println("Mobile search returned " + fileDTOs.size() + " files");
        return ResponseEntity.ok(fileDTOs);
    }

    @DeleteMapping("/gallery/delete/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteMobileFile(@PathVariable String id,
                                                                HttpServletRequest request) {
        System.out.println("=== MOBILE USER ACTION: Deleting File ===");
        System.out.println("File ID: " + id);
        System.out.println("User Agent: " + request.getHeader("User-Agent"));

        Map<String, Object> response = new HashMap<>();

        try {
            Optional<FileInfo> fileOptional = fileInfoRepository.findById(id);

            if (!fileOptional.isPresent()) {
                response.put("status", "error");
                response.put("message", "File not found");
                return ResponseEntity.notFound().build();
            }

            FileInfo file = fileOptional.get();
            System.out.println("Deleting mobile file: " + file.getName() + " (Microservice ID: " + file.getMicroserviceFileId() + ")");

            // Delete file from microservice
            ResponseEntity<String> deleteResponse = fileFeignClient.deleteFile(file.getMicroserviceFileId());
            if (!deleteResponse.getStatusCode().is2xxSuccessful()) {
                response.put("status", "error");
                response.put("message", "Failed to delete file from storage service");
                return ResponseEntity.status(500).body(response);
            }

            // Remove from database
            fileInfoRepository.delete(file);

            System.out.println("Mobile file successfully deleted: " + file.getName());
            response.put("status", "success");
            response.put("message", "File deleted successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.out.println("ERROR: Failed to delete mobile file with ID " + id + ": " + e.getMessage());
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", "Failed to delete file: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/gallery/download/{id}")
    @ResponseBody
    public ResponseEntity<?> downloadMobileFile(@PathVariable String id, HttpServletRequest request) {
        System.out.println("=== MOBILE USER ACTION: Downloading File ===");
        System.out.println("File ID: " + id);
        System.out.println("User Agent: " + request.getHeader("User-Agent"));

        try {
            // Find the file in database to get the microservice file ID
            Optional<FileInfo> fileOptional = fileInfoRepository.findById(id);

            if (!fileOptional.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            FileInfo fileInfo = fileOptional.get();
            String microserviceFileId = fileInfo.getMicroserviceFileId();

            System.out.println("Downloading mobile file: " + fileInfo.getName() + " (Microservice ID: " + microserviceFileId + ")");
            return fileFeignClient.downloadFile(microserviceFileId);
        } catch (Exception e) {
            System.out.println("ERROR: Failed to download mobile file with ID " + id + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Failed to download file: " + e.getMessage());
        }
    }

    private String getFileType(String fileExtension) {
        if (fileExtension == null) return "documents";

        String ext = fileExtension.toLowerCase();
        if (ext.matches("\\.(jpg|jpeg|png|gif|bmp|svg)")) {
            return "images";
        } else if (ext.matches("\\.(mp4|avi|mov|wmv|flv|webm)")) {
            return "videos";
        } else if (ext.matches("\\.(mp3|wav|flac|aac|ogg)")) {
            return "audio";
        } else {
            return "documents";
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // Helper method to convert FileInfo entity to Mobile DTO for the view
    private MobileFileInfoDTO convertToMobileDTO(FileInfo fileInfo) {
        MobileFileInfoDTO dto = new MobileFileInfoDTO();
        dto.setId(fileInfo.getId());
        dto.setName(fileInfo.getName());
        dto.setMicroserviceFileId(fileInfo.getMicroserviceFileId());
        dto.setType(fileInfo.getType());
        dto.setSize(fileInfo.getSize());
        dto.setDateAdded(fileInfo.getDateAddedFormatted());
        return dto;
    }

    // Mobile-specific DTO class for view compatibility
    public static class MobileFileInfoDTO {
        private String id;
        private String name;
        private String microserviceFileId;
        private String type;
        private String size;
        private String dateAdded;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getMicroserviceFileId() { return microserviceFileId; }
        public void setMicroserviceFileId(String microserviceFileId) { this.microserviceFileId = microserviceFileId; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getSize() { return size; }
        public void setSize(String size) { this.size = size; }

        public String getDateAdded() { return dateAdded; }
        public void setDateAdded(String dateAdded) { this.dateAdded = dateAdded; }

        public String getFilename() { return microserviceFileId; }
        public void setFilename(String filename) { this.microserviceFileId = filename; }

        // Add displayName property that HTML template expects
        public String getDisplayName() { return name; }
        public void setDisplayName(String displayName) { this.name = displayName; }
    }
}
