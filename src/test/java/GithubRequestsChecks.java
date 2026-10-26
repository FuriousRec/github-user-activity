import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

/** Offline regression checks; throws on failure even without Java -ea. */
public final class GithubRequestsChecks {
    private static int checks;

    public static void main(String[] args) {
        for (String valid : new String[] {"a", "FuriousRec", "user-name", "a1", "a".repeat(39)}) {
            equal(valid, GithubRequests.username(valid));
        }
        equal("FuriousRec", GithubRequests.username("  FuriousRec  "));
        for (String invalid : new String[] {null, "", " ", "-user", "user-", "a--b",
                "a/b", "a?x=y", "a#fragment", "a b", "a_b", "ümlaut", "a".repeat(40)}) {
            rejects(invalid);
        }
        equal(GithubRequests.CONNECT_TIMEOUT, GithubRequests.client().connectTimeout().orElseThrow());
        for (String url : new String[] {
                "https://api.github.com/users/FuriousRec/events",
                "https://api.github.com/users/FuriousRec/repos?per_page=100&page=2"}) {
            HttpRequest request = GithubRequests.get(url);
            equal(url, request.uri().toString());
            equal("GET", request.method());
            equal(GithubRequests.REQUEST_TIMEOUT, request.timeout().orElseThrow());
            equal("application/vnd.github+json", request.headers().firstValue("Accept").orElseThrow());
            equal("2022-11-28",
                    request.headers().firstValue("X-GitHub-Api-Version").orElseThrow());
            equal("github-user-activity-cli", request.headers().firstValue("User-Agent").orElseThrow());
            equal(false, request.headers().firstValue("Authorization").isPresent());
        }
        for (String invalidUrl : new String[] {
                "http://api.github.com/users/FuriousRec/events",
                "https://api.github.com.evil.example/users/FuriousRec/events",
                "https://token@api.github.com/users/FuriousRec/events",
                "https://api.github.com:444/users/FuriousRec/events",
                "https://api.github.com/users/FuriousRec/events#fragment",
                "not a url"}) {
            rejectsUrl(invalidUrl);
        }
        HttpRequest explicitHttpsPort = GithubRequests.get(
                "https://api.github.com:443/users/FuriousRec/events");
        equal("api.github.com", explicitHttpsPort.uri().getHost());

        HttpHeaders noLink = HttpHeaders.of(Map.of(), (key, item) -> true);
        check(GithubRequests.nextPage(noLink) == null, "missing Link has no next page");
        equal(
                "https://api.github.com/users/FuriousRec/repos?per_page=100&page=2",
                GithubRequests.nextPage(headers(
                        "Link",
                        "<https://api.github.com/users/FuriousRec/repos?per_page=100&page=1>; rel=\"prev\", "
                                + "<https://api.github.com/users/FuriousRec/repos?per_page=100&page=2>; rel=\"next\", "
                                + "<https://api.github.com/users/FuriousRec/repos?per_page=100&page=4>; rel=\"last\"")));
        check(GithubRequests.nextPage(headers("Link", "malformed")) == null,
                "malformed Link is ignored");
        rejectsPaginationLink(
                "<https://evil.example/repos?page=2>; rel=\"next\"");
        rejectsPaginationLink(
                "<http://api.github.com/repos?page=2>; rel=\"next\"");

        HttpRequest authenticated = GithubRequests.get(
                "https://api.github.com/users/FuriousRec/events",
                "  test-token_123  ");
        equal("Bearer test-token_123",
                authenticated.headers().firstValue("Authorization").orElseThrow());
        check(GithubRequests.token(null) == null, "null token stays unauthenticated");
        check(GithubRequests.token("") == null, "empty token stays unauthenticated");
        check(GithubRequests.token("   ") == null, "blank token stays unauthenticated");
        for (String invalidToken : new String[] {
                "token with spaces", "token\nvalue", "token\tvalue", "x".repeat(256)}) {
            rejectsToken(invalidToken);
        }
        HttpHeaders noHeaders = HttpHeaders.of(Map.of(), (key, item) -> true);
        equal(true, GithubRequests.isRateLimited(429, noHeaders));
        equal(true, GithubRequests.isRateLimited(
                403, headers("X-RateLimit-Remaining", "0")));
        equal(true, GithubRequests.isRateLimited(403, headers("Retry-After", "60")));
        equal(false, GithubRequests.isRateLimited(
                403, headers("X-RateLimit-Remaining", "42")));
        equal(false, GithubRequests.isRateLimited(403, noHeaders));
        equal(false, GithubRequests.isRateLimited(401, noHeaders));
        equal("GitHub API rate limit reached. Please try again in 1 second.",
                GithubRequests.rateLimitMessage(headers("Retry-After", "1")));
        equal("GitHub API rate limit reached. Please try again in 120 seconds.",
                GithubRequests.rateLimitMessage(headers("Retry-After", "120")));
        equal("GitHub API rate limit reached. Please try again after 2023-11-14T22:13:20Z.",
                GithubRequests.rateLimitMessage(headers("X-RateLimit-Reset", "1700000000")));
        equal("GitHub API rate limit reached. Please try again later.",
                GithubRequests.rateLimitMessage(headers("Retry-After", "invalid")));
        equal("GitHub API rate limit reached. Please try again later.",
                GithubRequests.rateLimitMessage(headers("Retry-After", "9".repeat(30))));
        System.out.println("Passed " + checks + " offline checks.");
    }

    private static HttpHeaders headers(String name, String value) {
        return HttpHeaders.of(Map.of(name, List.of(value)), (key, item) -> true);
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
        checks++;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }

    private static void rejects(String input) {
        try {
            GithubRequests.username(input);
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted invalid username: " + input);
    }

    private static void rejectsUrl(String input) {
        try {
            GithubRequests.get(input, "test-token");
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted untrusted API URL: " + input);
    }

    private static void rejectsPaginationLink(String link) {
        try {
            GithubRequests.nextPage(headers("Link", link));
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted untrusted pagination link: " + link);
    }

    private static void rejectsToken(String input) {
        try {
            GithubRequests.token(input);
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted invalid token value");
    }
}
