# Proposal-to-implementation reconciliation

Date: 11 October 2026. These are implementation decisions documented during the user's requested completion work. They do not assert historical coordinator approval or fabricate earlier team votes.

| Original proposal | Implemented decision | Reason and requirement preservation |
|---|---|---|
| Four academic bounded contexts | Four academic contexts plus Account | Core boundaries remain intact; Account owns authentication/profile data for the expanded product scope |
| Authentication/frontend excluded | Account and lightweight browser client included | Existing expanded scope is retained; neither substitutes for required REST, streaming or agentic stories |
| Planning reads weekly target through Subject REST and caches it | Subject target events merge into the progress topology | Targets update on changes and remain queryable during Subject outages. RT-02's target comparison is preserved; exact transport differs from the original plan |
| Long aggregate IDs; UUID event IDs; v1 example | UUID aggregates/events and v2 owner/revision envelopes | Supports ownership isolation, duplicate suppression and stale-update rejection; versioned decoder rejects unsupported events |
| Dedicated update/filter endpoint examples | PATCH resources and query filters | Same user capabilities through current contracts; Postman and use cases use executable current paths |
| Proposed SubjectCode/Deadline/etc. value-object examples | Actual WeeklyAvailability and WeeklyTimeSlots values plus guarded aggregate fields | Document actual invariant ownership instead of claiming classes that were not implemented |
| Generate/regenerate seven-day plan | Exact start through start+6, including valid empty versions | Restores the proposal horizon and completion transition; never promises scheduling through every future deadline |
| Save new plan versions | Database owner lock, unique owner/version index, transactional calendar replacement | Concurrent commands cannot silently create ambiguous history; legacy duplicate owners are renumbered chronologically without changing IDs/content |

This reconciliation is the current repository technical specification. The original proposal remains historical context. If the professor requires literal REST-based target acquisition rather than equivalent event-driven target delivery, the team must clarify that interpretation; adding a REST fallback would weaken the demonstrated upstream-offline query property. No external approval is inferred here.

Additional libraries already used for validation, BCrypt, PDF/DOCX extraction, JSON, OCR and testing should be listed by the team when seeking/recording the professor's dependency approval. This file records design rationale, not that approval.
