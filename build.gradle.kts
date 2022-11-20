plugins {
    java
}

repositories {

}

dependencies {

}

tasks {
    register<tequila.ValidateCommitsGitTask>("validateCommits")
    register<tequila.ValidateCommitMessageGitTask>("validateCommitMessageGit")
}
