---
name: build-learning-repository
description: Build, extend, review, or explain a Spring Boot web application for managing an internal learning-material repository and publication approval workflow. Use when the user asks to create, plan, implement, debug, or document this project using Spring Boot, Spring Security, Spring Data JPA, Thymeleaf, MySQL, file upload, role-based access, document approval states, admin/reviewer/submitter/user flows, or a 30-day student project plan.
---

# Build Learning Repository

## Overview

Use this skill to guide Codex through the user's academic Spring Boot project: a web system for storing internal learning materials and controlling the document publication process. Keep the output practical for a student building in 30 days, with clear MVC structure, database design, security, upload handling, approval history, and demo/report support.

## Operating Mode

Start by identifying the user's current stage:

- **Planning/reporting:** produce use cases, ERD, class diagram notes, schedule, module list, or explanation.
- **Project setup:** create or inspect a Maven Spring Boot project and configure MySQL, Thymeleaf, Security, JPA, and validation.
- **Feature build:** implement one vertical feature at a time: entity -> repository -> service -> controller -> Thymeleaf views -> tests.
- **Debugging:** reproduce the issue, locate the failing layer, fix narrowly, and add a guard test where reasonable.
- **Review/completion:** check security rules, status transitions, upload validation, database mapping, and demo readiness.

When the user is learning, explain in Vietnamese with beginner-friendly reasoning. Prefer short examples and avoid assuming they already know Spring internals.

## Project Scope

Build around these core roles:

- `ADMIN`: manage users, roles, categories, departments, and system overview.
- `SUBMITTER`: submit documents, upload files, view own documents, revise after feedback.
- `REVIEWER`: review submitted documents, approve, reject, or request revision.
- `USER`: search, view, and download published documents.

Use these document states unless the existing project already defines a compatible enum:

```text
DRAFT
SUBMITTED
REVISION_REQUIRED
APPROVED
PUBLISHED
REJECTED
```

Core entities:

```text
User, Role, Document, Category, Department, ApprovalHistory
```

Read `references/project-blueprint.md` when creating a new project, designing modules, database tables, security rules, or report content.

## Build Workflow

For implementation tasks:

1. Inspect existing files before editing.
2. Preserve the project's package naming, view conventions, and service style.
3. Implement through layers in this order: model, repository, service, controller, templates, tests.
4. Keep each feature usable end to end before moving to the next feature.
5. Use Spring Security method or URL authorization for role boundaries.
6. Validate file uploads by size, empty file, extension, and storage path safety.
7. Store file metadata in MySQL, not file bytes, unless the user explicitly asks for BLOB storage.
8. Track every review decision in `ApprovalHistory`.
9. Run the smallest relevant verification command: `mvn test`, `mvn spring-boot:run`, or targeted tests if present.

## Architecture Rules

Use the conventional structure:

```text
config
controller
entity
repository
service
service.impl
dto or form
exception
```

Prefer DTO/form objects for user input when forms become complex. Keep entities focused on persistence.

Use Thymeleaf templates with shared fragments for `header`, `sidebar`, and `footer` if the project already has or needs a consistent layout.

## Security Rules

Require authentication for dashboards, submissions, reviews, and admin pages.

Allow anonymous users only for login and public/published document browsing when requested.

Use BCrypt for passwords. Seed at least one admin account for local demo if the project has no user creation path yet.

Never expose raw upload directories directly unless access control is handled. Serve downloads through a controller that checks whether the document is published or the current user has permission.

## Student Deliverables

When the user asks for documentation, generate concise Vietnamese content for:

- Problem statement and project objective.
- Actor/use case list.
- Database tables and relationships.
- Main workflow: submit -> review -> revise/approve -> publish.
- 30-day implementation plan.
- Screenshots/demo checklist.
- Limitations and future development.

Keep the project realistic for a student: prioritize login, role authorization, document CRUD, upload, approval workflow, search/filter, and basic statistics before advanced features.
Beta
4 / 3000
used queries