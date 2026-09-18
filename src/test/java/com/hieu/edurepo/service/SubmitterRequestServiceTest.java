package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.SubmitterRequest;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.enums.SubmitterRequestStatus;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.SubmitterRequestRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.service.impl.SubmitterRequestServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubmitterRequestServiceTest {
    private final SubmitterRequestRepository requests = mock(SubmitterRequestRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final SubmitterRequestService service = new SubmitterRequestServiceImpl(requests, users, roles);

    @Test
    void userCanCreateOnePendingRequestOnly() {
        User requester = user(7L, RoleName.USER);
        when(users.lockById(7L)).thenReturn(Optional.of(requester));
        when(requests.existsByRequesterIdAndStatus(7L, SubmitterRequestStatus.PENDING))
                .thenReturn(false, true);

        service.create(7L, "  Tôi muốn đóng góp giáo trình Java.  ");

        ArgumentCaptor<SubmitterRequest> captor = ArgumentCaptor.forClass(SubmitterRequest.class);
        verify(requests).save(captor.capture());
        assertEquals("Tôi muốn đóng góp giáo trình Java.", captor.getValue().getReason());
        assertEquals(SubmitterRequestStatus.PENDING, captor.getValue().getStatus());
        assertThrows(InvalidStatusException.class, () -> service.create(7L, "Yêu cầu thứ hai"));
    }

    @Test
    void approvalAddsSubmitterWithoutRemovingExistingRoles() {
        User requester = user(7L, RoleName.USER);
        User admin = user(1L, RoleName.ADMIN);
        SubmitterRequest request = pending(10L, requester);
        Role submitter = new Role(RoleName.SUBMITTER);
        when(requests.lockById(10L)).thenReturn(Optional.of(request));
        when(users.lockById(7L)).thenReturn(Optional.of(requester));
        when(users.findById(1L)).thenReturn(Optional.of(admin));
        when(roles.findByName(RoleName.SUBMITTER)).thenReturn(Optional.of(submitter));

        service.approve(10L, 1L);

        assertEquals(SubmitterRequestStatus.APPROVED, request.getStatus());
        assertTrue(requester.getRoles().stream().anyMatch(role -> role.getName() == RoleName.USER));
        assertTrue(requester.getRoles().stream().anyMatch(role -> role.getName() == RoleName.SUBMITTER));
        verify(users).save(requester);
    }

    @Test
    void rejectionRequiresAndStoresAReason() {
        User requester = user(7L, RoleName.USER);
        User admin = user(1L, RoleName.ADMIN);
        SubmitterRequest request = pending(10L, requester);
        when(requests.lockById(10L)).thenReturn(Optional.of(request));
        when(users.findById(1L)).thenReturn(Optional.of(admin));

        assertThrows(IllegalArgumentException.class, () -> service.reject(10L, 1L, " "));
        verify(requests, never()).save(request);

        service.reject(10L, 1L, "  Chưa mô tả rõ nguồn học liệu.  ");
        assertEquals(SubmitterRequestStatus.REJECTED, request.getStatus());
        assertEquals("Chưa mô tả rõ nguồn học liệu.", request.getRejectionReason());
    }

    private User user(Long id, RoleName roleName) {
        User user = new User();
        user.setId(id);
        user.setRoles(Set.of(new Role(roleName)));
        return user;
    }

    private SubmitterRequest pending(Long id, User requester) {
        SubmitterRequest request = new SubmitterRequest();
        request.setRequester(requester);
        return request;
    }
}
