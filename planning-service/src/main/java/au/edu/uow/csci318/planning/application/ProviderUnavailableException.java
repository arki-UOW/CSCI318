package au.edu.uow.csci318.planning.application;

/** A safe provider-facing message; raw transport details never become an API response. */
public class ProviderUnavailableException extends RuntimeException {
  public ProviderUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
