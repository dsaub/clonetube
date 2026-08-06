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

    @Query("select v from Video v where v.author.id = :authorId order by v.created_at desc, v.id desc")
    List<Video> findAllByAuthorIdOrderByCreatedAtDescIdDesc(@Param("authorId") Integer authorId, Pageable pageable);

    @Query("select v from Video v where v.author.id = :authorId and v.visibility = :visibility "
            + "order by v.created_at desc, v.id desc")
    List<Video> findAllByAuthorIdAndVisibilityOrderByCreatedAtDescIdDesc(
            @Param("authorId") Integer authorId, @Param("visibility") VisibilityEnum visibility, Pageable pageable);

    long countByAuthorId(Integer authorId);

    long countByAuthorIdAndVisibility(Integer authorId, VisibilityEnum visibility);
}
