package com.justjava.mycommunity.gallery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileInfoRepository extends JpaRepository<FileInfo, String> {

    // Find all files ordered by date added
    List<FileInfo> findAllByOrderByDateAddedDesc();

    // Find files by type
    List<FileInfo> findByTypeOrderByDateAddedDesc(String type);

    // Search files by name
    @Query("SELECT f FROM FileInfo f WHERE " +
            "LOWER(f.name) LIKE LOWER(CONCAT('%', :query, '%'))" +
            "AND (:type is NULL OR f.type = :type)" +
            " ORDER BY f.dateAdded DESC")
    List<FileInfo> searchFiles(@Param("query") String query, @Param("type") String type);

    // Find by microservice file ID (useful for downloads and deletes)
    Optional<FileInfo> findByMicroserviceFileId(String microserviceFileId);
}
