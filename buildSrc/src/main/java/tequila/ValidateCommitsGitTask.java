package tequila;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.revwalk.RevCommit;
import org.gradle.api.tasks.TaskAction;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ValidateCommitsGitTask extends ValidateCommitMessageGitTask {
    public ValidateCommitsGitTask() throws Exception {
    }

    @TaskAction
    public void validateMessages() throws Exception {
        var rootBranch = repository.resolve("origin/%s".formatted(rootBranch(repository.getBranch())));
        var latestRootCommit = commits(git, log -> log.setMaxCount(1).add(rootBranch)).findFirst().orElse(null);
        commits(git, log -> {
            if (latestRootCommit != null) log.not(latestRootCommit);
        })
                .filter(commit -> !MERGE_PATTERN.matcher(commit.getShortMessage()).matches())
                .map(this::validate)
                .filter(Objects::nonNull)
                .forEach(this::addErr);
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
