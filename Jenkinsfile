pipeline {
    agent any
    options {
        skipDefaultCheckout()
    }

    stages {
        stage("Checkout") {
            steps {
                sh 'git config --global --add safe.directory "*"'
                checkout([$class: 'GitSCM',
                    branches: [[name: '*/latest']],
                    extensions: [[$class: 'CloneOption', shallow: true, depth: 1, noTags: true]],
                    userRemoteConfigs: [[url: 'https://github.com/dsaub/clonetube.git']]])
            }
        }

        stage("Backend Tests") {
            steps {
                catchError(message: 'Backend tests completed with failures', buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        sudo docker run --rm \\
                            -v "$(pwd)/backend:/app" \\
                            -w /app \\
                            python:3.14-slim \\
                            sh -c "
                                pip install uv -q &&
                                uv sync --group dev -q &&
                                uv run pytest --junitxml=test-results.xml --tb=short -v 2>&1
                            " | tee backend/pytest-output.log
                    '''
                }
            }
            post {
                always {
                    junit 'backend/test-results.xml'

                    script {
                        def warningsFile = 'backend/pytest-warnings.txt'
                        sh """
                            grep -iE '(warning|deprecation|deprecated)' backend/pytest-output.log 2>/dev/null | sort -u > $warningsFile || true
                        """
                        if (fileExists(warningsFile)) {
                            def warnings = readFile(warningsFile).trim()
                            if (warnings) {
                                echo "=== PYTEST WARNINGS ==="
                                echo warnings
                                echo "========================"
                            }
                        }
                    }

                    archiveArtifacts artifacts: 'backend/pytest-output.log, backend/pytest-warnings.txt', allowEmptyArchive: true

                    recordIssues(
                        enabledForFailure: true,
                        aggregatingResults: true,
                        tools: [issues(pattern: 'backend/pytest-output.log', id: 'pytest', name: 'Pytest Warnings')]
                    )
                }
            }
        }

        stage("Frontend Tests") {
            steps {
                catchError(message: 'Frontend tests completed with failures', buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    sh '''
                        sudo docker run --rm \\
                            -v "$(pwd)/frontend:/app" \\
                            -w /app \\
                            node:24-slim \\
                            sh -c "
                                npm install -g pnpm -q &&
                                pnpm install --frozen-lockfile -q &&
                                pnpm vitest run --reporter=junit --outputFile=test-results.xml 2>&1
                            " | tee frontend/vitest-output.log
                    '''
                }
            }
            post {
                always {
                    junit 'frontend/test-results.xml'

                    script {
                        def warningsFile = 'frontend/vitest-warnings.txt'
                        sh """
                            grep -iE '(warning|deprecation|deprecated)' frontend/vitest-output.log 2>/dev/null | sort -u > $warningsFile || true
                        """
                        if (fileExists(warningsFile)) {
                            def warnings = readFile(warningsFile).trim()
                            if (warnings) {
                                echo "=== VITEST WARNINGS ==="
                                echo warnings
                                echo "========================"
                            }
                        }
                    }

                    archiveArtifacts artifacts: 'frontend/vitest-output.log, frontend/vitest-warnings.txt', allowEmptyArchive: true

                    recordIssues(
                        enabledForFailure: true,
                        aggregatingResults: true,
                        tools: [issues(pattern: 'frontend/vitest-output.log', id: 'vitest', name: 'Vitest Warnings')]
                    )
                }
            }
        }

        stage("Login with Docker") {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'gh-token',
                        usernameVariable: 'USER',
                        passwordVariable: 'PASS'
                    )
                ]) {
                    sh 'sudo docker login ghcr.io -u $USER -p $PASS'
                }
            }
        }

        stage("Backend Docker Image Build and Push") {
            steps {
                sh 'sudo docker build -t ghcr.io/dsaub/clonetube-backend:latest backend'
                sh 'sudo docker push ghcr.io/dsaub/clonetube-backend:latest'
            }
        }

        stage("Frontend Docker Image Build and Push") {
            steps {
                sh 'sudo docker build -t ghcr.io/dsaub/clonetube-frontend:latest frontend'
                sh 'sudo docker push ghcr.io/dsaub/clonetube-frontend:latest'
            }
        }
    }
}
