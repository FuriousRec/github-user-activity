import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.time.Duration;

/** Shared validation and request policy for public GitHub API calls. */
final class GithubRequests {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private GithubRequests() {}

    static String username(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Please provide a GitHub username.");
        }
        String value = input.trim();
        if (!value.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?")
                || value.contains("--")) {
            throw new IllegalArgumentException(
                    "Use a GitHub username of 1–39 letters, digits or single hyphens; "
                    + "it must start and end with a letter or digit.");
        }
        return value;
    }

    static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "github-user-activity-cli")
                .GET()
                .build();
    }

    static boolean isRateLimited(int statusCode) {
        return statusCode == 403 || statusCode == 429;
    }

    static String rateLimitMessage(HttpHeaders headers) {
        Long retryAfterSeconds = nonNegativeLong(headers.firstValue("Retry-After").orElse(null));
        if (retryAfterSeconds != null) {
            return "GitHub API rate limit reached. Please try again in "
                    + retryAfterSeconds + (retryAfterSeconds == 1 ? " second." : " seconds.");
        }

        Long resetEpochSeconds =
                nonNegativeLong(headers.firstValue("X-RateLimit-Reset").orElse(null));
        if (resetEpochSeconds != null) {
            return "GitHub API rate limit reached. Please try again after "
                    + Instant.ofEpochSecond(resetEpochSeconds) + ".";
        }

        return "GitHub API rate limit reached. Please try again later.";
    }

    private static Long nonNegativeLong(String value) {
        if (value == null || !value.matches("[0-9]+")) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
