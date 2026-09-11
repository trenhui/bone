This project uses AGENTS.md as the single source of truth for AI collaboration rules. See AGENTS.md and doc/architecture/ for structured constraints and specifications.

Key constraints:
- ORM: bone-metadata-sdk only (no MyBatis/JPA/Hibernate)
- Domain layer must not depend on Spring
- Unit test coverage >= 70%
- API must be wrapped in ApiResponse<T>
