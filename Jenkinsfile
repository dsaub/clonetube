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
                        docker run --rm \\
                            -v "$(pwd)/backend:/app" \\
                            -w /app \\
                            python:3.14-slim \\
                            sh -c "
                                pip install uv -q &&
                                uv sync --group dev -q &&
                                uv run pytest --junitxml=test-results.xml --tb=short -v
                            "
                    '''
                }
            }
            post {
                always {
                    junit 'backend/test-results.xml'
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
                    sh 'docker login ghcr.io -u $USER -p $PASS'
                }
            }
        }

        stage("Backend Docker Image Build and Push") {
            steps {
                sh 'docker build -t ghcr.io/dsaub/clonetube-backend:latest backend'
                sh 'docker push ghcr.io/dsaub/clonetube-backend:latest'
            }
        }

        stage("Frontend Docker Image Build and Push") {
            steps {
                sh 'docker build -t ghcr.io/dsaub/clonetube-frontend:latest frontend'
                sh 'docker push ghcr.io/dsaub/clonetube-frontend:latest'
            }
        }
    }
}
