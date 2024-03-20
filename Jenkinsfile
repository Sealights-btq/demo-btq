
pipeline {
  agent {
    kubernetes {
      yaml readTrusted('jenkins/pod-templates/CI_shell_pod.yaml')
      defaultContainer "shell"
    }
  }

  environment {
    machine_dns = 'demo.btq.sealights.co'
    SL_TOKEN = (sh(returnStdout: true, script:"aws secretsmanager get-secret-value --region eu-west-1 --secret-id 'btq/demo_token' | jq -r '.SecretString' | jq -r '.demo_token'" )).trim()
  }


  parameters {
    separator(name: "App parameters", sectionHeader: "App parameters",separatorStyle: "border-width: 5",
    sectionHeaderStyle: """
				background-color:  #cf0c0c;
				text-align: center;
				padding: 4px;
				color: #000000;
				font-size: 22px;
				font-weight: normal;
				text-transform: uppercase;
				font-family: 'Orienta', sans-serif;
				letter-spacing: 1px;
				font-style: italic;
			"""
		)
    string(name: 'APP_NAME', defaultValue: 'Boutique', description: 'name of the app (integration build)')
    string(name: 'BRANCH', defaultValue: 'public', description: 'Branch to clone')
    string(name: 'BUILD_NAME', defaultValue: '', description: 'build name (If not provided, default will be branchname-1-0-run ex: main-1-0-7)')
    separator(name: "Tests", sectionHeader: "Tests to run",separatorStyle: "border-width: 5",
    sectionHeaderStyle: """
				background-color:  #198ce6;
				text-align: center;
				padding: 4px;
				color: #000000;
				font-size: 22px;
				font-weight: normal;
				text-transform: uppercase;
				font-family: 'Orienta', sans-serif;
				letter-spacing: 1px;
				font-style: italic;
			"""
		)
    booleanParam(name: 'Run_all_tests', defaultValue: true, description: 'Checking this box will run all tests even if individual ones are not checked')
    booleanParam(name: 'Cucumber', defaultValue: false, description: 'Run tests using Cucumber testing framework (java)')
    booleanParam(name: 'Cypress', defaultValue: false, description: 'Run tests using Cypress testing framework')
    booleanParam(name: 'Junit_with_testNG', defaultValue: false, description: 'Run tests using Junit testing framework with testNG (maven)')
    booleanParam(name: 'Junit_without_testNG', defaultValue: false, description: 'Run tests using Junit testing framework without testNG (maven)')
    booleanParam(name: 'Junit_with_testNG_gradle', defaultValue: false, description: 'Run tests using Junit testing framework with testNG (gradle)')
    booleanParam(name: 'Mocha', defaultValue: false, description: 'Run tests using Mocha testing framework')
    booleanParam(name: 'MS', defaultValue: false, description: 'Run tests using MS testing framework')
    booleanParam(name: 'NUnit', defaultValue: false, description: 'Run tests using NUnit testing framework')
    booleanParam(name: 'Postman', defaultValue: false, description: 'Run tests using postman testing framework')
    booleanParam(name: 'Pytest', defaultValue: false, description: 'Run tests using Pytest testing framework')
    booleanParam(name: 'Robot', defaultValue: false, description: 'Run tests using Robot testing framework')
    booleanParam(name: 'Soapui', defaultValue: false, description: 'Run tests using Soapui testing framework')
    booleanParam(name: 'long_test', defaultValue: false, description: 'Runs a long test for showing tia (not effected by run_all_tests flag)')
  }

  stages {
    //Build parallel images
    stage('Build BTQ') {
      steps {
        script {
          env.CURRENT_VERSION = "1-0-${BUILD_NUMBER}"

          build_btq(
            sl_token: env.SL_TOKEN,
            build_name: "${params.BUILD_NAME}" == "" ? "${params.BRANCH}-${env.CURRENT_VERSION}" : "${params.BUILD_NAME}",
            branch: params.BRANCH,
            tag: env.CURRENT_VERSION,
          )
        }
      }
    }

    stage('update-btq') {
      steps {
        script {
          def build_name = "${params.BUILD_NAME}" == "" ? "${params.BRANCH}-${env.CURRENT_VERSION}" : "${params.BUILD_NAME}"
          env.CURRENT_VERSION = "1-0-${BUILD_NUMBER}"
          def IDENTIFIER= "${params.BRANCH}-${env.CURRENT_VERSION}"
          env.LAB_ID = create_lab_id(
          token: "${env.SL_TOKEN}",
          machine: "https://btq-demo.sealights.co",
          app: "${params.APP_NAME}",
          branch: "${params.BRANCH}",
          test_env: "${IDENTIFIER}",
          lab_alias: "${IDENTIFIER}",
          cdOnly: true,
          )


          build(job: 'update-btq', parameters: [string(name:'tag' , value:"${env.CURRENT_VERSION}"),
                                                string(name:'buildname' , value:build_name),
                                                string(name:'labid' , value:"${env.LAB_ID}"),
                                                string(name:'branch' , value:"${params.BRANCH}"),
                                                string(name:'token' , value:"${env.SL_TOKEN}")])
        }
      }
    }

    stage('Run Tests') {
      steps {
        script {
          run_tests(
            branch: params.BRANCH,
            lab_id: env.LAB_ID,
            token: env.SL_TOKEN,
            Run_all_tests: params.Run_all_tests,
            Cucumber: params.Cucumber,
            Cypress: params.Cypress,
            Junit_with_testNG: params.Junit_with_testNG,
            Junit_without_testNG: params.Junit_without_testNG,
            Junit_with_testNG_gradle: params.Junit_with_testNG_gradle,
            Mocha: params.Mocha,
            MS: params.Mocha,
            NUnit: params.NUnit,
            Postman: params.Postman,
            Pytest: params.Pytest,
            Robot: params.Robot,
            Soapui: params.Soapui,
            long_test: params.long_test
          )
        }
      }
    }

  }
}


def build_btq(Map params){
  

  def parallelLabs = [:]
  //List of all the images name

  def services_list = ["adservice","cartservice","checkoutservice", "currencyservice","emailservice","frontend","paymentservice","productcatalogservice","recommendationservice","shippingservice"]
  //def special_services = ["cartservice"].

  services_list.each { service ->
    parallelLabs["${service}"] = {
      build(job: 'BTQ-BUILD', parameters: [string(name: 'SERVICE', value: "${service}"),
                                           string(name:'TAG' , value:"${params.tag}"),
                                           string(name:'BRANCH' , value:"${params.branch}"),
                                           string(name:'BUILD_NAME' , value:"${params.build_name}")])
    }
  }
  parallel parallelLabs
}


def run_tests(Map params){
      sleep time: 120, unit: 'SECONDS'
      build(job: "test_runner", parameters: [
        string(name: 'BRANCH', value: "${params.branch}"),
        string(name: 'SL_LABID', value: "${params.lab_id}"),
        booleanParam(name: 'Run_all_tests', value: params.Run_all_tests),
        booleanParam(name: 'Cucumber', value: params.Cucumber),
        booleanParam(name: 'Cypress', value: params.Cypress),
        booleanParam(name: 'Junit_with_testNG', value: params.Junit_with_testNG),
        booleanParam(name: 'Junit_without_testNG', value: params.Junit_without_testNG),
        booleanParam(name: 'Junit_with_testNG_gradle', value: params.Junit_with_testNG_gradle),
        booleanParam(name: 'Mocha', value: params.Mocha),
        booleanParam(name: 'MS', value: params.Mocha),
        booleanParam(name: 'NUnit', value: params.NUnit),
        booleanParam(name: 'Postman', value: params.Postman),
        booleanParam(name: 'Pytest', value: params.Pytest),
        booleanParam(name: 'Robot', value: params.Robot),
        booleanParam(name: 'Soapui', value: params.Soapui),
        booleanParam(name: 'long_test', value: params.long_test)
      ])

}

def set_assume_role(Map params) {
  params.set_globaly = params.set_globaly == null ? true : params.set_globaly
  def credential_map = sh (returnStdout: true, script: """
                                aws sts assume-role --role-arn arn:aws:iam::${params.account_id}:role/${params.role_name}  \\
                                --role-session-name ${params.env}-access --query \"Credentials\"
                            """).replace('"', '').replaceAll('[\\s]', '').trim()

  def map = convert_to_map(credential_map)
  if (params.set_globaly) {
    env.AWS_ACCESS_KEY_ID = "${map.AccessKeyId}"
    env.AWS_SECRET_ACCESS_KEY = "${map.SecretAccessKey}"
    env.AWS_SESSION_TOKEN = "${map.SessionToken}"
  } else {
    return map
  }
}

def create_lab_id(Map params) {
  try {
    def cdOnlyString = ""
    if (params.cdOnly){
      cdOnlyString = ', "cdOnly": true'
    }
    if (params.isPR){
      env.LAB_ID = (sh(returnStdout: true, script:"""
            #!/bin/sh -e +x
            curl -X POST "${params.machine}/sl-api/v1/agent-apis/lab-ids/pull-request" -H "Authorization: Bearer ${params.token}" -H "Content-Type: application/json" -d '{ "appName": "${params.app}", "branchName": "${params.branch}", "testEnv": "${params.test_env}", "targetBranch": "${params.target_branch}", "isHidden": true }' | jq -r '.data.labId'
           """)).trim()
    } else {
      env.LAB_ID = (sh(returnStdout: true, script:"""
            #!/bin/sh -e +x
            curl -X POST "${params.machine}/sl-api/v1/agent-apis/lab-ids" -H "Authorization: Bearer ${params.token}" -H "Content-Type: application/json" -d '{ "appName": "${params.app}", "branchName": "${params.branch}", "testEnv": "${params.test_env}", "labAlias": "${params.lab_alias}", "isHidden": true ${cdOnlyString}}' | jq -r '.data.labId'
           """)).trim()
    }
    echo "LAB ID: ${env.LAB_ID}"
    return env.LAB_ID
  } catch (err) {
    echo env.LAB_ID
    error "Failed to create lab id"
  }
}

def convert_to_map(mapAsString) {
  def map =
    // Take the String value between
    // the [ and ] brackets.
    mapAsString[1..-2]
    // Split on , to get a List.
      .split(',')
    // Each list item is transformed
    // to a Map entry with key/value.
      .collectEntries { entry ->
        def pair = entry.split(':')
        [(pair.first()): "${pair.last()}"]
      }

  return map
}
