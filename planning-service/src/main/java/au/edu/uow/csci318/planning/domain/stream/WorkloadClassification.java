package au.edu.uow.csci318.planning.domain.stream;

/** Domain policy separated from Kafka, HTTP, storage and presentation. */
public final class WorkloadClassification {
  private WorkloadClassification() {}

  public static String classify(int dueSevenDays, int estimatedMinutes) {
    return dueSevenDays >= 3 || estimatedMinutes > 1200
        ? "HIGH"
        : dueSevenDays > 0 || estimatedMinutes > 480 ? "MEDIUM" : "LOW";
  }

  public static String progress(int studied, int target) {
    if (studied == 0) return "NO_ACTIVITY";
    if (studied >= target) return "TARGET_REACHED";
    return studied * 2 >= target ? "ON_TRACK" : "BEHIND_TARGET";
  }
}
