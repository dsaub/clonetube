pipeline {
    agent any

    stages {
        stage("Checkout repository") {
            steps {
                sh 'git clone https://github.com/dsaub/clonetube'
            }
        }
        stage("Login with Docker") {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'github-token',
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
                
                sh 'cd clonetube && docker build -t ghcr.io/dsaub/clonetube-backend:latest backend'
               
                sh 'docker push ghcr.io/dsaub/clonetube-backend:latest'
            }
        }
        stage("Frontend Docker Image Build and Push") {
            steps {
                sh 'cd clonetube && docker build -t ghcr.io/dsaub/clonetube-frontend:latest frontend'
                sh 'docker push ghcr.io/dsaub/clonetube-frontend:latest'
            }
        }
    }
}