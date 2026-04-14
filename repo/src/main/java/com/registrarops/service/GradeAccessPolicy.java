package com.registrarops.service;

import com.registrarops.entity.Role;
import com.registrarops.entity.User;
import com.registrarops.repository.GradeComponentRepository;
import com.registrarops.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Service-layer object-level authorization for grade/student data.
 *
 * Rules:
 *   ROLE_ADMIN, ROLE_REVIEWER → any student / any course
 *   ROLE_FACULTY              → only students/courses where they have recorded
 *                               at least one grade component (scope by activity)
 *   ROLE_STUDENT              → only their own student id (courses denied)
 */
@Service
public class GradeAccessPolicy {

    private final UserRepository userRepository;
    private final GradeComponentRepository componentRepository;

    public GradeAccessPolicy(UserRepository userRepository,
                             GradeComponentRepository componentRepository) {
        this.userRepository = userRepository;
        this.componentRepository = componentRepository;
    }

    public void assertCanReadStudent(String principalUsername, Long targetStudentId) {
        User u = requireUser(principalUsername);
        switch (u.getRole()) {
            case ROLE_ADMIN:
            case ROLE_REVIEWER:
                return;
            case ROLE_STUDENT:
                if (u.getId().equals(targetStudentId)) return;
                throw new AccessDeniedException("Students may only read their own grades");
            case ROLE_FACULTY:
                if (componentRepository.existsByStudentIdAndRecordedBy(targetStudentId, u.getId())) return;
                throw new AccessDeniedException("Faculty may only read students they have graded");
            default:
                throw new AccessDeniedException("Role not permitted");
        }
    }

    public void assertCanReadCourse(String principalUsername, Long targetCourseId) {
        User u = requireUser(principalUsername);
        switch (u.getRole()) {
            case ROLE_ADMIN:
            case ROLE_REVIEWER:
                return;
            case ROLE_FACULTY:
                if (componentRepository.existsByCourseIdAndRecordedBy(targetCourseId, u.getId())) return;
                throw new AccessDeniedException("Faculty may only read courses they have graded");
            default:
                throw new AccessDeniedException("Role not permitted");
        }
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Unknown principal"));
    }
}
