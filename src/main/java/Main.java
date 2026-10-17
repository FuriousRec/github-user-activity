import java.io.IOException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;//To connect with GITHUB
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Main {
        private static final int GITHUB_PAGE_SIZE = 100;

        // ANCHOR: Program Start
        public static void main(String[] commandLineArguments) {

                // ANCHOR: Username Validation
                if (commandLineArguments.length != 1
                                || commandLineArguments[0].isBlank()) {

                        printUsageInstructions();
                        return;
                }

                // ANCHOR: Username Input
                try {
                        String githubUsername = GithubRequests.username(commandLineArguments[0]);
                        String githubToken = GithubRequests.token(
                                        System.getenv("GITHUB_TOKEN"));

                        // ANCHOR: GitHub API URL
                        // We are constructing the GITHUB URL FOR the users page
                        String githubActivityUrl = "https://api.github.com/users/"
                                        + githubUsername
                                        + "/events";
                        String githubRepositoriesUrl = "https://api.github.com/users/"
                                        + githubUsername
                                        + "/repos";
                        HttpClient githubHttpClient = GithubRequests.client();// the messenger

                        // ANCHOR: HTTP Request
                        HttpRequest githubActivityRequest = GithubRequests.get(
                                        githubActivityUrl,
                                        githubToken);

                        // ANCHOR: HTTP Response
                        HttpResponse<String> githubActivityResponse = githubHttpClient.send(
                                        githubActivityRequest,
                                        HttpResponse.BodyHandlers.ofString());

                        // ANCHOR: Console Output
                        System.out.println("GitHub username: " + githubUsername);
                        System.out.println("Request URL: " + githubActivityRequest.uri());
                        System.out.println("Request method: " + githubActivityRequest.method());
                        int responseStatusCode = githubActivityResponse.statusCode();

                        System.out.println("Response status code: " + responseStatusCode);

                        if (responseStatusCode == 200) {
                                displayAllRepositories(
                                                githubHttpClient,
                                                githubRepositoriesUrl,
                                                githubUsername,
                                                githubToken);

                                String githubActivityJson = githubActivityResponse.body();

                                if (githubActivityJson.trim().equals("[]")) {
                                        System.out.println("No recent public activity found.");
                                        return;
                                } else {
                                        System.out.println("Recent public activity is available.");
                                }
                                ObjectMapper jsonObjectMapper = new ObjectMapper();

                                JsonNode githubEvents = jsonObjectMapper.readTree(githubActivityJson);

                                System.out.println(
                                                "Number of recent events: " + githubEvents.size());
                                // ANCHOR: Display Every GitHub Event
                                for (JsonNode githubEvent : githubEvents) {

                                        String eventCreatedAt = githubEvent
                                                        .path("created_at")
                                                        .asText("Unknown time");

                                        String readableEventDescription = GithubEventFormatter.format(githubEvent);

                                        System.out.println(
                                                        eventCreatedAt
                                                                        + " | "
                                                                        + readableEventDescription);
                                }
                        }

                        else if (responseStatusCode == 401 && githubToken != null) {
                                System.out.println(
                                                "Error: GitHub rejected GITHUB_TOKEN. "
                                                                + "Check or remove the environment variable.");
                        }

                        else if (responseStatusCode == 404) {
                                System.out.println(
                                                "Error: GitHub user \""
                                                                + githubUsername
                                                                + "\" was not found.");
                        }

                        else if (GithubRequests.isRateLimited(responseStatusCode)) {
                                System.out.println("Error: " + GithubRequests.rateLimitMessage(
                                                githubActivityResponse.headers()));
                        }

                        else {
                                System.out.println(
                                                "Error: GitHub request failed with status code "
                                                                + responseStatusCode
                                                                + ".");
                        }
                } catch (IllegalArgumentException exception) {
                        System.out.println("Error: " + exception.getMessage());
                } catch (HttpTimeoutException exception) {
                        System.out.println("Error: GitHub request timed out. Please try again later.");
                } catch (IOException exception) {
                        System.out.println(
                                        "Error: Could not process the GitHub response.");
                        System.out.println("Details: " + exception.getMessage());
                } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        System.out.println("Error: The GitHub request was interrupted.");
                }
        }

        // ANCHOR: Display All GitHub Repositories
        private static void displayAllRepositories(
                        HttpClient githubHttpClient,
                        String githubRepositoriesUrl,
                        String githubUsername,
                        String githubToken)
                        throws IOException, InterruptedException {

                ObjectMapper jsonObjectMapper = new ObjectMapper();
                int currentPage = 1;
                int repositoryCount = 0;

                System.out.println();
                System.out.println("Public repositories:");

                while (true) {
                        String pagedRepositoriesUrl = githubRepositoriesUrl
                                        + "?per_page="
                                        + GITHUB_PAGE_SIZE
                                        + "&page="
                                        + currentPage;

                        HttpRequest githubRepositoriesRequest = GithubRequests.get(
                                        pagedRepositoriesUrl,
                                        githubToken);

                        HttpResponse<String> githubRepositoriesResponse = githubHttpClient.send(
                                        githubRepositoriesRequest,
                                        HttpResponse.BodyHandlers.ofString());

                        if (githubRepositoriesResponse.statusCode() == 401
                                        && githubToken != null) {
                                System.out.println(
                                                "Could not load repositories: GitHub rejected GITHUB_TOKEN. "
                                                                + "Check or remove the environment variable.");
                                return;
                        }

                        if (GithubRequests.isRateLimited(
                                        githubRepositoriesResponse.statusCode())) {
                                System.out.println(
                                                "Could not load repositories: "
                                                                + GithubRequests.rateLimitMessage(
                                                                                githubRepositoriesResponse.headers()));
                                return;
                        }

                        if (githubRepositoriesResponse.statusCode() != 200) {
                                System.out.println(
                                                "Could not load repositories for "
                                                                + githubUsername
                                                                + ". Status code: "
                                                                + githubRepositoriesResponse.statusCode()
                                                                + ".");
                                return;
                        }

                        JsonNode githubRepositories = jsonObjectMapper.readTree(
                                        githubRepositoriesResponse.body());

                        if (githubRepositories.isEmpty()) {
                                break;
                        }

                        // ANCHOR: Display Every GitHub Repository
                        for (JsonNode githubRepository : githubRepositories) {

                                String repositoryName = githubRepository
                                                .path("name")
                                                .asText("Unknown repository");

                                System.out.println("- " + repositoryName);
                                repositoryCount++;
                        }

                        if (githubRepositories.size() < GITHUB_PAGE_SIZE) {
                                break;
                        }

                        currentPage++;
                }

                if (repositoryCount == 0) {
                        System.out.println("No public repositories found.");
                } else {
                        System.out.println("Total public repositories: " + repositoryCount);
                }
        }

        // ANCHOR: Usage Instructions
        private static void printUsageInstructions() {
                System.out.println("Error: Please provide exactly one GitHub username.");
                System.out.println(
                                "Usage: mvn exec:java "
                                                + "\"-Dexec.mainClass=Main\" "
                                                + "\"-Dexec.args=<username>\"");
        }

}
