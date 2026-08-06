package me.elordenador.clonetube.repository;

import me.elordenador.clonetube.models.MultipartUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MultipartUploadRepository extends JpaRepository<MultipartUpload, Integer> {
    Optional<MultipartUpload> findByUploadIdAndUploadKey(String uploadId, String uploadKey);
}
