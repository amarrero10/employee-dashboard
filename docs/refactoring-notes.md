# Employee Dashboard — Refactoring & Production-Readiness Notes

This document explains the changes made to move the codebase toward
production-quality Spring Boot, and — just as importantly — *why* each change
matters. It's written to be read top to bottom as a learning resource.

**How it's organized**

1. [Background: two bugs fixed first](#background)
2. [Employee package](#employee-package) — explained in depth (concepts introduced here)
3. [Manager package](#manager-package) — same architecture, plus what's different
4. [Cross-cutting changes](#cross-cutting-changes) — things that span the whole app
5. [Left to your discretion](#left-to-your-discretion) — what I deliberately did *not* change, and why

A quick vocabulary note, since these terms repeat throughout:

- **Entity** — a class mapped to a database table (`@Entity`). It represents a row.
- **DTO (Data Transfer Object)** — a plain object that defines the *shape of data crossing the API boundary* (what a request accepts / a response returns). It is deliberately separate from the entity.
- **Repository** — the Spring Data interface that talks to the database.
- **Service** — where business logic lives.
- **Controller** — the thin HTTP layer that maps URLs to service calls.

The golden rule that drives most of this refactor:
**entities stay inside the service/persistence layer; the web layer only ever
sees DTOs.**

---

## Background

Before the production refactor, two blocking bugs were fixed. They're worth
understanding because they shaped later decisions.

### 1. Flyway wasn't running at all

**Symptom:** `Schema validation: missing table [employees]`, and *zero* Flyway
log lines on startup.

**Cause:** This project uses **Spring Boot 4**, which broke the old single
`spring-boot-autoconfigure` jar into many small per-integration modules. In
Boot 3, having `flyway-core` on the classpath was enough to trigger Flyway's
auto-configuration. In Boot 4 that auto-configuration lives in a separate
module, `org.springframework.boot:spring-boot-flyway`, and `flyway-core` does
**not** pull it in. Without that module, Spring never ran your migrations, so
the tables were never created, and Hibernate's `ddl-auto=validate` failed.

**Fix:** added the `spring-boot-flyway` dependency to `pom.xml`.

**Lesson:** when an auto-configuration silently doesn't happen in Boot 4, check
whether you're missing the dedicated `spring-boot-<integration>` module — not
just the third-party library.

### 2. `ConcurrentModificationException` when updating managers

**Cause:** `Employee` and `Manager` have a **bidirectional** `@ManyToMany`
relationship (`Employee.managers` ↔ `Manager.employees`), and both were
annotated with Lombok's `@Data`. `@Data` generates `equals()`, `hashCode()`,
and `toString()` that include **every** field — including those association
collections. So adding a `Manager` to a `Set` computed its `hashCode()`, which
reached into the lazy `employees` collection, which forced Hibernate to run a
query mid-modification and triggered a `ConcurrentModificationException`. (It
would also have caused infinite recursion when serializing to JSON.)

**Fix:** excluded the association collections from the generated methods:

```java
// Employee.java
@EqualsAndHashCode.Exclude
@ToString.Exclude
private Set<Manager> managers = new HashSet<>();

// Manager.java
@EqualsAndHashCode.Exclude
@ToString.Exclude
@JsonIgnore                       // also stops JSON serialization from looping back
private Set<Employee> employees = new HashSet<>();
```

**Lesson:** `@Data` is convenient but a poor fit for JPA entities with
relationships. Its `equals`/`hashCode`/`toString` should never traverse a
bidirectional association. (See [Left to your discretion](#left-to-your-discretion)
for the fuller version of this point.)

---

## Employee package

This section explains each pattern in detail. The manager package uses the same
patterns, so I'll keep that section shorter and just point out the differences.

### New file: `EmployeeUpdateDTO`

```java
@Data
public class EmployeeUpdateDTO {
    private String firstName;
    private String lastName;
    @Email(message = "Enter a valid email")
    private String email;
    private String phoneNumber;
    private List<Long> managerIds;
}
```

**What changed:** the PATCH endpoint used to accept the raw `Employee` **entity**
(`@RequestBody Employee`). Now it accepts this DTO.

**Why this matters (two real problems it solves):**

1. **Mass assignment / over-posting.** When a controller binds JSON straight
   onto an entity, a client can set *any* field — `id`, `createdAt`,
   `updatedAt`, even the raw `managers` collection. That's a security and
   data-integrity hole. A DTO is an explicit allow-list: the only things a
   client can change are the fields you put on the DTO.
2. **Partial-update (PATCH) semantics.** Every field is optional. A `null` means
   "the client didn't send this, so leave it alone." That's exactly what PATCH
   means (as opposed to PUT, which replaces the whole resource). Note there's no
   `@NotBlank` here — that's intentional, because for a partial update, *absent*
   is legal. But `@Email` still applies *when a value is present*.

**API contract change you should know:** the request body for updating managers
changed from `{"managers":[{"id":1}]}` to `{"managerIds":[1,2]}`. This also
makes update consistent with `EmployeeCreateDTO`, which already used
`managerIds`.

### New file: `EmployeeMapper`

```java
@Component
@RequiredArgsConstructor
public class EmployeeMapper {
    private final ManagerMapper managerMapper;

    public EmployeeResponseDTO toResponse(Employee employee) {
        List<ManagerResponseDTO> managers = employee.getManagers()
                .stream().map(managerMapper::toResponse).toList();
        return new EmployeeResponseDTO(
                employee.getId(), employee.getFirstName(), employee.getLastName(),
                employee.getEmail(), employee.getRole(), managers);
    }
}
```

**What changed:** the entity→DTO conversion used to be written inline, twice,
inside `EmployeeService` (once in `allEmployees()`, once in `createEmployee()`).
Now there's a single mapper.

**Why this matters — and a real bug it fixed:** the two inline copies had
*drifted*. `ManagerResponseDTO`'s constructor is
`(id, firstName, lastName, email, phoneNumber, role)`, but `allEmployees()` was
calling it with `getRole()` and `getPhoneNumber()` **swapped**. So
`GET /api/employees` returned every manager's phone and role transposed, while
`POST` returned them correctly — the same data looked different depending on the
endpoint. When conversion logic is duplicated, copies drift and bugs like this
hide. Centralizing it in one mapper means there's exactly one place that can be
right or wrong, and it's now right.

> **Teaching point:** this is *why* "Don't Repeat Yourself" matters — not for
> tidiness, but because duplicated logic diverges over time.

### Rewritten: `EmployeeService`

Several distinct improvements here.

**a) Constructor injection instead of field injection.**

```java
// before
@Autowired private EmployeeRepository repository;

// after
@Service
@RequiredArgsConstructor          // Lombok generates a constructor for final fields
public class EmployeeService {
    private final EmployeeRepository repository;
    private final ManagerRepository managerRepository;
    private final EmployeeMapper employeeMapper;
```

Why constructor injection is the recommended default:
- Fields can be `final` → the object is fully initialized and immutable once
  built; you can't accidentally leave a dependency null.
- Dependencies are **visible in the constructor signature**. If a class needs 8
  collaborators, the constructor makes that obvious (and the pain nudges you to
  split the class). Field injection hides it.
- **Testability:** you can `new EmployeeService(mockRepo, ...)` in a plain unit
  test. With `@Autowired` fields you need reflection or a Spring context.

**b) Transaction boundaries with `@Transactional`.**

Reads are marked `@Transactional(readOnly = true)`; writes `@Transactional`.
A transaction defines an all-or-nothing unit of work. In `updateEmployee`, we
load the employee, mutate fields, re-sync the managers join table, and save —
`@Transactional` guarantees those either all commit or all roll back. It also
keeps the Hibernate session open for the whole method, which is what lets the
mapper safely read the lazy `managers` collection. `readOnly = true` on queries
is a hint that lets Hibernate skip dirty-checking — a small performance win and
a statement of intent.

**c) Proper "not found" handling.**

```java
private Employee getEmployeeOrThrow(Long id) {
    return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                    "Employee not found with id " + id));
}
```

Previously a missing employee threw a bare `RuntimeException` (or
`NoSuchElementException` from `.orElseThrow()` with no argument), which Spring
turns into **HTTP 500 Internal Server Error**. But a missing record isn't a
server *error* — it's a "not found," which is **HTTP 404**. The new
`ResourceNotFoundException` (handled globally — see cross-cutting section) maps
to 404 so clients get an accurate, actionable status.

**d) `resolveManagers()` — validate references instead of silently dropping them.**

```java
private Set<Manager> resolveManagers(List<Long> managerIds) {
    if (managerIds == null || managerIds.isEmpty()) return new HashSet<>();
    Set<Long> distinctIds = new HashSet<>(managerIds);
    List<Manager> found = managerRepository.findAllById(distinctIds);
    if (found.size() != distinctIds.size()) {
        throw new ResourceNotFoundException(
                "One or more managers were not found for the given ids");
    }
    return new HashSet<>(found);
}
```

`findAllById` **silently ignores ids that don't exist** — ask for managers
`[1, 2, 999]` and you get back just `[1, 2]` with no error. That's a
"fail-silent" bug: the client thinks it assigned three managers. Now we compare
counts and fail loudly with a 404 if any id is bogus. It also de-duplicates the
incoming ids. This one helper is reused by both create and update.

**e) Methods now take and return DTOs**, never the entity. The service is the
boundary: DTO in → entity work → DTO out.

### Rewritten: `EmployeeController`

```java
@RestController
@RequestMapping("/api/employees")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
public class EmployeeController {
    private final EmployeeService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)                       // 201, not 200
    public EmployeeResponseDTO createEmployee(@Valid @RequestBody EmployeeCreateDTO dto) { ... }

    @PatchMapping("/{id}")
    public EmployeeResponseDTO updateEmployee(@PathVariable Long id,
                                              @Valid @RequestBody EmployeeUpdateDTO dto) { ... }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        return service.deleteEmployee(id)
                ? ResponseEntity.noContent().build()          // 204
                : ResponseEntity.notFound().build();          // 404
    }
}
```

Changes and reasoning:
- **No endpoint returns an entity anymore.** `getEmployee` and `updateEmployee`
  used to return `Employee`; they now return `EmployeeResponseDTO`. This is the
  golden rule in action — and it's what permanently prevents the JSON-recursion
  problem, because the response type is a flat DTO with no back-references.
- **`@Valid`** on the request body triggers the Jakarta Bean Validation
  annotations (`@NotBlank`, `@Email`, …). Invalid input is rejected with 400
  before your service ever runs.
- **Correct status codes:** `201 Created` for POST, `204 No Content` for a
  successful DELETE, `404` when the id doesn't exist. (We discussed why DELETE
  returns a bare status and no "success" message body — the status *is* the
  result; message text is the frontend's job.)
- **Constructor injection** here too.

### Employee entity

Only the `equals`/`hashCode`/`toString` exclusions described in the
[background](#2-concurrentmodificationexception-when-updating-managers) were
added. The mapping itself was already correct.

### Files intentionally left unchanged

`EmployeeCreateDTO` and `EmployeeResponseDTO` were already proper DTOs, so they
were kept as-is.

---

## Manager package

The manager package received the **same architectural treatment** as the
employee package, for the same reasons. Rather than repeat it all:

- **New `ManagerUpdateDTO`** — partial-update DTO (all fields optional, `@Email`
  when present). Same rationale as `EmployeeUpdateDTO`: replaces
  `@RequestBody Manager` so PATCH can no longer over-post onto the entity.
- **New `ManagerMapper`** — single source of truth for `Manager → ManagerResponseDTO`.
  (`EmployeeMapper` depends on this one to map an employee's nested managers.)
- **Rewritten `ManagerService`** — constructor injection, `@Transactional`
  boundaries, DTO in/out, `getManagerOrThrow()` → `ResourceNotFoundException`
  (404 instead of 500).
- **Rewritten `ManagerController`** — returns DTOs everywhere (`getManager`,
  `updateManager` no longer return the entity), `201` on create, `204/404` on
  delete, `@Valid` on request bodies, constructor injection.
- **Manager entity** — `@EqualsAndHashCode.Exclude`, `@ToString.Exclude`, and
  `@JsonIgnore` on the `employees` back-reference.

### What's different in the manager package

- **`@JsonIgnore` on `Manager.employees`.** The employee side does *not* have
  this. The reason is about which direction you want serialized: an employee's
  JSON *should* include its managers, but a manager's JSON should **not** loop
  back and list all its employees (that's the infinite-recursion path). So we
  cut the loop on the manager→employees side.
- **Minor signature fix:** `deleteManager(long id)` → `deleteManager(Long id)`,
  to match the rest of the codebase (the primitive `long` was inconsistent with
  every other id parameter, which is the object type `Long`).
- **The `managers.role` UNIQUE bug** — see the migration note in cross-cutting
  changes. This lived in the database schema, not the Java, but it's a
  manager-domain problem: as written, two managers could never share a role.

### Files intentionally left unchanged

`ManagerCreateDTO` and `ManagerResponseDTO` were already proper DTOs.

---

## Cross-cutting changes

These affect the whole application rather than one feature package.

### New `exception` package

The `EmailAlreadyExistsException` class and `GlobalExceptionHandler` used to sit
loose in the root package. They were moved into a dedicated
`com.employee.dashboard.exception` package, and two things were added:

- **`ResourceNotFoundException`** — thrown by the services; mapped to **404**.
- **`ErrorResponse`** — a small `record` giving errors a consistent JSON shape:

  ```java
  public record ErrorResponse(int status, String message, LocalDateTime timestamp) { ... }
  ```

`GlobalExceptionHandler` (annotated `@RestControllerAdvice`, meaning it handles
exceptions across *all* controllers in one place) now returns:

| Exception | HTTP status | Notes |
|---|---|---|
| `ResourceNotFoundException` | **404 Not Found** | was 500 before |
| `EmailAlreadyExistsException` | **409 Conflict** | was 400; 409 is the correct "resource already exists" code |
| `MethodArgumentNotValidException` (from `@Valid`) | **400 Bad Request** | returns a `field → message` map so the frontend can show per-field errors |

Why centralize error handling: without it, every controller would need
try/catch blocks and would translate errors inconsistently. `@RestControllerAdvice`
lets the services just `throw`, and one class decides how each exception type
becomes an HTTP response. Consistent, DRY, and the services stay focused on
business logic.

### Entities and Lombok (the bidirectional relationship)

Covered in [background](#2-concurrentmodificationexception-when-updating-managers).
The short version: on a bidirectional JPA association, never let Lombok's
`@Data` include the association collections in `equals`/`hashCode`/`toString`.

### `application.properties`

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5433/employee_db}
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:password}
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.jpa.show-sql=true
```

- **Externalized credentials** — `${DB_PASSWORD:password}` means "use the
  `DB_PASSWORD` environment variable, or fall back to `password` if it's not
  set." Secrets shouldn't be hard-coded in a file that goes into version
  control. The defaults keep local development working unchanged; in production
  you set real values via environment variables.
- **`spring.jpa.open-in-view=false`** — "Open Session In View" (OSIV) is a
  Spring default that keeps the database session open through the entire web
  request, including JSON rendering. It's convenient but a known anti-pattern:
  it can fire surprise queries during serialization and hold connections longer
  than needed. We can safely turn it off *because* all entity→DTO mapping now
  happens inside `@Transactional` service methods — by the time the controller
  returns a DTO, no lazy loading is left to do. This change and the
  `@Transactional` boundaries are a package deal.
- **`show-sql`** left on for now — handy while learning; turn it off in
  production (see discretionary list).

### `pom.xml`

Added `spring-boot-flyway` (the Flyway fix from the background section).

### New migration `V6__drop_unique_role_on_managers.sql`

```sql
ALTER TABLE managers DROP CONSTRAINT IF EXISTS managers_role_key;
```

`V4` created `managers.role` as `UNIQUE`, which means two managers could never
have the same role (no two "Team Lead"s). That's almost certainly unintended, so
V6 drops the constraint. Note we add a *new* migration rather than editing V4 —
**never edit a migration that has already run**, because Flyway stores a
checksum of each applied script and will fail if it changes. Forward-only
migrations are the rule.

---

## Left to your discretion

These are things I noticed but deliberately **did not** change, because they
depend on product decisions, involve data you'd need to inspect, or are
judgment calls I didn't want to make silently. Each is a good learning thread to
pull on.

### 1. `FlywayConfig` runs `flyway.repair()` on every startup

```java
@Bean
public FlywayMigrationStrategy cleanMigrateStrategy() {
    return flyway -> { flyway.repair(); flyway.migrate(); };
}
```

`repair()` rewrites Flyway's stored checksums. That's a handy escape hatch while
you're iterating and editing migrations locally — but on every boot it will
happily *mask* real migration drift (e.g. a migration that was altered after
being applied, which should normally raise a loud error). **In production this
can hide a corrupted migration history.** Recommendation: remove `repair()` once
your migrations stabilize, or gate this bean behind a dev-only profile
(`@Profile("dev")`). I left it because right now it's probably helping you while
you experiment.

### 2. `createEmployee` hard-codes `employee.setRole("Child")`

Every created employee gets the role `"Child"` regardless of input (and
`EmployeeCreateDTO` has no role field at all). If that's a deliberate part of
your domain model (employees are "children" of managers), fine. If not, it's a
latent bug. I preserved the existing behavior rather than guess at your intent —
this is a product decision only you can make.

### 3. Join table uses `BIGSERIAL` for its foreign keys

In `V5`, `employee_manager.employee_id` and `manager_id` are declared
`BIGSERIAL`. `BIGSERIAL` creates an auto-incrementing sequence — appropriate for
a primary key, but pointless (and slightly misleading) on **foreign-key**
columns, which should just be `BIGINT NOT NULL`. It works as-is, so I didn't add
a migration to change it, but it's worth cleaning up if you rebuild the schema.

### 4. `employees.role` is nullable in the DB but `NOT NULL` on the entity

`V3` added `role VARCHAR(20)` (nullable), but `Employee.role` is
`@Column(nullable = false)` with `@NotBlank`. `ddl-auto=validate` doesn't check
nullability, so this mismatch passes silently. Making the column `NOT NULL`
would require first backfilling any existing rows that have a null role — that's
data-dependent, so it's your call and needs a migration you write against your
actual data.

### 5. DTOs use Lombok `@Data` classes rather than Java `records`

Modern Java `record`s are the idiomatic choice for immutable DTOs — less
boilerplate, immutable by default. I kept your `@Data` class style for
consistency with the existing DTOs and to keep the diff reviewable. Converting
the DTOs to records is a nice, low-risk follow-up exercise if you want to learn
records.

### 6. Entities still use `@Data`

We excluded the association collections, which fixes the crash. But `@Data` on an
entity still generates `equals`/`hashCode` over *all the scalar fields*, which
is fragile for entities (their field values change over their lifetime, and
their identity should really be based on the database id). The textbook approach
is `@Getter @Setter` plus an explicit `equals`/`hashCode` based only on the id.
I left it because the current version is safe enough for this app and changing
it is more of a "deepen your understanding" task than a bug fix.

### 7. CORS origin is hard-coded on each controller

`@CrossOrigin(origins = "http://localhost:5173")` is repeated on both
controllers. For production you'd typically centralize this in a single CORS
configuration bean and read the allowed origin(s) from properties (so dev,
staging, and prod can differ without code changes). Left as-is because it works
for your current single-frontend setup.

---

*Generated as part of the production-readiness refactor. If anything here is
unclear, it's a great thing to dig into — each section maps to a real Spring
Boot concept worth knowing.*
