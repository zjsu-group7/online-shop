package com.group7.shop.repository;

import com.group7.shop.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByParentIdAscSortAscIdAsc();

    List<Category> findByParentIdIsNotNullOrderByIdAsc();

    boolean existsByParentId(Long parentId);

    @Query("select (count(c) > 0) from Category c where c.name = :name "
            + "and ((:parentId is null and c.parentId is null) or c.parentId = :parentId)")
    boolean existsByNameAndParent(@Param("name") String name, @Param("parentId") Long parentId);
}