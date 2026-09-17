package au.edu.uow.csci318.messaging.application;

/** One-time v2 migration: enqueue authoritative snapshots from this context's own database. */
public interface SnapshotSource {
  String migrationKey();

  void enqueueSnapshots();
}
