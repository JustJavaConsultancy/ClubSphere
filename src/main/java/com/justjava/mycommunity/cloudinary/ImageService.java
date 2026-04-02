package com.justjava.mycommunity.cloudinary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ImageService {

    private final CloudinaryService cloudinaryService;
    private final ImageRepository imageRepository;

    public ResponseEntity<Map> uploadImage(Image image) {
        try {
            if (image.getFile().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            image.setUrl(cloudinaryService.uploadFile(image.getFile(), "folder_1"));
            if(image.getUrl() == null) {
                return ResponseEntity.badRequest().build();
            }
            imageRepository.save(image);
            return ResponseEntity.ok().body(Map.of("url", image.getUrl()));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }


    }

    public String uploadImageReturnUrl(Image image,String mediaType) {
        try {
            if (image.getFile().isEmpty()) {
                return "File is Empty";
            }

            if("video".equalsIgnoreCase(mediaType)){
                image.setUrl(cloudinaryService.uploadVideo(image.getFile(), "folder_1"));
            }else{
                image.setUrl(cloudinaryService.uploadFile(image.getFile(), "folder_1"));

            }
             if(image.getUrl() == null) {
                return "File Cannot be uploaded";
            }
            imageRepository.save(image);
            return image.getUrl();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Set<String> uploadManyFilesReturnUrls(List<MultipartFile> files) {
        Set<String> uploadedUrls = new HashSet<>();
        try {
            if (files == null || files.isEmpty()) {
                return Collections.emptySet();
            }
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    log.debug("File is Empty");
                    uploadedUrls.add("One or more files are empty.");
                    continue;
                }
                String url = cloudinaryService.uploadFile(file, "folder_1");
                uploadedUrls.add(Objects.requireNonNullElse(url, "File cannot be uploaded."));
            }
            return uploadedUrls;
        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage());
            return null;
        }
    }
}