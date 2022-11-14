plugins {
    java
}

repositories {

}

dependencies {

}

tasks {
    register<tequila.ValidateCommitTask>("validateCommitMessage")
}
