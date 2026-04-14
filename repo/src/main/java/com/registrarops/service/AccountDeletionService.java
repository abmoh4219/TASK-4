package com.registrarops.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.registrarops.entity.Order;
import com.registrarops.entity.StudentGrade;
import com.registrarops.entity.User;
import com.registrarops.repository.OrderRepository;
import com.registrarops.repository.StudentGradeRepository;
import com.registrarops.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Account deletion with 7-day soft-delete window and downloadable local export.
 *
 * Flow:
 *   1. {@link #exportAndSoftDelete} collects all of a user's orders + grades into
 *      a JSON file under {@code /tmp/exports/user_{id}_{token}.json}, stores the
 *      path on the user record, and stamps {@code deleted_at = NOW()}.
 *   2. {@link CustomUserDetailsService} blocks login for any user whose
 *      {@code deletedAt} is set, so the account is immediately unusable.
 *   3. The {@link #purgeExpiredSoftDeletes} scheduled job runs daily and physically
 *      anonymizes (we keep the audit trail intact) any user whose deletedAt is
 *      older than 7 days.
 *
 * The export format is intentionally simple JSON so it can be read back without
 * depending on this codebase.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);
    private static final int RETENTION_DAYS = 7;

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path exportDir;

    public AccountDeletionService(UserRepository userRepository,
                                  OrderRepository orderRepository,
                                  StudentGradeRepository studentGradeRepository,
                                  AuditService auditService,
                                  @Value("${registrarops.export-dir:/tmp/exports}") String exportDir) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.studentGradeRepository = studentGradeRepository;
        this.auditService = auditService;
        this.exportDir = Paths.get(exportDir);
    }

    @Transactional
    public String exportAndSoftDelete(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        try {
            Files.createDirectories(exportDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create export directory: " + exportDir, e);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("user", Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail() == null ? "" : user.getEmail(),
                "fullName", user.getFullName() == null ? "" : user.getFullName(),
                "role", user.getRole().name()));

        List<Order> orders = orderRepository.findByStudentIdOrderByCreatedAtDesc(userId);
        payload.put("orders", orders.stream().map(o -> Map.of(
                "id", o.getId(),
                "correlationId", o.getCorrelationId(),
                "status", o.getStatus().name(),
                "totalAmount", o.getTotalAmount().toString(),
                "createdAt", o.getCreatedAt().toString())).toList());

        List<StudentGrade> grades = studentGradeRepository.findByStudentIdOrderByCalculatedAtDesc(userId);
        payload.put("grades", grades.stream().map(g -> Map.of(
                "courseId", g.getCourseId(),
                "letterGrade", g.getLetterGrade(),
                "gpaPoints", g.getGpaPoints().toString(),
                "weightedScore", g.getWeightedScore().toString(),
                "calculatedAt", g.getCalculatedAt().toString())).toList());

        String token = UUID.randomUUID().toString().replace("-", "");
        Path file = exportDir.resolve("user_" + user.getId() + "_" + token + ".json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), payload);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write export: " + file, e);
        }

        user.setDeletedAt(LocalDateTime.now());
        user.setExportFilePath(file.toString());
        user.setIsActive(false);
        userRepository.save(user);

        auditService.log(userId, user.getUsername(), "ACCOUNT_DELETE_REQUESTED",
                "User", userId, null,
                "{\"exportFile\":\"" + file.getFileName() + "\"}", null);

        log.info("user {} soft-deleted, export at {}", user.getUsername(), file);
        return token;
    }

    /** Daily cleanup: hard-anonymize users whose soft-delete is older than 7 days. */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredSoftDeletes() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        List<User> all = userRepository.findAll();
        int purged = 0;
        for (User u : all) {
            if (u.getDeletedAt() != null && u.getDeletedAt().isBefore(cutoff)) {
                u.setEmail(null);
                u.setFullName("[purged]");
                u.setExportFilePath(null);
                userRepository.save(u);
                auditService.logSystem("ACCOUNT_PURGED", "User", u.getId(),
                        "{\"username\":\"" + u.getUsername() + "\"}");
                purged++;
            }
        }
        if (purged > 0) log.info("purged {} expired soft-deleted accounts", purged);
    }
}
