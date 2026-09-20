# Engineering Rules

## 1. General Rule

Build the smallest reliable implementation that satisfies the requirements.

Do not introduce complexity without justification.

---

# 2. Architecture

The agent must determine the final architecture after analyzing the requirements.

Before implementation, document:

* Major modules
* Responsibilities
* Data flow
* Persistence strategy
* State management
* Background execution
* Notification architecture
* Backup/restore architecture
* Testing architecture

The architecture should remain modular enough to maintain but should not become unnecessarily enterprise-like.

---

# 3. Source of Truth

There should be a clearly defined source of truth for persistent application data.

Avoid maintaining multiple independent copies of the same state.

Derived values should generally be calculated from authoritative data unless there is a demonstrated performance reason to persist them.

---

# 4. Database

Use a local database appropriate for Android structured data.

The agent must determine:

* Tables/entities
* Relationships
* Primary keys
* Indexes
* Constraints
* Migration strategy
* Deletion behavior
* Historical data strategy

The database must be designed for years of use.

Do not prematurely optimize the schema.

Every index should have a reason.

---

# 5. Database Queries

Queries should retrieve only the information required.

Avoid:

* Loading entire historical datasets unnecessarily
* Repeated identical queries
* N+1 query patterns
* Performing expensive aggregation repeatedly when unnecessary

Large historical datasets should be handled efficiently.

---

# 6. UI

UI should follow Android's recommended lifecycle-aware practices.

Screens should not retain unnecessary references to:

* Activities
* Contexts
* Views
* Large datasets

UI state should be scoped appropriately.

---

# 7. Concurrency

Use structured concurrency.

Avoid:

* Unmanaged threads
* Infinite loops
* Arbitrary background threads
* Blocking the main/UI thread
* Fire-and-forget asynchronous operations without lifecycle consideration

Long-running operations should be cancellable where practical.

---

# 8. Background Work

Background work must have a legitimate reason.

Do NOT implement:

* Polling loops
* Permanent services
* Frequent timers
* Continuous database checks

Use appropriate Android scheduling mechanisms.

The exact mechanism should be selected based on the task's requirements.

---

# 9. Notifications

Notifications should be scheduled rather than generated through continuous polling.

Notification scheduling must account for:

* Device reboot
* App updates
* Time changes
* Time zones
* Daylight-saving changes where applicable
* Disabled notifications
* Habit schedule changes
* Deleted/paused habits

Avoid creating duplicate alarms.

---

# 10. Battery

The following are prohibited unless explicitly justified:

* Permanent foreground services
* Continuous polling
* Frequent wakeups
* Repeated unnecessary synchronization
* Background network requests
* Unbounded WorkManager jobs
* Rebuilding all statistics on every small interaction

---

# 11. Memory

Avoid holding large historical datasets in memory.

Prefer:

* Pagination
* Lazy loading
* Targeted queries
* Streaming where appropriate
* Lifecycle-aware state

Do not cache data without a demonstrated benefit.

---

# 12. Storage

Avoid unnecessary duplication.

Do not store:

* Temporary data permanently
* Repeated derived information
* Excessive logs
* Unbounded debug output
* Large media files unless explicitly required

Temporary files must be cleaned up when safe.

---

# 13. Dependencies

Before adding a dependency, determine:

1. Is it actually necessary?
2. Does Android/Kotlin/Jetpack already provide the functionality?
3. What is its runtime/build cost?
4. What is its license?
5. Does it introduce telemetry or network behavior?
6. Is it actively maintained?
7. Can the same functionality reasonably be implemented without it?

Do not add dependencies merely for convenience.

---

# 14. Network

The core application must not require network access.

Do not introduce networking libraries or remote services unless explicitly approved.

If a future feature requires networking, it must be treated as a separate architectural decision.

---

# 15. Permissions

Request the minimum permissions required.

Do not request permissions merely because they might be useful later.

Every permission should have an explicit product justification.

---

# 16. Security

Protect local user data appropriately.

Consider:

* Backup exposure
* Exported files
* Database access
* Intents
* Content providers
* Logs
* Screenshots where relevant
* Sensitive data exposure

Never log private user data unnecessarily.

---

# 17. Backup and Restore

Backup must be treated as a data-integrity feature.

Before implementing restore:

* Validate input
* Handle incompatible versions
* Protect existing data
* Define failure behavior
* Test corrupted/incomplete backups
* Test large backups

Never assume imported data is valid.

---

# 18. Testing

Every meaningful feature should have appropriate tests.

At minimum consider:

* Unit tests
* Database tests
* Scheduling tests
* Date/time edge cases
* UI tests where useful
* Backup/restore tests

Important edge cases must be tested.

Examples:

* Empty database
* First day of month
* Last day of month
* Leap year
* Year boundary
* Time-zone change
* Reboot
* Deleted habit
* Paused habit
* Habit schedule change
* Large history
* Duplicate notification scheduling
* Corrupted backup

---

# 19. Verification

A feature is not complete when code has been written.

A feature is complete when:

* Implementation exists
* Static analysis passes
* Relevant tests pass
* Application builds
* Relevant behavior is verified
* No obvious regressions are introduced

The agent must report verification results.

---

# 20. Resource Management

This is mandatory.

When performing development or testing tasks, the agent must be conscious of system resources.

### Process handling

When starting a process:

* Know why it is being started.
* Prefer reusing an existing suitable process.
* Track processes started for the task.
* Clean up processes started specifically for the task when they are no longer required.

This applies to:

* Java
* Gradle
* Kotlin compiler processes
* ADB processes
* Android emulators
* Development servers
* Test servers
* Python processes
* Node processes
* Other temporary tooling

### Critical safety rule

Never broadly kill processes based only on process name.

For example, do not use:

```text
killall java
```

or equivalent broad termination commands.

A Java process may belong to another application or development task.

Prefer targeted cleanup using known process IDs or the tool that started the process.

Never terminate unrelated user processes.

---

# 21. Temporary Resources

Clean up resources created during development where safe:

* Temporary files
* Temporary directories
* Test databases
* Temporary servers
* Generated debug artifacts
* Temporary logs

Do not delete user data or unrelated project files.

---

# 22. Build Efficiency

Prefer incremental builds.

Do not repeatedly perform expensive clean builds unless necessary.

Use targeted tests during development.

Run the full verification suite at meaningful milestones.

Do not perform repeated expensive operations without a reason.

---

# 23. Git

Git must be used throughout development.

Before substantial changes:

* Inspect current status.
* Understand existing modifications.
* Do not overwrite unrelated user changes.

After completing a meaningful unit of work:

* Verify changes.
* Run appropriate tests.
* Commit the completed work.

Avoid enormous uncommitted changes spanning multiple unrelated features.

Never destroy user work to resolve a conflict without explicit approval.

---

# 24. Existing User Changes

The agent must assume that uncommitted changes may be intentional.

Before modifying files with existing changes:

* Inspect them.
* Determine whether they are relevant.
* Preserve them.

Never blindly reset, checkout, clean, or overwrite the repository.

---

# 25. Error Handling

Errors should fail predictably.

Do not silently ignore:

* Database failures
* Backup failures
* Notification scheduling failures
* Invalid input
* Migration failures

User-facing errors should be understandable.

Developer diagnostics should contain enough information to debug the issue without logging sensitive user data.

---

# 26. Logging

Use logging only where useful.

Do not continuously log:

* Habit history
* Goal contents
* Personal notes
* Sensitive user information

Debug logging should be appropriately controlled for release builds.

---

# 27. Performance

Performance should be measured rather than guessed.

Pay particular attention to:

* Startup time
* Today screen rendering
* Database query latency
* Scrolling
* Statistics generation
* Backup/restore
* Widget updates
* Notification scheduling

Do not sacrifice correctness for speculative performance gains.

---

# 28. Release Builds

Before release:

* Remove debug behavior
* Verify permissions
* Verify exported components
* Verify backup behavior
* Verify notification behavior
* Verify database migrations
* Verify ProGuard/R8 configuration if applicable
* Test on physical hardware
* Confirm offline operation
* Check APK/AAB size
* Check obvious battery/resource behavior

---

# 29. Agent Behavior

The agent should:

* Analyze before implementing
* Ask when requirements genuinely conflict
* Make reasonable implementation decisions autonomously
* Explain significant architectural decisions
* Avoid unnecessary rewrites
* Avoid unnecessary dependencies
* Preserve existing working functionality
* Verify its work
* Clean up resources it created
* Maintain documentation
* Keep commits focused

The agent should NOT:

* Rewrite the project without justification
* Add cloud infrastructure
* Add AI
* Add telemetry
* Add advertising
* Add unnecessary dependencies
* Create permanent background services
* Delete user data
* Reset Git state destructively
* Claim a feature is complete without verification
