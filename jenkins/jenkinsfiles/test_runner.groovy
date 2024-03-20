pipeline {
  agent {
    kubernetes {
      yaml readTrusted('jenkins/pod-templates/test_runner_pod.yaml')
      defaultContainer "shell"
    }
  }

  options {
    buildDiscarder logRotator(numToKeepStr: '30')
    timestamps()
  }

  parameters {
    string(name: 'BRANCH', defaultValue: 'public', description: 'Branch to clone')
    string(name: 'SL_LABID', defaultValue: '', description: 'Lab_id')
    booleanParam(name: 'Run_all_tests', defaultValue: true, description: 'Checking this box will run all tests even if individual ones are not checked')
    booleanParam(name: 'Cucumber', defaultValue: false, description: 'Run tests using Cucumber testing framework (java)')
    booleanParam(name: 'Cypress', defaultValue: false, description: 'Run tests using Cypress testing framework')
    booleanParam(name: 'Junit_with_testNG', defaultValue: false, description: 'Run tests using Junit testing framework with testNG (maven)')
    booleanParam(name: 'Junit_without_testNG', defaultValue: false, description: 'Run tests using Junit testing framework without testNG (maven)')
    booleanParam(name: 'Junit_with_testNG_gradle', defaultValue: false, description: 'Run tests using Junit testing framework with testNG (gradle)')
    booleanParam(name: 'Mocha', defaultValue: false, description: 'Run tests using Mocha testing framework')
    booleanParam(name: 'MS', defaultValue: false, description: 'Run tests using MS testing framework')
    booleanParam(name: 'NUnit', defaultValue: false, description: 'Run tests using NUnityour_dns testing framework')
    booleanParam(name: 'Postman', defaultValue: false, description: 'Run tests using postman testing framework')
    booleanParam(name: 'Pytest', defaultValue: false, description: 'Run tests using Pytest testing framework')
    booleanParam(name: 'Robot', defaultValue: false, description: 'Run tests using Robot testing framework')
    booleanParam(name: 'Soapui', defaultValue: false, description: 'Run tests using Soapui testing framework')
    booleanParam(name: 'long_test', defaultValue: false, description: 'Runs a long test for showing tia (not effected by run_all_tests flag)')
  }
  environment {
    MACHINE_DNS = 'http://demo.btq.sealights.co:8081'
    machine_dns = 'http://demo.btq.sealights.co:8081'
    SL_TOKEN = (sh(returnStdout: true, script:"aws secretsmanager get-secret-value --region eu-west-1 --secret-id 'btq/demo_token' | jq -r '.SecretString' | jq -r '.demo_token'" )).trim()
    wait_time = "30"
  }
  stages{
    stage("Init test"){
      steps{
        script{
          git branch: params.BRANCH, url: 'https://github.com/Sealights-btq/demo-btq.git'
        }
      }
    }
    stage("Downloading agents"){
      steps{
        script{
          //We're not downloading node agent since it will need to be downloaded in node modules
          sh """ 
          #mkdir /sealights
          cd /sealights
          #Java agent
          wget -nv https://agents.sealights.co/sealights-java/sealights-java-latest.zip && unzip -o sealights-java-latest.zip && rm sealights-java-latest.zip
          
          #Python agent
          pip install sealights-python-agent
          
          #Dotnet agent
          wget -nv -O sealights-dotnet-agent.tar.gz https://agents.sealights.co/dotnetcore/latest/sealights-dotnet-agent-linux-self-contained.tar.gz && \
          mkdir sl-dotnet-agent && tar -xzf ./sealights-dotnet-agent.tar.gz --directory ./sl-dotnet-agent
          cd ~
          """
        }
      }
    }
    stage('Cypress framework starting'){
      steps{
        script{
          if( params.Run_all_tests == true || params.Cypress == true) {
            build(job:"BTQ-nodejs-tests-Cypress-framework", parameters: [string(name: 'BRANCH', value: "${params.BRANCH}"),string(name: 'SL_LABID', value: "${params.SL_LABID}") , string(name:'SL_TOKEN' , value:"${env.SL_TOKEN}") ,string(name:'MACHINE_DNS1' , value:"${env.MACHINE_DNS}")])
          }
      }
    }
    }
    stage('MS-Tests framework'){
      steps{
        script{
          if( params.Run_all_tests == true || params.MS == true) {
              sh """
                echo 'MS-Tests framework starting ..... '
                export machine_dns="${env.MACHINE_DNS}" # Inside the code we use machine_dns envronment variable
                dotnet /sealights/sl-dotnet-agent/SL.DotNet.dll startExecution --testStage "MS-Tests" --labId ${params.SL_LABID} --token ${env.SL_TOKEN}
                dotnet /sealights/sl-dotnet-agent/SL.DotNet.dll run --workingDir . --instrumentationMode tests --target dotnet   --testStage "MS-Tests" --labId ${params.SL_LABID} --token ${env.SL_TOKEN} --targetArgs "test ./integration-tests/dotnet-tests/MS-Tests/"
                dotnet /sealights/sl-dotnet-agent/SL.DotNet.dll endExecution --testStage "MS-Tests" --labId ${params.SL_LABID} --token ${env.SL_TOKEN}
                sleep ${env.wait_time} # Wait at least 10 seconds for the backend to update status that the previous test stage was closed, closing and starting a test stage withing 10 seconds can cause inaccurate test stage coverage
                """
          }
        }
      }
    }


    stage('N-Unit framework starting'){
      steps{
        script{
          if( params.Run_all_tests == true || params.NUnit == true) {
            sh """
                  echo 'N-Unit framework starting ..... '
                  export machine_dns="${env.MACHINE_DNS}"
                  dotnet /sealights/sl-dotnet-agent/SL.DotNet.dll startExecution --testStage "NUnit-Tests" --labId ${params.SL_LABID} --token ${env.SL_TOKEN}
                  dotnet /sealights/sl-dotnet-agent/SL.DotNet.dll run --workingDir . --instrumentationMode tests --target dotnet   --testStage "NUnit-Tests" --labId ${params.SL_LABID} --token ${env.SL_TOKEN} --targetArgs "test ./integration-tests/dotnet-tests/NUnit-Tests/"
                  dotnet /sealights/sl-dotnet-agent/SL.DotNet.dll endExecution --testStage "NUnit-Tests" --labId ${params.SL_LABID} --token ${env.SL_TOKEN}
                  sleep ${env.wait_time}
                  """
          }
        }
      }
    }


    stage('Gradle'){
      steps{
        script{
          if( params.Run_all_tests == true || params.Junit_with_testNG_gradle == true) {
            sh """
                      #!/bin/bash
                      export machine_dns="${env.MACHINE_DNS}"
                      cd ./integration-tests/java-tests-gradle
                      echo ${env.SL_TOKEN}>sltoken.txt
                      echo '{
                          "executionType": "testsonly",
                          "tokenFile": "./sltoken.txt",
                          "createBuildSessionId": false,
                          "testStage": "Junit without testNG-gradle",
                          "runFunctionalTests": true,
                          "labId": "${params.SL_LABID}",
                          "proxy": null,
                          "logEnabled": false,
                          "logDestination": "console",
                          "logLevel": "info",
                          "sealightsJvmParams": {}
                      }' > slgradletests.json


                      echo "Adding Sealights to Tests Project gradle file..."
                      java -jar /sealights/sl-build-scanner.jar -gradle -configfile slgradletests.json -workspacepath .
                      gradle test
                      sleep ${env.wait_time}
                      """
          }
        }
      }
    }
    stage('robot framework'){
      steps{
        script{
          if( params.Run_all_tests == true || params.Robot == true) {
            sh """
                      pip install robotframework && pip install robotframework-requests
                      export machine_dns="${env.MACHINE_DNS}"
                      export PYTHONPATH="."
                      echo 'robot framework starting ..... '
                      cd ./integration-tests/robot-tests
                      
                      apt-get update && apt-get install -y \
                        ca-certificates \
                        fonts-liberation \
                        libappindicator3-1 \
                        libasound2 \
                        libatk-bridge2.0-0 \
                        libatk1.0-0 \
                        libcups2 \
                        libdbus-1-3 \
                        libgdk-pixbuf2.0-0 \
                        libnspr4 \
                        libnss3 \
                        libxcomposite1 \
                        libxdamage1 \
                        libxrandr2 \
                        xdg-utils \
                        libgbm1 > /dev/null
                        wget https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb
                      apt install -y ./google-chrome-stable_current_amd64.deb > /dev/null
                      pip install -r requirements.txt
                      robot --listener "SLListener.py:${env.SL_TOKEN}::Robot Tests:${params.SL_LABID}" ./
                      cd ../..
                      sleep ${env.wait_time}
                      """
          }
        }
      }
    }

    stage('Cucumber framework') {
      steps{
        script{
          if( params.Run_all_tests == true || params.Cucumber == true) {
            sh """
                      #!/bin/bash
                      export machine_dns="${env.MACHINE_DNS}"
                      echo 'Cucumber framework starting ..... '
                      cd ./integration-tests/cucumber-framework/
                      echo ${env.SL_TOKEN}>sltoken.txt
                      # shellcheck disable=SC2016
                      echo  '{
                              "executionType": "testsonly",
                              "tokenFile": "./sltoken.txt",
                              "createBuildSessionId": false,
                              "testStage": "Cucmber framework java ",
                              "runFunctionalTests": true,
                              "labId": "${params.SL_LABID}",
                              "proxy": null,
                              "logEnabled": false,
                              "logDestination": "console",
                              "logLevel": "info",
                              "sealightsJvmParams": {}
                              }' > slmaventests.json
                      echo "Adding Sealights to Tests Project POM file..."
                      java -jar /sealights/sl-build-scanner.jar -pom -configfile slmaventests.json -workspacepath .

                      unset MAVEN_CONFIG
                      ./mvnw test
                      sleep ${env.wait_time}
                      """
          }
        }
      }
    }



    stage('Junit support testNG framework'){
      steps{
        script{
          if( params.Run_all_tests == true || params.Junit_with_testNG == true) {
            sh """
                      #!/bin/bash
                      echo 'Junit support testNG framework starting ..... '
                      pwd
                      ls
                      cd ./integration-tests/support-testNG
                      export SL_TOKEN="${env.SL_TOKEN}"
                      echo $SL_TOKEN>sltoken.txt
                      export machine_dns="${env.MACHINE_DNS}"
                      # shellcheck disable=SC2016
                      echo  '{
                              "executionType": "testsonly",
                              "tokenFile": "./sltoken.txt",
                              "createBuildSessionId": false,
                              "testStage": "Junit support testNG",
                              "runFunctionalTests": true,
                              "labId": "${params.SL_LABID}",
                              "proxy": null,
                              "logEnabled": false,
                              "logDestination": "console",
                              "logLevel": "info",
                              "sealightsJvmParams": {}
                              }' > slmaventests.json
                      echo "Adding Sealights to Tests Project POM file..."
                      java -jar /sealights/sl-build-scanner.jar -pom -configfile slmaventests.json -workspacepath .
                      mvn clean package
                      sleep ${env.wait_time}
                      """
          }
        }
      }
    }


    stage('Junit without testNG '){
      steps{
        script{
          if( params.Run_all_tests == true || params.Junit_without_testNG == true) {
            sh """
                      #!/bin/bash
                      echo 'Junit without testNG framework starting ..... '
                      pwd
                      ls
                      cd integration-tests/java-tests
                      export SL_TOKEN="${env.SL_TOKEN}"
                      echo $SL_TOKEN>sltoken.txt
                      export machine_dns="${env.MACHINE_DNS}"
                      # shellcheck disable=SC2016
                      echo  '{
                              "executionType": "testsonly",
                              "tokenFile": "./sltoken.txt",
                              "createBuildSessionId": false,
                              "testStage": "Junit without testNG",
                              "runFunctionalTests": true,
                              "labId": "${params.SL_LABID}",
                              "proxy": null,
                              "logEnabled": false,
                              "logDestination": "console",
                              "logLevel": "info",
                              "sealightsJvmParams": {}
                              }' > slmaventests.json
                      echo "Adding Sealights to Tests Project POM file..."
                      java -jar /sealights/sl-build-scanner.jar -pom -configfile slmaventests.json -workspacepath .

                      mvn clean package
                      sleep ${env.wait_time}
                      """
          }
        }
      }
    }


    stage('Postman framework'){
      steps{
        script{
          if( params.Run_all_tests == true || params.Postman == true) {
            sh """
                    export MACHINE_DNS="${env.MACHINE_DNS}"
                    cd ./integration-tests/postman-tests/
                    npm install -g sealights-newman-wrapper newman-reporter-xunit newman-reporter-junit5
                    echo 'Postman framework starting ..... '
                    npx sealights-newman-wrapper --sl-token ${env.SL_TOKEN} --sl-labId ${params.SL_LABID} --sl-testStage "postman tests" sealights-excersise.postman_collection.json --env-var machine_dns=${env.MACHINE_DNS}
                    cd ../..
                    sleep ${env.wait_time}
                    """
          }
        }
      }
    }


    // stage('Jest framework'){
    //   steps{
    //     script{
    //
    //       sh """
    //             echo 'Jest framework starting ..... '
    //             export machine_dns="${env.MACHINE_DNS}"
    //             cd ./integration-tests/nodejs-tests/Jest
    //             npm install jest && npm install jest-cli && npm install sealights-jest-plugin
    //             export NODE_DEBUG=sl
    //             export SL_TOKEN="${env.SL_TOKEN}"
    //             export SL_LABID="${params.SL_LABID}"
    //             npm install
    //             npx jest integration-tests/nodejs-tests/Jest/test.js --sl-testStage='Jest tests' --sl-token="${env.SL_TOKEN}" --sl-labId="${params.SL_LABID}"
    //             cd ../..
    //             sleep ${env.wait_time}
    //         """
    //     }
    //   }
    // }



    stage('Mocha framework'){
      steps{
        script{
          if( params.Run_all_tests == true || params.Mocha == true) {
            sh """
                      export machine_dns="${env.MACHINE_DNS}"
                      cd ./integration-tests/nodejs-tests/mocha
                      npm install
                      npm install slnodejs
                      echo 'Mocha framework starting ..... '
                      ./node_modules/.bin/slnodejs mocha --token "${env.SL_TOKEN}" --labid "${params.SL_LABID}" --teststage 'Mocha tests'  --useslnode2 -- ./test/test.js --recursive --no-timeouts
                      cd ../..
                      sleep ${env.wait_time}
                      """
          }
        }
      }
    }



    stage('Soap-UI framework'){
      steps{
        script{
          if( params.Run_all_tests == true || params.Soapui == true) {
            sh """
              echo 'Soap-UI framework starting ..... '
              wget https://dl.eviware.com/soapuios/5.7.1/SoapUI-5.7.1-mac-bin.zip
              unzip SoapUI-5.7.1-mac-bin.zip
              cp integration-tests/soapUI/test-soapui-project.xml SoapUI-5.7.1/bin
              cd SoapUI-5.7.1/bin
              echo 'Downloading Sealights Agents...'
              wget -nv https://agents.sealights.co/sealights-java/sealights-java-latest.zip
              unzip -o sealights-java-latest.zip
              echo "Sealights agent version used is:" `cat sealights-java-version.txt`
              export SL_TOKEN="${env.SL_TOKEN}"
              echo ${env.SL_TOKEN}>sltoken.txt
              echo  '{
                "executionType": "testsonly",
                "tokenFile": "./sltoken.txt",
                "createBuildSessionId": false,
                "testStage": "Soap-UI framework",
                "runFunctionalTests": true,
                "labId": "${params.SL_LABID}",
                "proxy": null,
                "logEnabled": false,
                "logDestination": "console",
                "logLevel": "warn",
                "sealightsJvmParams": {}
                }' > slmaventests.json
              echo "Adding Sealights to Tests Project POM file..."
              pwd
              sed -i "s#machine_dns#${env.MACHINE_DNS}#" test-soapui-project.xml
              sed "s#machine_dns#${env.MACHINE_DNS}#" test-soapui-project.xml
              export SL_JAVA_OPTS="-javaagent:sl-test-listener.jar -Dsl.token=${env.SL_TOKEN} -Dsl.labId=${params.SL_LABID} -Dsl.testStage=Soapui-Tests -Dsl.log.enabled=true -Dsl.log.level=debug -Dsl.log.toConsole=true"
              sed -i -r "s/(^\\S*java)(.*com.eviware.soapui.tools.SoapUITestCaseRunner)/\\1 \\\$SL_JAVA_OPTS \\2/g" testrunner.sh
              sh -x ./testrunner.sh -s "TestSuite 1" "test-soapui-project.xml"
              sleep ${env.wait_time}
              """
          }
        }
      }
    }
    stage('Pytest framework'){
      steps{
        script{
          if( params.Run_all_tests == true || params.Pytest == true) {
            sh"""
                  pip install pytest && pip install requests
                  echo 'Pytest tests starting ..... '
                  export machine_dns="${env.MACHINE_DNS}"
                  cd ./integration-tests/python-tests
                  sl-python pytest --teststage "Pytest tests"  --labid ${params.SL_LABID} --token ${env.SL_TOKEN} python-tests.py
                  cd ../..
                  sleep ${env.wait_time}
                  """
          }
        }
      }
    }
    stage('Long test'){
      steps{
        script{
          if(params.long_test == true) {
            sh"""
                  pip install pytest && pip install requests
                  echo 'Pytest tests starting ..... '
                  export machine_dns="${env.MACHINE_DNS}"
                  cd ./integration-tests/python-tests
                  sl-python pytest --teststage "long_test"  --labid ${params.SL_LABID} --token ${env.SL_TOKEN} long_test.py
                  cd ../..
                  """
          }
        }
      }
    }
  }
}

