pipeline {
    agent any

    parameters {
        booleanParam(name: 'RUN_MATRIX_BENCHMARKS', defaultValue: false, description: 'Run dedicated server multi-mod matrix benchmarks')
        choice(name: 'OVERRIDE_JDK', choices: ['AUTO', 'JDK21', 'JDK17', 'JDK8'], description: 'Override detected JDK for this build')
        booleanParam(name: 'DEPLOY_PRODUCTION', defaultValue: false, description: 'Package and deploy production release bundle to dist/production and GitHub Releases')
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '10'))
        timestamps()
        timeout(time: 20, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        GRADLE_OPTS = "-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Xmx2g"
    }

    stages {
        stage('Detect Version & Environment') {
            steps {
                script {
                    echo "================================================="
                    echo " HeapHammer Automated Multi-Version Jenkins Build"
                    echo " Branch: ${env.BRANCH_NAME ?: env.GIT_BRANCH}"
                    echo "================================================="

                    // Read target Minecraft, mod, and Java versions from gradle.properties
                    def props = readProperties file: 'gradle.properties'
                    def mcVersion = props['minecraft_version'] ?: '1.21.1'
                    def modVersion = props['mod_version'] ?: '1.0.0'
                    def targetJava = props['java_version'] ?: '21'

                    echo "Target Minecraft Version: ${mcVersion}"
                    echo "Target Mod Version: ${modVersion}"
                    echo "Target Java Version: ${targetJava}"

                    env.TARGET_MC_VERSION = mcVersion
                    env.TARGET_MOD_VERSION = modVersion
                    env.TARGET_JAVA_VERSION = targetJava

                    // Select JDK tool based on java_version or user override
                    if (params.OVERRIDE_JDK != 'AUTO') {
                        env.SELECTED_JDK = params.OVERRIDE_JDK
                    } else if (targetJava == '8') {
                        env.SELECTED_JDK = 'JDK8'
                    } else if (targetJava == '17') {
                        env.SELECTED_JDK = 'JDK17'
                    } else {
                        env.SELECTED_JDK = 'JDK21'
                    }

                    echo "Selected Jenkins JDK Tool: ${env.SELECTED_JDK}"
                }
            }
        }

        stage('Compile & Test') {
            tools {
                jdk "${env.SELECTED_JDK}"
            }
            steps {
                script {
                    echo "Compiling and executing test suite with ${env.SELECTED_JDK}..."
                    if (isUnix()) {
                        sh 'chmod +x gradlew'
                        sh './gradlew check test --no-daemon'
                    } else {
                        bat 'gradlew.bat check test --no-daemon'
                    }
                }
            }
            post {
                always {
                    junit testResults: 'build/test-results/**/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Assemble Artifacts') {
            tools {
                jdk "${env.SELECTED_JDK}"
            }
            steps {
                script {
                    echo "Packaging HeapHammer mod and synthetic test fixtures..."
                    if (isUnix()) {
                        sh './gradlew build buildTestmods --no-daemon'
                    } else {
                        bat 'gradlew.bat build buildTestmods --no-daemon'
                    }
                }
            }
            post {
                success {
                    archiveArtifacts artifacts: 'build/libs/*.jar, build/testmods/*.jar', fingerprint: true, allowEmptyArchive: false
                }
            }
        }

        stage('Matrix Server Verification') {
            when {
                expression { return params.RUN_MATRIX_BENCHMARKS }
            }
            tools {
                jdk "${env.SELECTED_JDK}"
            }
            steps {
                script {
                    echo "Running live dedicated server multi-mod matrix benchmarks..."
                    if (isUnix()) {
                        sh 'pwsh tools/run-mod-matrix-test.ps1 || true'
                    } else {
                        bat 'powershell -ExecutionPolicy Bypass -File tools/run-mod-matrix-test.ps1'
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'build/matrix-reports/*.json, build/matrix-reports/*.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Production Release & Deployment') {
            when {
                anyOf {
                    buildingTag()
                    branch 'release/*'
                    expression { return params.DEPLOY_PRODUCTION }
                }
            }
            tools {
                jdk "${env.SELECTED_JDK}"
            }
            steps {
                script {
                    echo "================================================="
                    echo " Production Release & Deployment Pipeline Active"
                    echo " Target Mod Version: ${env.TARGET_MOD_VERSION}"
                    echo " Target MC Version:  ${env.TARGET_MC_VERSION}"
                    echo " Branch/Tag:         ${env.BRANCH_NAME ?: env.TAG_NAME ?: env.GIT_BRANCH}"
                    echo "================================================="

                    // Stage production release bundle in dist/production with SHA-256 sums
                    if (isUnix()) {
                        sh 'pwsh tools/publish-release.ps1 -SkipGitHub || true'
                    } else {
                        bat 'powershell -ExecutionPolicy Bypass -File tools/publish-release.ps1 -SkipGitHub'
                    }

                    // Optional GitHub Releases deployment if credentials exist
                    if (env.TAG_NAME || params.DEPLOY_PRODUCTION) {
                        def releaseTag = env.TAG_NAME ?: "v${env.TARGET_MOD_VERSION}"
                        echo "Production bundle staged for release tag: ${releaseTag}"
                        try {
                            withCredentials([string(credentialsId: 'github-release-token', variable: 'GH_TOKEN')]) {
                                echo "Publishing release ${releaseTag} to GitHub Releases with credentials..."
                                if (isUnix()) {
                                    sh "pwsh tools/publish-release.ps1 -Tag ${releaseTag}"
                                } else {
                                    bat "powershell -ExecutionPolicy Bypass -File tools/publish-release.ps1 -Tag ${releaseTag}"
                                }
                            }
                        } catch (Exception e) {
                            echo "Notice: GitHub credential 'github-release-token' not configured or upload skipped (${e.message}). Artifacts are archived in dist/production/."
                        }
                    }
                }
            }
            post {
                success {
                    archiveArtifacts artifacts: 'dist/production/**/*', fingerprint: true, allowEmptyArchive: false
                }
            }
        }
    }

    post {
        success {
            echo "HeapHammer build completed successfully for Minecraft ${env.TARGET_MC_VERSION}!"
        }
        failure {
            echo "HeapHammer build failed on branch ${env.BRANCH_NAME ?: env.GIT_BRANCH}!"
        }
    }
}
