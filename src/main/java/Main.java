import java.io.IOException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;//To connect with GITHUB
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Main {
        private static final int GITHUB_PAGE_SIZE = 100;

        // ANCHOR: Program Start
        public static void main(String[] commandLineArguments) {

                // ANCHOR: Username Validation
                if (commandLineArguments.length == 0
                                || commandLineArguments[0].isBlank()) {

                        printUsageInstructions();
                        return;
                }

                // ANCHOR: Username Input
                try {
                        String githubUsername = commandLineArguments[0].trim();

                        // ANCHOR: GitHub API URL
                        // We are constructing the GITHUB URL FOR the users page
                        String githubActivityUrl = "https://api.github.com/users/"
                                        + githubUsername
                                        + "/events";
                        String githubRepositoriesUrl = "https://api.github.com/users/"
                                        + githubUsername
                                        + "/repos";
                        HttpClient githubHttpClient = HttpClient.newHttpClient();// the messenger

                        // ANCHOR: HTTP Request
                        HttpRequest githubActivityRequest = HttpRequest.newBuilder()
                                        .uri(URI.create(githubActivityUrl))
                                        .header("Accept", "application/vnd.github+json")
                                        .header("User-Agent", "github-user-activity-cli")
                                        .GET()
                                        .build();

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
                                                githubUsername);

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

                                        String readableEventDescription = formatGithubEvent(githubEvent);

                                        System.out.println(
                                                        eventCreatedAt
                                                                        + " | "
                                                                        + readableEventDescription);
                                }
                        }

                        else if (responseStatusCode == 404) {
                                System.out.println(
                                                "Error: GitHub user \""
                                                                + githubUsername
                                                                + "\" was not found.");
                        }

                        else if (responseStatusCode == 403) {
                                System.out.println("Error: GitHub API rate limit reached. Please try again later.");
                        }

                        else {
                                System.out.println(
                                                "Error: GitHub request failed with status code "
                                                                + responseStatusCode
                                                                + ".");
                        }
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
                        String githubUsername)
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

                        HttpRequest githubRepositoriesRequest = HttpRequest.newBuilder()
                                        .uri(URI.create(pagedRepositoriesUrl))
                                        .header("Accept", "application/vnd.github+json")
                                        .header("User-Agent", "github-user-activity-cli")
                                        .GET()
                                        .build();

                        HttpResponse<String> githubRepositoriesResponse = githubHttpClient.send(
                                        githubRepositoriesRequest,
                                        HttpResponse.BodyHandlers.ofString());

                        if (githubRepositoriesResponse.statusCode() == 403) {
                                System.out.println(
                                                "Could not load repositories because the GitHub API rate limit was reached.");
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
                System.out.println("Error: Please provide a GitHub username.");
                System.out.println(
                                "Usage: mvn exec:java "
                                                + "\"-Dexec.mainClass=Main\" "
                                                + "\"-Dexec.args=<username>\"");
        }

        // ANCHOR: Format GitHub Event
        private static String formatGithubEvent(JsonNode githubEvent) {

                String eventType = githubEvent
                                .path("type")
                                .asText("UnknownEvent");

                String repositoryName = githubEvent
                                .path("repo")
                                .path("name")
                                .asText("Unknown repository");

                JsonNode eventPayload = githubEvent.path("payload");

                return switch (eventType) {

                        case "PushEvent" -> {
                                String pushedReference = eventPayload
                                                .path("ref")
                                                .asText("Unknown branch");

                                String branchName = pushedReference
                                                .replace("refs/heads/", "");

                                yield "Pushed commits to "
                                                + repositoryName
                                                + " on branch "
                                                + branchName;
                        }

                        case "WatchEvent" ->
                                "Starred " + repositoryName;

                        case "ForkEvent" -> {
                                String forkedRepositoryName = eventPayload
                                                .path("forkee")
                                                .path("full_name")
                                                .asText(repositoryName);

                                yield "Forked "
                                                + repositoryName
                                                + " as "
                                                + forkedRepositoryName;
                        }

                        case "CreateEvent" -> {
                                String referenceType = eventPayload
                                                .path("ref_type")
                                                .asText("item");

                                String referenceName = eventPayload
                                                .path("ref")
                                                .asText("");

                                if (referenceType.equals("repository")) {
                                        yield "Created repository " + repositoryName;
                                }

                                if (referenceName.isBlank()) {
                                        yield "Created a "
                                                        + referenceType
                                                        + " in "
                                                        + repositoryName;
                                }

                                yield "Created "
                                                + referenceType
                                                + " \""
                                                + referenceName
                                                + "\" in "
                                                + repositoryName;
                        }

                        case "IssuesEvent" -> {
                                String issueAction = eventPayload
                                                .path("action")
                                                .asText("updated");

                                int issueNumber = eventPayload
                                                .path("issue")
                                                .path("number")
                                                .asInt();

                                yield capitalizeFirstLetter(issueAction)
                                                + " issue #"
                                                + issueNumber
                                                + " in "
                                                + repositoryName;
                        }

                        case "PullRequestEvent" -> {
                                String pullRequestAction = eventPayload
                                                .path("action")
                                                .asText("updated");

                                int pullRequestNumber = eventPayload
                                                .path("pull_request")
                                                .path("number")
                                                .asInt();

                                yield capitalizeFirstLetter(pullRequestAction)
                                                + " pull request #"
                                                + pullRequestNumber
                                                + " in "
                                                + repositoryName;
                        }

                        case "DeleteEvent" -> {
                                String deletedReferenceType = eventPayload
                                                .path("ref_type")
                                                .asText("item");

                                String deletedReferenceName = eventPayload
                                                .path("ref")
                                                .asText("");

                                yield "Deleted "
                                                + deletedReferenceType
                                                + " \""
                                                + deletedReferenceName
                                                + "\" from "
                                                + repositoryName;
                        }

                        default ->
                                "Performed "
                                                + eventType
                                                + " in "
                                                + repositoryName;
                };
        }

        // ANCHOR: Capitalize Text
        private static String capitalizeFirstLetter(String text) {

                if (text == null || text.isBlank()) {
                        return "Updated";
                }

                return Character.toUpperCase(text.charAt(0))
                                + text.substring(1);
        }
}
