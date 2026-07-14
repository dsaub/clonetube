pipeline {
    agent any
    options {
        skipDefaultCheckout()
    }

    stages {
        stage("Checkout") {
            steps {
                checkout([$class: 'GitSCM',
                    branches: [[name: '*/latest']],
                    extensions: [[$class: 'CloneOption', shallow: true, depth: 1, noTags: true]],
                    userRemoteConfigs: [[url: 'https://github.com/dsaub/clonetube.git']]])
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