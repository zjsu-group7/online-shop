package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.CategoryNode;
import com.group7.shop.dto.CategoryRequest;
import com.group7.shop.entity.Category;
import com.group7.shop.repository.CategoryRepository;
import com.group7.shop.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    /** 两级类别树，供买家端筛选与卖家端管理共用。 */
    @Transactional(readOnly = true)
    public List<CategoryNode> tree() {
        List<Category> all = categoryRepository.findAllByOrderByParentIdAscSortAscIdAsc();
        Map<Long, List<Category>> childrenByParent = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));

        return all.stream()
                .filter(Category::isTopLevel)
                .sorted(Comparator.comparingInt(Category::getSort))
                .map(top -> CategoryNode.of(top,
                        childrenByParent.getOrDefault(top.getId(), List.of()).stream()
                                .sorted(Comparator.comparingInt(Category::getSort))
                                .map(child -> CategoryNode.of(child, List.of()))
                                .toList()))
                .toList();
    }

    @Transactional
    public CategoryNode create(CategoryRequest request) {
        String name = normalizeName(request.name());
        Long parentId = request.parentId();
        if (parentId != null) {
            Category parent = categoryRepository.findById(parentId)
                    .orElseThrow(() -> BusinessException.badRequest("上级类别不存在"));
            if (!parent.isTopLevel()) {
                throw BusinessException.badRequest("类别最多两级，不能在二级类别下继续新建");
            }
        }
        if (categoryRepository.existsByNameAndParent(name, parentId)) {
            throw BusinessException.conflict("同级下已存在同名类别");
        }
        int sort = request.sort() == null ? 0 : request.sort();
        Category saved = categoryRepository.save(new Category(name, parentId, sort));
        return CategoryNode.of(saved, List.of());
    }

    @Transactional
    public CategoryNode update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("类别不存在"));
        String name = normalizeName(request.name());
        if (!name.equals(category.getName())
                && categoryRepository.existsByNameAndParent(name, category.getParentId())) {
            throw BusinessException.conflict("同级下已存在同名类别");
        }
        category.setName(name);
        if (request.sort() != null) {
            category.setSort(request.sort());
        }
        Category saved = categoryRepository.save(category);
        return CategoryNode.of(saved, List.of());
    }

    /** 有二级类别或挂有商品时禁止删除。 */
    @Transactional
    public void delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("类别不存在"));
        if (category.isTopLevel() && categoryRepository.existsByParentId(id)) {
            throw BusinessException.conflict("该类别下还有二级类别，请先删除二级类别");
        }
        if (productRepository.existsByCategoryId(id)) {
            throw BusinessException.badRequest("该类别下存在商品，请先把商品改挂到其他类别");
        }
        categoryRepository.delete(category);
    }

    /** 校验商品使用的类别：必须存在且必须是二级类别。 */
    @Transactional(readOnly = true)
    public Category requireSecondLevel(Long categoryId) {
        if (categoryId == null) {
            throw BusinessException.badRequest("商品类别必填");
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> BusinessException.badRequest("商品类别不存在"));
        if (category.isTopLevel()) {
            throw BusinessException.badRequest("商品必须挂在二级类别下");
        }
        return category;
    }

    private String normalizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty()) {
            throw BusinessException.badRequest("类别名称必填");
        }
        return name;
    }
}