package me.elordenador.clonetube.repository;

import me.elordenador.clonetube.enums.VisibilityEnum;
import me.elordenador.clonetube.models.Video;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Integer> {
    Optional<Video> findByFilename(String filename);

    List<Video> findAllByVisibility(VisibilityEnum visibility);

    @Query("select v from Video v join fetch v.author where v.visibility = :visibility")
    List<Video> findAllByVisibilityWithAuthor(@Param("visibility") VisibilityEnum visibility);

    List<Video> findAllByAuthorId(Integer authorId);

    List<Video> findAllByAuthorIdOrderByCreatedAtDescIdDesc(Integer authorId, Pageable pageable);

    List<Video> findAllByAuthorIdAndVisibilityOrderByCreatedAtDescIdDesc(
            Integer authorId, VisibilityEnum visibility, Pageable pageable);

    long countByAuthorId(Integer authorId);

    long countByAuthorIdAndVisibility(Integer authorId, VisibilityEnum visibility);
}
