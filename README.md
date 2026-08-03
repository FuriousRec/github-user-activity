# GitHub User Activity CLI

A Java command-line tool that fetches a GitHub user's recent public activity and public repositories using the GitHub REST API.

This project was built as a backend/CLI practice project with Maven, Java's `HttpClient`, and Jackson for JSON parsing.

## Features

- Fetches recent public GitHub events for a username
- Lists public repositories with pagination
- Formats common event types like pushes, stars, forks, issues, pull requests, created refs, and deleted refs
- Handles missing users and GitHub API rate limits

## Tech Stack

- Java 21
- Maven
- GitHub REST API
- Jackson Databind

## Run

```bash
mvn compile
mvn exec:java "-Dexec.mainClass=Main" "-Dexec.args=<username>"
```

Example:

```bash
mvn exec:java "-Dexec.mainClass=Main" "-Dexec.args=FuriousRec"
```

## Example Output

```text
GitHub username: FuriousRec
Response status code: 200

Public repositories:
- Algorithms-And-Data-Structure
- imperial-dominion-bot
- qr-decomposition-java
Total public repositories: 3

Recent public activity is available.
Number of recent events: 4
2026-08-03T20:55:04Z | Pushed commits to FuriousRec/imperial-dominion-bot on branch main
```
