-- Seed users. Passwords are BCrypt(12) hashes of:
--   admin    : Admin@Registrar24!
--   faculty  : Faculty@Reg2024!
--   reviewer : Review@Reg2024!
--   student  : Student@Reg24!
INSERT INTO users (id, username, password_hash, role, email, full_name, is_active, created_at, updated_at) VALUES
  (1, 'admin',    '$2b$12$BWTKijn9GjMlcMj7SMS72.EMn0RYwGKtWLiAGTC3OaGmEdvqCkTrC', 'ROLE_ADMIN',    'admin@registrar.local',    'System Administrator', 1, NOW(), NOW()),
  (2, 'faculty',  '$2b$12$JQwr6qtut6FFkQCyitGQpu7toO2jgvUBZgy/kSaUb3Lf.80I2xOlO', 'ROLE_FACULTY',  'faculty@registrar.local',  'Dr. Eleanor Vance',    1, NOW(), NOW()),
  (3, 'reviewer', '$2b$12$IYVrZvQNtmAwEZP5jZ2a2.IKGVO0h5bJFJLx5Zf6L3pqfvoB8EOqG', 'ROLE_REVIEWER', 'reviewer@registrar.local', 'Dean Marcus Holloway', 1, NOW(), NOW()),
  (4, 'student',  '$2b$12$dRn.KpDshC6aqpjXzldsSOfYFWg4CxRMB3nj.hMitGmXJAdB.liJG', 'ROLE_STUDENT',  'student@registrar.local',  'Aiko Tanaka',          1, NOW(), NOW());

-- Default message preferences for each user (quiet hours 22:00–07:00).
INSERT INTO message_preferences (user_id, muted_categories, quiet_start_hour, quiet_end_hour) VALUES
  (1, '', 22, 7),
  (2, '', 22, 7),
  (3, '', 22, 7),
  (4, '', 22, 7);

-- Sample courses.
INSERT INTO courses (id, code, title, description, credits, category, tags, author_name, price, rating_avg, enroll_count, is_active, created_at) VALUES
  (1, 'MATH201', 'Calculus II',                'Integration techniques, sequences, series.',           4.00, 'Mathematics', 'calculus,integrals,series',          'Dr. E. Vance',     0.00,  4.50, 22, 1, NOW()),
  (2, 'CS301',   'Data Structures & Algorithms','Trees, graphs, hashing, complexity analysis.',        3.00, 'Computer Science', 'algorithms,data,programming',  'Prof. R. Kim',     49.99, 4.80, 41, 1, NOW()),
  (3, 'HIST110', 'World History Survey',       'From antiquity to the modern era.',                     3.00, 'History',     'history,world,civilization',         'Dr. M. Holloway',  19.99, 4.20, 18, 1, NOW()),
  (4, 'CHEM210', 'Organic Chemistry I',        'Bonding, nomenclature, reactions of organic molecules.',4.00, 'Chemistry',   'chemistry,organic,molecules',        'Dr. S. Patel',     0.00,  4.10, 14, 1, NOW()),
  (5, 'ART150',  'Introduction to Studio Art', 'Drawing, color theory, composition fundamentals.',      2.00, 'Arts',        'art,drawing,studio',                 'Prof. L. Romero',  0.00,  4.70, 9,  1, NOW());

-- Sample course materials.
INSERT INTO course_materials (course_id, title, type, file_url, price, created_at) VALUES
  (1, 'Calculus II Workbook (PDF)', 'PDF',  '/materials/calc2.pdf', 9.99, NOW()),
  (2, 'CS301 Lab Manual',           'PDF',  '/materials/cs301-lab.pdf', 14.99, NOW()),
  (3, 'World History Atlas',        'PDF',  '/materials/atlas.pdf', 12.50, NOW());

-- Sample enrollments for the student user.
INSERT INTO enrollments (student_id, course_id, enrolled_at, status) VALUES
  (4, 1, NOW(), 'active'),
  (4, 2, NOW(), 'active'),
  (4, 3, NOW(), 'active');

-- Default grade rules per course (30% coursework / 20% midterm / 50% final).
INSERT INTO grade_rules (course_id, version, is_active, retake_policy, weights_json, created_by, created_at) VALUES
  (1, 1, 1, 'HIGHEST_SCORE', '{"coursework":30,"midterm":20,"final":50}', 1, NOW()),
  (2, 1, 1, 'HIGHEST_SCORE', '{"coursework":30,"midterm":20,"final":50}', 1, NOW()),
  (3, 1, 1, 'LATEST_SCORE',  '{"coursework":40,"midterm":20,"final":40}', 1, NOW()),
  (4, 1, 1, 'HIGHEST_SCORE', '{"coursework":25,"midterm":25,"final":50}', 1, NOW()),
  (5, 1, 1, 'LATEST_SCORE',  '{"coursework":50,"midterm":20,"final":30}', 1, NOW());

-- Trending search terms (seed data so the catalog page has something to show).
INSERT INTO search_terms (term, search_count, last_searched_at) VALUES
  ('calculus',       42, NOW()),
  ('algorithms',     35, NOW()),
  ('history',        21, NOW()),
  ('organic chemistry', 14, NOW()),
  ('art',            11, NOW()),
  ('data structures', 9, NOW());
