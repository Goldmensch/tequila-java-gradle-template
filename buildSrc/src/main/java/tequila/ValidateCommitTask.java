package tequila;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskExecutionException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ValidateCommitTask extends DefaultTask {

    public static final Collection<String> TEMPLATE_PROJECT_SCOPES = List.of("gradle", "readme");

    public static final String TEMPLATE_PROJECT_ORIGIN = "Goldmensch/tequila-java-gradle-template.git";

    public static final Pattern HEADER_PATTERN =
            Pattern.compile("^(?<type>\\w+?)(?:\\((?<scope>\\w+?)\\))?!?: (?<message>\\S[^.]*)");

    private final Repository repository = new RepositoryBuilder()
            .setGitDir(new File(getProject().getRootDir(), ".git"))
            .readEnvironment()
            .build();

    private final Git git = new Git(repository);

    private final Collection<String> types = parseList(getProject().property("commit.types"));

    private final Collection<String> scopes = isTemplateRepository(repository)
            ? parseList(getProject().property("commit.scopes"))
            : TEMPLATE_PROJECT_SCOPES;

    public ValidateCommitTask() throws Exception {
    }

    @TaskAction
    public void validateMessage() throws Exception {
        var rootBranch = repository.resolve("origin/%s".formatted(rootBranch(repository.getBranch())));
        var latestRootCommit = commits(git, log -> log.setMaxCount(1).add(rootBranch)).findFirst().orElse(null);
        commits(git, log -> {if (latestRootCommit != null) log.not(latestRootCommit);})
                .map(this::validate)
                .filter(Objects::nonNull)
                .forEach(err -> getState().addFailure(new TaskExecutionException(this, new RuntimeException(err))));
    }

    private String validate(RevCommit commit) {
        var errors = Stream.concat(validateHeader(commit.getShortMessage()).stream(),
                        validateFooter(commit.getFullMessage()).stream())
                .collect(Collectors.joining("\n"));

        return errors.isEmpty()
                ? null
                : "Errors for commit: %s @%s \n %s".formatted(commit.getShortMessage(),
                commit.getId().abbreviate(7).name(),
                errors);
    }

    private Collection<String> validateFooter(String fullMessage) {
        // TODO: 14.11.22 Implement validation for footer
        return List.of();
    }

    private Collection<String> validateHeader(String header) {
        var matcher = HEADER_PATTERN.matcher(header);
        var errors = new ArrayList<String>();
        if (matcher.matches()) {
            var type = matcher.group("type");
            var scope = matcher.group("scope");
            if (!types.contains(type)) {
                errors.add("-> Unkown type '%s'".formatted());
            }
            if (scope != null && !scopes.contains(scope)) {
                errors.add("-> Unkown scope '%s'".formatted(scope));
            }
        } else {
            errors.add("Commit header (short commit message) violates conventional commits format.");
        }
        return errors;
    }

    private boolean isTemplateRepository(Repository repository) {
        return repository.getConfig().getString("remote", "origin", "url")
                .contains(TEMPLATE_PROJECT_ORIGIN);
    }

    // assume validated branch naming
    private String rootBranch(String branch) {
        var branches = branch.split("/");
        return switch (branches.length) {
            case 1, 2 -> "main";
            case 3 -> branches[1];
            default -> throw new IllegalArgumentException("Branch doens't follow branch naming conventions.");

        };
    }

    private Collection<String> parseList(Object source) {
        return Arrays.stream(((String) source).split(",")).map(String::trim)
                .toList();
    }

    private Stream<RevCommit> commits(Git git, ThrowingConsumer<LogCommand> builder) throws Exception {
        var log = git.log();
        builder.apply(log);
        return StreamSupport.stream(log.call().spliterator(), false);
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void apply(T some) throws Exception;
    }
}
