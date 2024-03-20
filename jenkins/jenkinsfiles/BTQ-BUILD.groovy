pipeline{
  agent {
    kubernetes {
      yaml readTrusted('jenkins/pod-templates/BTQ_BUILD_shell_kaniko_pod.yaml')
      defaultContainer "shell"
    }
  }

  options {
    buildDiscarder logRotator(numToKeepStr: '100')
    timestamps()
  }
  parameters {
    string(name: 'TAG', defaultValue: '1.2.2', description: 'latest tag')
    string(name: 'REGION', defaultValue: 'eu-west-1', description: 'latest tag')
    string(name: 'BRANCH', defaultValue: 'main', description: 'default branch')
    choice(name: 'SERVICE', choices: ["adservice","cartservice","checkoutservice", "currencyservice","emailservice","frontend","paymentservice","productcatalogservice","recommendationservice","shippingservice"], description: 'Service name to build')
    string(name: 'BUILD_NAME', defaultValue: 'none', description: 'build name')
  }
  environment{
    ECR_FULL_NAME = "btq-${params.SERVICE}"
    ECR_URI = "474620256508.dkr.ecr.eu-west-1.amazonaws.com/${env.ECR_FULL_NAME}"
    SL_TOKEN = (sh(returnStdout: true, script:"aws secretsmanager get-secret-value --region eu-west-1 --secret-id 'btq/demo_token' | jq -r '.SecretString' | jq -r '.demo_token'" )).trim()
    TAG = "demo_${params.TAG}"
  }

  stages{
    stage('Init') {
      steps {
        script {
          // Clone the repository with the specified branch.
          git branch: params.BRANCH, url: 'https://github.com/Sealights-btq/demo-btq.git'

          stage("Create ECR repository") {

            def repo_policy = readTrusted('jenkins/repo_policy/repo_policy.json')
            create_repo([
              region : params.REGION,
              artifact_name: "${env.ECR_FULL_NAME}",
              key_type: "KMS"
            ] as Map)
            set_repo_policy([
              region : params.REGION,
              artifact_name: "${env.ECR_FULL_NAME}",
              repo_policy: repo_policy
            ] as Map)
          }
          stage("Build Docker ${params.SERVICE} Image") {
            container(name: 'kaniko'){
              script {
                def CONTEXT = params.SERVICE == "cartservice" ? "./src/${params.SERVICE}/src" : "./src/${params.SERVICE}"
                def DP = "${CONTEXT}/Dockerfile"
                def D = "${env.ECR_URI}:${env.TAG}"
                def BRANCH = params.BRANCH
                def BUILD_NAME = params.BUILD_NAME
                def SL_TOKEN = env.SL_TOKEN
                def AGENT_URL = params.AGENT_URL
                def AGENT_URL_SLCI = params.AGENT_URL_SLCI

                sh """
                    /kaniko/executor \
                    --context ${CONTEXT} \
                    --dockerfile ${DP} \
                    --destination ${D} \
                    --build-arg BRANCH=${BRANCH} \
                    --build-arg BUILD_NAME=${BUILD_NAME} \
                    --build-arg SEALIGHTS_TOKEN=${SL_TOKEN}
                """
              }
            }
          }
        }
      }
    }
  }
}
def set_repo_policy(Map params) {
  sh """
        aws ecr set-repository-policy \
        --region ${params.region} \
        --repository-name ${params.artifact_name} \
        --policy-text '${params.repo_policy}'
    """
}

def create_repo(Map params) {
  sh """#!/bin/bash
        output=\$(aws ecr describe-repositories --region ${params.region} --repository-names ${params.artifact_name} 2>&1)

        if [ \$? -ne 0 ]; then
        if echo \${output} | grep -q RepositoryNotFoundException; then
            aws ecr create-repository --region ${params.region} --repository-name ${params.artifact_name} --encryption-configuration encryptionType="${params.key_type}"
        else
            >&2 echo \${output}
        fi
        fi
    """
}
