package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.*;
import com.hieu.edurepo.enums.*;
import com.hieu.edurepo.repository.*;
import com.hieu.edurepo.security.CustomUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RealtimeSnapshotService {
    private final UserRepository users;
    private final DocumentRepository documents;
    public RealtimeSnapshotService(UserRepository users, DocumentRepository documents) { this.users = users; this.documents = documents; }
    public record Snapshot(ProfileView profile, List<LiveDocument> documents, List<LiveDocument> reviewQueue, boolean canReview) { }
    @Transactional(readOnly = true)
    public Snapshot snapshot(CustomUserPrincipal principal) {
        var account = users.findById(principal.getId()).orElseThrow(() -> new AccessDeniedException("Session expired"));
        if (!account.isEnabled() || account.getDeletedAt() != null
                || !Objects.equals(account.getPassword(), principal.getPassword())
                || !Objects.equals(account.getEmail(), principal.getUsername())
                || !account.getRoles().stream().map(role -> "ROLE_" + role.getName()).collect(Collectors.toSet())
                    .equals(principal.getAuthorities().stream().map(authority -> authority.getAuthority()).collect(Collectors.toSet())))
            throw new AccessDeniedException("Session expired");
        boolean reviewer = account.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ADMIN || role.getName() == RoleName.REVIEWER);
        return new Snapshot(ProfileView.from(account), documents.liveOwned(account.getId()),
                reviewer ? documents.liveQueue(List.of(DocumentStatus.SUBMITTED, DocumentStatus.APPROVED)) : List.of(), reviewer);
    }
}
