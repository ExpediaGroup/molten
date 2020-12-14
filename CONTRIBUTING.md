# How To Contribute

We'd love to accept your patches and contributions to this project. There are just a few guidelines you need to follow which are described in detail below.

## 1. Fork this repo

You should create a fork of this project in your account and work from there. You can create a fork by clicking the fork button in GitHub.

## 2. One feature, one branch

Work for each new feature/issue should occur in its own branch. To create a new branch from the command line:
```shell
git checkout -b my-new-feature
```
where "my-new-feature" describes what you're working on.

## 3. Add tests for any bug fixes or new functionality

## 4. Check code style

Before opening a pull request, ensure that your new code conforms to the code style by running Checkstyle checks with `mvn verify`. See Checkstyle ruleset [here](build/checkstyle_rules.xml). 
CodeStyle configuration for IntelliJ is also available [here](build/codeStyleFormatter.xml).

## 5. Add documentation for new or updated functionality

Please review all the .md files in this project to see if they are impacted by your change and update them accordingly.

## 6. Add to CHANGELOG.md

Any notable changes should be recorded in the CHANGELOG.md following the [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) conventions.

## 7. Submit Pull Request and describe the change

Push your changes to your branch and open a pull request against the parent repo on GitHub. The project administrators will review your pull request and respond with feedback.

# How Your Contribution Gets Merged

Upon Pull Request submission, your code will be reviewed by the maintainers. They will confirm at least the following:

- Tests run successfully (unit, coverage, integration, style).
- Contribution policy has been followed.

Two (human) reviewers will need to sign off on your Pull Request before it can be merged.
