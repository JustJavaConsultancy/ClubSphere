package com.justjava.mycommunity.cloudinary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {
    Optional<Video> findByPublicId(String publicId);
    List<Video> findByFolder(String folder);
    List<Video> findByTitleContainingIgnoreCase(String title);
}