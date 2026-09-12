package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Bookmark;
import com.hieu.edurepo.entity.CollectionItem;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentCollection;
import com.hieu.edurepo.entity.User;

import java.util.List;

public interface LibraryService {
    boolean toggleBookmark(User user, Long documentId);
    boolean isBookmarked(Long userId, Long documentId);
    List<Bookmark> bookmarks(Long userId);
    DocumentCollection createCollection(User owner, String name, String description);
    DocumentCollection updateCollection(Long collectionId, Long ownerId, String name, String description);
    List<DocumentCollection> collections(Long ownerId);
    DocumentCollection findOwnedCollection(Long collectionId, Long ownerId);
    List<CollectionItem> collectionItems(Long collectionId, Long ownerId);
    void addToCollection(Long collectionId, Long documentId, Long ownerId);
    void removeFromCollection(Long collectionId, Long documentId, Long ownerId);
    void deleteCollection(Long collectionId, Long ownerId);
    long collectionSize(Long collectionId);
    List<Document> recommend(Long userId, Document currentDocument, int limit);
}
