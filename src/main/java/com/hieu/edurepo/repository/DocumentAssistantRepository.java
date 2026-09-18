package com.hieu.edurepo.repository;

import com.hieu.edurepo.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentAssistantRepository extends Repository<Document, Long> {

    @Query(value = "select d from Document d left join fetch d.category c left join fetch d.createdBy creator "
            + "where d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED "
            + "and (:keyword = '' or lower(d.title) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.summary, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.keywords, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(c.name, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.authorName, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(creator.fullName, '')) like lower(concat('%', :keyword, '%')))",
            countQuery = "select count(d) from Document d left join d.category c left join d.createdBy creator "
                    + "where d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED "
                    + "and (:keyword = '' or lower(d.title) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(d.summary, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(d.keywords, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(c.name, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(d.authorName, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(creator.fullName, '')) like lower(concat('%', :keyword, '%')))"
    )
    Page<Document> searchPublished(@Param("keyword") String keyword, Pageable pageable);

    @Query(value = "select d from Document d left join fetch d.category c left join fetch d.createdBy creator "
            + "where d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED "
            + "and (:keyword = '' or lower(d.title) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.summary, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.keywords, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(c.name, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.authorName, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(creator.fullName, '')) like lower(concat('%', :keyword, '%'))) "
            + "and (:topic = '' or lower(coalesce(c.name, '')) like lower(concat('%', :topic, '%')) "
            + "or lower(coalesce(d.keywords, '')) like lower(concat('%', :topic, '%')) "
            + "or lower(d.title) like lower(concat('%', :topic, '%')) "
            + "or lower(coalesce(d.summary, '')) like lower(concat('%', :topic, '%')) "
            + "or lower(coalesce(d.description, '')) like lower(concat('%', :topic, '%'))) "
            + "and (:author = '' or lower(coalesce(d.authorName, '')) like lower(concat('%', :author, '%')) "
            + "or lower(coalesce(creator.fullName, '')) like lower(concat('%', :author, '%'))) "
            + "and (:languageCode = '' or lower(coalesce(d.languageCode, '')) = lower(:languageCode)) "
            + "and (:year is null or year(d.publishedAt) = :year)",
            countQuery = "select count(d) from Document d left join d.category c left join d.createdBy creator "
                    + "where d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED "
                    + "and (:keyword = '' or lower(d.title) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(d.summary, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(d.keywords, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(c.name, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(d.authorName, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(creator.fullName, '')) like lower(concat('%', :keyword, '%'))) "
                    + "and (:topic = '' or lower(coalesce(c.name, '')) like lower(concat('%', :topic, '%')) "
                    + "or lower(coalesce(d.keywords, '')) like lower(concat('%', :topic, '%')) "
                    + "or lower(d.title) like lower(concat('%', :topic, '%')) "
                    + "or lower(coalesce(d.summary, '')) like lower(concat('%', :topic, '%')) "
                    + "or lower(coalesce(d.description, '')) like lower(concat('%', :topic, '%'))) "
                    + "and (:author = '' or lower(coalesce(d.authorName, '')) like lower(concat('%', :author, '%')) "
                    + "or lower(coalesce(creator.fullName, '')) like lower(concat('%', :author, '%'))) "
                    + "and (:languageCode = '' or lower(coalesce(d.languageCode, '')) = lower(:languageCode)) "
                    + "and (:year is null or year(d.publishedAt) = :year)"
    )
    Page<Document> searchPublishedStructured(@Param("keyword") String keyword,
                                             @Param("topic") String topic,
                                             @Param("author") String author,
                                             @Param("languageCode") String languageCode,
                                             @Param("year") Integer year,
                                             Pageable pageable);

    @Query(value = "select d from Document d left join fetch d.category c left join fetch d.createdBy creator "
            + "where d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED "
            + "and (:keyword = '' or lower(d.title) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.summary, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.keywords, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(c.name, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.authorName, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(creator.fullName, '')) like lower(concat('%', :keyword, '%'))) "
            + "and (:topic = '' or lower(coalesce(c.name, '')) like lower(concat('%', :topic, '%')) "
            + "or lower(coalesce(d.keywords, '')) like lower(concat('%', :topic, '%')) "
            + "or lower(d.title) like lower(concat('%', :topic, '%')) "
            + "or lower(coalesce(d.summary, '')) like lower(concat('%', :topic, '%')) "
            + "or lower(coalesce(d.description, '')) like lower(concat('%', :topic, '%'))) "
            + "and (:author = '' or lower(coalesce(d.authorName, '')) like lower(concat('%', :author, '%')) "
            + "or lower(coalesce(creator.fullName, '')) like lower(concat('%', :author, '%'))) "
            + "and (:languageCode = '' or lower(coalesce(d.languageCode, '')) = lower(:languageCode)) "
            + "and (:year is null or year(d.publishedAt) = :year) "
            + "order by case "
            + "when :keyword <> '' and lower(d.title) like lower(concat('%', :keyword, '%')) then 0 "
            + "when :topic <> '' and lower(d.title) like lower(concat('%', :topic, '%')) then 0 "
            + "when :keyword <> '' and lower(coalesce(d.keywords, '')) like lower(concat('%', :keyword, '%')) then 1 "
            + "when :topic <> '' and lower(coalesce(d.keywords, '')) like lower(concat('%', :topic, '%')) then 1 "
            + "when :keyword <> '' and lower(coalesce(c.name, '')) like lower(concat('%', :keyword, '%')) then 2 "
            + "when :topic <> '' and lower(coalesce(c.name, '')) like lower(concat('%', :topic, '%')) then 2 "
            + "when :keyword <> '' and (lower(coalesce(d.authorName, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(creator.fullName, '')) like lower(concat('%', :keyword, '%'))) then 3 "
            + "when :author <> '' and (lower(coalesce(d.authorName, '')) like lower(concat('%', :author, '%')) "
            + "or lower(coalesce(creator.fullName, '')) like lower(concat('%', :author, '%'))) then 3 "
            + "when :keyword <> '' and (lower(coalesce(d.summary, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%'))) then 4 "
            + "else 5 end, d.publishedAt desc, d.viewCount desc, d.downloadCount desc, d.id desc",
            countQuery = "select count(d) from Document d left join d.category c left join d.createdBy creator "
                    + "where d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED "
                    + "and (:keyword = '' or lower(d.title) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(d.description, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(d.summary, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(d.keywords, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(c.name, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(d.authorName, '')) like lower(concat('%', :keyword, '%')) "
                    + "or lower(coalesce(creator.fullName, '')) like lower(concat('%', :keyword, '%'))) "
                    + "and (:topic = '' or lower(coalesce(c.name, '')) like lower(concat('%', :topic, '%')) "
                    + "or lower(coalesce(d.keywords, '')) like lower(concat('%', :topic, '%')) "
                    + "or lower(d.title) like lower(concat('%', :topic, '%')) "
                    + "or lower(coalesce(d.summary, '')) like lower(concat('%', :topic, '%')) "
                    + "or lower(coalesce(d.description, '')) like lower(concat('%', :topic, '%'))) "
                    + "and (:author = '' or lower(coalesce(d.authorName, '')) like lower(concat('%', :author, '%')) "
                    + "or lower(coalesce(creator.fullName, '')) like lower(concat('%', :author, '%'))) "
                    + "and (:languageCode = '' or lower(coalesce(d.languageCode, '')) = lower(:languageCode)) "
                    + "and (:year is null or year(d.publishedAt) = :year)"
    )
    Page<Document> searchPublishedRelevant(@Param("keyword") String keyword,
                                           @Param("topic") String topic,
                                           @Param("author") String author,
                                           @Param("languageCode") String languageCode,
                                           @Param("year") Integer year,
                                           Pageable pageable);

    @Query("select d from Document d left join fetch d.category c left join fetch d.createdBy creator "
            + "where d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED")
    List<Document> findPublishedCandidates(Pageable pageable);

    @Query("select distinct c.name from Document d join d.category c "
            + "where d.status = com.hieu.edurepo.enums.DocumentStatus.PUBLISHED "
            + "and c.name is not null "
            + "and (:keyword = '' or lower(c.name) like lower(concat('%', :keyword, '%')) "
            + "or lower(coalesce(d.keywords, '')) like lower(concat('%', :keyword, '%')) "
            + "or lower(d.title) like lower(concat('%', :keyword, '%'))) "
            + "order by c.name")
    List<String> suggestPublishedCategories(@Param("keyword") String keyword, Pageable pageable);
}
