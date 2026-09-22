package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.Bookmark;
import com.hieu.edurepo.entity.CollectionItem;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentCollection;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.BookmarkRepository;
import com.hieu.edurepo.repository.CollectionItemRepository;
import com.hieu.edurepo.repository.DocumentCollectionRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.service.DocumentService;
import com.hieu.edurepo.service.LibraryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@Transactional
/**
 * Triển khai dịch vụ Thư viện học tập cá nhân của người học (Library Service Implementation).
 * <p>
 * Quản lý danh sách tài liệu yêu thích (Bookmark), tạo và sắp xếp các bộ sưu tập tài liệu theo chuyên đề,
 * và hỗ trợ các đề xuất học liệu phù hợp dựa trên danh mục sinh viên quan tâm.
 * </p>
 */
public class LibraryServiceImpl implements LibraryService {
    private final BookmarkRepository bookmarkRepository;
    private final DocumentCollectionRepository collectionRepository;
    private final CollectionItemRepository itemRepository;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;

    public LibraryServiceImpl(BookmarkRepository bookmarkRepository,
                              DocumentCollectionRepository collectionRepository,
                              CollectionItemRepository itemRepository,
                              DocumentRepository documentRepository,
                              DocumentService documentService) {
        this.bookmarkRepository = bookmarkRepository;
        this.collectionRepository = collectionRepository;
        this.itemRepository = itemRepository;
        this.documentRepository = documentRepository;
        this.documentService = documentService;
    }

    @Override
    public boolean toggleBookmark(User user, Long documentId) {
        Document document = requirePublished(documentId);
        var existing = bookmarkRepository.findByUserIdAndDocumentId(user.getId(), documentId);
        if (existing.isPresent()) {
            bookmarkRepository.delete(existing.get());
            return false;
        }
        Bookmark bookmark = new Bookmark();
        bookmark.setUser(user);
        bookmark.setDocument(document);
        bookmarkRepository.save(bookmark);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBookmarked(Long userId, Long documentId) {
        return userId != null && bookmarkRepository.existsByUserIdAndDocumentId(userId, documentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Bookmark> bookmarks(Long userId) {
        return bookmarkRepository.findDetailedByUserId(userId);
    }

    @Override
    public DocumentCollection createCollection(User owner, String name, String description) {
        String normalizedName = normalizeRequired(name, "Tên bộ sưu tập không được để trống");
        if (normalizedName.length() > 150) throw new InvalidStatusException("Tên bộ sưu tập quá dài");
        if (collectionRepository.existsByOwnerIdAndNameIgnoreCase(owner.getId(), normalizedName)) {
            throw new InvalidStatusException("Bạn đã có bộ sưu tập cùng tên");
        }
        DocumentCollection collection = new DocumentCollection();
        collection.setOwner(owner);
        collection.setName(normalizedName);
        collection.setDescription(normalizeDescription(description));
        return collectionRepository.save(collection);
    }

    @Override
    public DocumentCollection updateCollection(Long collectionId, Long ownerId, String name, String description) {
        DocumentCollection collection = findOwnedCollection(collectionId, ownerId);
        String normalizedName = normalizeRequired(name, "Tên bộ sưu tập không được để trống");
        if (normalizedName.length() > 150) throw new InvalidStatusException("Tên bộ sưu tập quá dài");
        if (collectionRepository.existsByOwnerIdAndNameIgnoreCaseAndIdNot(ownerId, normalizedName, collectionId)) {
            throw new InvalidStatusException("Bạn đã có bộ sưu tập cùng tên");
        }
        collection.setName(normalizedName);
        collection.setDescription(normalizeDescription(description));
        return collectionRepository.save(collection);
    }

    private String normalizeDescription(String description) {
        String value = description == null ? null : description.trim();
        if (value != null && value.length() > 500) throw new InvalidStatusException("Mô tả không được vượt quá 500 ký tự");
        return value;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentCollection> collections(Long ownerId) {
        return collectionRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentCollection findOwnedCollection(Long collectionId, Long ownerId) {
        return collectionRepository.findByIdAndOwnerId(collectionId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bộ sưu tập"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollectionItem> collectionItems(Long collectionId, Long ownerId) {
        findOwnedCollection(collectionId, ownerId);
        return itemRepository.findDetailedByCollectionId(collectionId);
    }

    @Override
    public void addToCollection(Long collectionId, Long documentId, Long ownerId) {
        DocumentCollection collection = findOwnedCollection(collectionId, ownerId);
        Document document = requirePublished(documentId);
        if (itemRepository.findByCollectionIdAndDocumentId(collectionId, documentId).isPresent()) return;
        CollectionItem item = new CollectionItem();
        item.setCollection(collection);
        item.setDocument(document);
        itemRepository.save(item);
    }

    @Override
    public void removeFromCollection(Long collectionId, Long documentId, Long ownerId) {
        findOwnedCollection(collectionId, ownerId);
        itemRepository.findByCollectionIdAndDocumentId(collectionId, documentId)
                .ifPresent(itemRepository::delete);
    }

    @Override
    public void deleteCollection(Long collectionId, Long ownerId) {
        DocumentCollection collection = findOwnedCollection(collectionId, ownerId);
        itemRepository.deleteByCollectionId(collectionId);
        collectionRepository.delete(collection);
    }

    @Override
    @Transactional(readOnly = true)
    public long collectionSize(Long collectionId) {
        return itemRepository.countByCollectionId(collectionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> recommend(Long userId, Document currentDocument, int limit) {
        LinkedHashSet<Long> categoryIds = new LinkedHashSet<>();
        if (userId != null) {
            bookmarks(userId).stream().map(Bookmark::getDocument).map(Document::getCategory)
                    .filter(category -> category != null).map(category -> category.getId())
                    .forEach(categoryIds::add);
        }
        if (currentDocument != null && currentDocument.getCategory() != null) {
            categoryIds.add(currentDocument.getCategory().getId());
        }
        if (categoryIds.isEmpty()) return List.of();
        Long excludeId = currentDocument == null ? null : currentDocument.getId();
        return documentRepository.findRecommendedByCategories(List.copyOf(categoryIds), excludeId,
                PageRequest.of(0, Math.max(1, limit)));
    }

    private Document requirePublished(Long documentId) {
        Document document = documentService.findById(documentId);
        if (document.getStatus() != DocumentStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Tài liệu chưa được công bố");
        }
        return document;
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) throw new InvalidStatusException(message);
        return value.trim();
    }
}
