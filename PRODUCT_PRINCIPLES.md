# Product Principles

These principles are more important than individual features.

## 1. Local Means Local

User data belongs on the user's device.

The default architecture must not require:

* Backend servers
* Cloud databases
* Accounts
* Internet access
* Remote APIs

If a feature can be implemented locally, prefer the local implementation.

---

## 2. Offline Is a Requirement, Not a Mode

The application should not merely "support offline mode."

It should be designed so that offline operation is the normal state.

Airplane mode should not fundamentally change the application's functionality.

---

## 3. Privacy Over Convenience

Do not collect data merely because collecting it is technically easy.

Do not add telemetry, analytics, advertising SDKs, or tracking without explicit product approval.

Minimize permissions.

---

## 4. Simplicity Beats Feature Count

A feature should exist because it solves a real problem.

Do not add functionality simply because competing applications have it.

A smaller application that users understand is preferable to a larger application containing features nobody needs.

---

## 5. Battery Is a Product Feature

The application should spend as little energy as reasonably possible.

Prefer:

* Event-driven operations
* Scheduled work
* Efficient database queries
* Batched operations
* Android lifecycle-aware behavior

Avoid:

* Polling
* Continuous background services
* Frequent wakeups
* Repeated unnecessary database queries
* Constant recalculation of historical data

---

## 6. Storage Should Scale Predictably

Historical data should remain useful for years without causing uncontrolled storage growth.

Prefer normalized structured data.

Avoid duplicating information.

Do not store derived information unnecessarily.

---

## 7. History Is Truth

Do not rewrite history to make statistics look better.

If the user missed a habit on September 10, September 10 should continue to represent that reality.

Allow users to explicitly correct mistakes, but never silently rewrite historical records.

---

## 8. Don't Punish Users

The product should help users understand consistency rather than make them feel guilty for missing a day.

Streaks are useful measurements, not moral judgments.

Completion percentages, averages, trends, and recovery are often more informative than streaks alone.

---

## 9. Today Comes First

The user should not need to navigate through statistics and configuration screens to figure out what they need to do today.

The Today experience is the heart of the product.

---

## 10. No Unnecessary Abstraction

Use abstractions when they solve real problems.

Do not create:

* Generic frameworks
* Excessive interfaces
* Five-layer abstractions for simple operations
* Custom infrastructure when Android already provides it

The architecture should be understandable by one developer.

---

## 11. Prefer Platform Capabilities

Before adding a third-party library, check whether Android/Kotlin/Jetpack already provides the required capability.

Every dependency introduces:

* Maintenance cost
* Security considerations
* Build complexity
* Potential performance impact
* License considerations

---

## 12. Measure Before Optimizing

Do not perform speculative micro-optimizations.

First make the application correct.

Then measure.

Then optimize actual bottlenecks.

---

## 13. User Experience Over Technical Cleverness

The user does not care how elegant the repository pattern is.

They care that:

* The app opens quickly.
* Their data is safe.
* Tapping a habit feels immediate.
* Notifications work.
* The UI is understandable.
* Nothing unexpectedly disappears.

Optimize for the actual experience.

---

## 14. No Feature Without a Cost

Every feature has costs in:

* Code
* Storage
* Memory
* Battery
* UI complexity
* Testing
* Maintenance
* Potential bugs

Those costs should be considered before implementation.

---

## 15. Build for the Long Term

The application should still be understandable and usable years after its initial development.

Prefer boring, reliable technology over fashionable technology when both solve the problem equally well.
