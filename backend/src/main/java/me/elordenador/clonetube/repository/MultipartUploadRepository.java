package me.elordenador.clonetube.repository;

import me.elordenador.clonetube.models.MultipartUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MultipartUploadRepository extends JpaRepository<MultipartUpload, Integer> {
    @Query("select m from MultipartUpload m where m.upload_id = :uploadId and m.upload_key = :uploadKey")
    Optional<MultipartUpload> findByUploadIdAndUploadKey(@Param("uploadId") String uploadId,
                                                          @Param("uploadKey") String uploadKey);

    @Query("select m from MultipartUpload m where m.status = :status order by m.created_at")
    List<MultipartUpload> findByStatus(@Param("status") String status, Pageable pageable);
}
