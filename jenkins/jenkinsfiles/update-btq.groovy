properties([
        parameters([
                string(name: 'tag', defaultValue: 'null', description: 'change btq version'),
                string(name: 'buildname', defaultValue: 'null', description: 'change sealights buildname for cd agents (if you change anything then this parameter is mandatory)'),
                string(name: 'labid', defaultValue: 'null', description: 'change sealights lab_id'),
                string(name: 'branch', defaultValue: 'null', description: 'change branch to pull (effects tests and helm)')
        ])
])

pipeline {
   agent {
        kubernetes {
            defaultContainer 'shell'
            yaml """
                apiVersion: v1
                kind: Pod
                metadata:
                  labels:
                    some-label: some-value
                spec:
                  containers:
                  - name: shell
                    image: "public.ecr.aws/a2q7i5i2/sl-jenkins-base-ci:latest"
                    command:
                    - cat
                    tty: true
            """
        }
    }
    environment {
        SL_TOKEN = (sh(returnStdout: true, script:"aws secretsmanager get-secret-value --region eu-west-1 --secret-id 'btq/demo_token' | jq -r '.SecretString' | jq -r '.demo_token'" )).trim()
        IDENTIFIER = 'demo.btq.sealights.co'
        tag = "demo_${params.tag}"
    }
     stages {
        stage("Preparing Spin up") {
            steps {
                script {
                    cleanWs()
                    if ("${IDENTIFIER}" == "") {
                        error("IDENTIFIER must be inserted!")
                        currentBuild.result = 'FAILURE'
                    }
                    ENV_NAME = "${IDENTIFIER}"
                    currentBuild.displayName = "${ENV_NAME} btq update"
                    LOWER_ENV_NAME = "${ENV_NAME}".toLowerCase()
                    IP = "${IDENTIFIER}"
                            stage("Updating Helm") {
                                sh script: """
                                    aws secretsmanager get-secret-value --region eu-west-1 --secret-id 'btq/demo_key_pair' | jq -r '.SecretString' | jq -r '.demo_key_pair' > key.pem
                                    chmod 0400 key.pem

                                    ssh -o StrictHostKeyChecking=no -i key.pem ec2-user@demo.btq.sealights.co 'bash /opt/sealights/install-btq.sh ${env.tag} ${params.buildname} ${params.labid} ${params.branch} ${env.SL_TOKEN} ${params.branch}'
                                """
                            }
                }
            }
        }
    }
}