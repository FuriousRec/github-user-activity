import com.fasterxml.jackson.databind.JsonNode;

/** Pure formatting, separated from network access for deterministic tests. */
final class GithubEventFormatter {
        private GithubEventFormatter() {}

        // ANCHOR: Format GitHub Event
        static String format(JsonNode githubEvent) {

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
                                                .replaceFirst("^refs/heads/", "");

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

                                int pullRequestNumber = eventPayload.path("number").asInt(
                                                eventPayload.path("pull_request").path("number").asInt());
                                if ("closed".equals(pullRequestAction)
                                                && eventPayload.path("pull_request").path("merged").asBoolean(false)) {
                                        pullRequestAction = "merged";
                                }

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
