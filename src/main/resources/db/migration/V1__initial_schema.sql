CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    enabled BIT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(255),
    active BIT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE faculties (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(255),
    active BIT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_faculties_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE departments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(255),
    active BIT NOT NULL,
    faculty_id BIGINT,
    PRIMARY KEY (id),
    CONSTRAINT uk_departments_name UNIQUE (name),
    CONSTRAINT fk_departments_faculty FOREIGN KEY (faculty_id) REFERENCES faculties (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    author_name VARCHAR(255),
    file_name VARCHAR(255),
    file_path VARCHAR(255),
    file_type VARCHAR(255),
    file_size BIGINT,
    status VARCHAR(30) NOT NULL,
    category_id BIGINT,
    department_id BIGINT,
    created_by BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6),
    published_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_documents_status_published_at (status, published_at),
    INDEX idx_documents_category_id (category_id),
    CONSTRAINT fk_documents_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_documents_department FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_documents_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE approval_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    comment TEXT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_approval_history_document_id (document_id),
    CONSTRAINT fk_approval_history_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT fk_approval_history_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
