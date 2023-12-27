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
    string(name: 'SL_TOKEN', defaultValue: '', description: 'SL_TOKEN')
    string(name: 'SL_LABID', defaultValue: '', description: 'Lab_id')
    string(name: 'MACHINE_DNS', defaultValue: '', description: 'machine dns')

  }
  environment {
    MACHINE_DNS = "${params.MACHINE_DNS}"
    machine_dns = "${params.MACHINE_DNS}"
    wait_time = "20"
  }
  stages{
    stage("Init test"){
      steps{
        script{
          git branch: params.BRANCH, url: 'https://github.com/Sealights/microservices-demo-template.git'
        }
      }
    }
    // stage('Cypress framework starting'){
    //   steps{
    //     script{
    //       build(job:"BTQ-nodejs-tests-Cypress-framework", parameters: [string(name: 'BRANCH', value: "${params.BRANCH}"),string(name: 'SL_LABID', value: "${params.SL_LABID}") , string(name:'SL_TOKEN' , value:"${params.SL_TOKEN}") ,string(name:'MACHINE_DNS1' , value:"${params.MACHINE_DNS}")])
    //     }
    //   }
    // }

    stage('MS-Tests framework'){
      steps{
        script{
          sh """
                sleep ${env.wait_time} # Wait at least 10 seconds for the backend to update status that the previous test stage was closed, closing and starting a test stage withing 10 seconds can cause inaccurate test stage coverage
                echo 'MS-Tests framework starting ..... '
                export machine_dns="${params.MACHINE_DNS}" # Inside the code we use machine_dns envronment variable
                dotnet /sealights/sl-dotnet-agent/SL.DotNet.dll startExecution --testStage "MS-Tests" --labId ${params.SL_LABID} --token ${params.SL_TOKEN}
                sleep 30
                dotnet /sealights/sl-dotnet-agent/SL.DotNet.dll run --workingDir . --instrumentationMode tests --target dotnet   --testStage "MS-Tests" --labId ${params.SL_LABID} --token ${params.SL_TOKEN} --targetArgs "test ./integration-tests/dotnet-tests/MS-Tests/"
                dotnet /sealights/sl-dotnet-agent/SL.DotNet.dll endExecution --testStage "MS-Tests" --labId ${params.SL_LABID} --token ${params.SL_TOKEN}
                """
        }
      }
    }


    stage('N-Unit framework starting'){
      steps{
        script{
          sh """
                sleep ${env.wait_time}
                echo 'N-Unit framework starting ..... '
                export machine_dns="${params.MACHINE_DNS}"
                dotnet /sealights/sl-dotnet-agent/SL.DotNet.dll startExecution --testStage "NUnit-Tests" --labId ${params.SL_LABID} --token ${params.SL_TOKEN}
                sleep 30
                dotnet /sealights/sl-dotnet-agent/SL.DotNet.dll run --workingDir . --instrumentationMode tests --target dotnet   --testStage "NUnit-Tests" --labId ${params.SL_LABID} --token ${params.SL_TOKEN} --targetArgs "test ./integration-tests/dotnet-tests/NUnit-Tests/"
                sleep 30
                dotnet /sealights/sl-dotnet-agent/SL.DotNet.dll endExecution --testStage "NUnit-Tests" --labId ${params.SL_LABID} --token ${params.SL_TOKEN}
                """
        }
      }
    }


    stage('Gradle framework'){
      steps{
        script{
          sh """
                    #!/bin/bash
                    sleep ${env.wait_time}
                    export machine_dns="${params.MACHINE_DNS}"
                    cd ./integration-tests/java-tests-gradle
                    echo ${params.SL_TOKEN}>sltoken.txt
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
                        "logLevel": "warn",
                        "sealightsJvmParams": {}
                    }' > slgradletests.json


                    echo "Adding Sealights to Tests Project gradle file..."
                    java -jar /sealights/sl-build-scanner.jar -gradle -configfile slgradletests.json -workspacepath .
                    gradle test
                    """
        }
      }
    }
    stage('robot framework'){
      steps{
        script{
          sh """
                    sleep ${env.wait_time}
                    export machine_dns="${params.MACHINE_DNS}"
                    echo 'robot framework starting ..... '
                    cd ./integration-tests/robot-tests
                    sl-python start --labid ${SL_LABID} --token ${SL_TOKEN} --teststage "Robot Tests"
                    sleep 30
                    robot -xunit api_tests.robot
                    sl-python uploadreports --reportfile "unit.xml" --labid ${SL_LABID} --token ${SL_TOKEN}
                    sl-python end --labid ${SL_LABID} --token ${SL_TOKEN}
                    cd ../..
                    """
        }
      }
    }

    stage('Cucumber framework') {
      steps{
        script{
          sh """
                    #!/bin/bash
                    sleep ${env.wait_time}
                    export machine_dns="${params.MACHINE_DNS}"
                    echo 'Cucumber framework starting ..... '
                    cd ./integration-tests/cucumber-framework/
                    echo ${params.SL_TOKEN}>sltoken.txt
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
                            "logLevel": "warn",
                            "sealightsJvmParams": {}
                            }' > slmaventests.json
                    echo "Adding Sealights to Tests Project POM file..."
                    java -jar /sealights/sl-build-scanner.jar -pom -configfile slmaventests.json -workspacepath .

                    unset MAVEN_CONFIG
                    ./mvnw test
                    """

        }
      }
    }



    stage('Junit support testNG framework'){
      steps{
        script{
          sh """
                    #!/bin/bash
                    sleep ${env.wait_time}
                    echo 'Junit support testNG framework starting ..... '
                    pwd
                    ls
                    cd ./integration-tests/support-testNG
                    export SL_TOKEN="${params.SL_TOKEN}"
                    echo $SL_TOKEN>sltoken.txt
                    export machine_dns="${params.MACHINE_DNS}"
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
                            "logLevel": "warn",
                            "sealightsJvmParams": {}
                            }' > slmaventests.json
                    echo "Adding Sealights to Tests Project POM file..."
                    java -jar /sealights/sl-build-scanner.jar -pom -configfile slmaventests.json -workspacepath .
                    mvn clean package
                    """
        }
      }
    }


    stage('Junit without testNG '){
      steps{
        script{
          sh """
                    #!/bin/bash
                    sleep ${env.wait_time}
                    echo 'Junit without testNG framework starting ..... '
                    pwd
                    ls
                    cd integration-tests/java-tests
                    export SL_TOKEN="${params.SL_TOKEN}"
                    echo $SL_TOKEN>sltoken.txt
                    export machine_dns="${params.MACHINE_DNS}"
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
                            "logLevel": "warn",
                            "sealightsJvmParams": {}
                            }' > slmaventests.json
                    echo "Adding Sealights to Tests Project POM file..."
                    java -jar /sealights/sl-build-scanner.jar -pom -configfile slmaventests.json -workspacepath .

                    mvn clean package
                    """
        }
      }
    }


    stage('Postman framework'){
      steps{
        script{
          sh """
                    sleep ${env.wait_time}
                    echo 'Postman framework starting ..... '
                    export MACHINE_DNS="${params.MACHINE_DNS}"
                    cd ./integration-tests/postman-tests/
                    cp -r /nodeModules/node_modules .
                    npm i slnodejs
                    npm install newman
                    npm install newman-reporter-xunit
                    ./node_modules/.bin/slnodejs start --labid ${params.SL_LABID} --token ${params.SL_TOKEN} --teststage "postman tests"
                    npx newman run sealights-excersise.postman_collection.json --env-var machine_dns="${params.MACHINE_DNS}" -r xunit --reporter-xunit-export './result.xml' --suppress-exit-code
                    ./node_modules/.bin/slnodejs uploadReports --labid ${params.SL_LABID} --token ${params.SL_TOKEN} --reportFile './result.xml'
                    ./node_modules/.bin/slnodejs end --labid ${params.SL_LABID} --token ${params.SL_TOKEN}
                    cd ../..
                    """
        }
      }
    }


    // stage('Jest framework'){
    //   steps{
    //     script{

    //       sh """
    //             sleep ${env.wait_time}
    //             echo 'Jest framework starting ..... '
    //             export machine_dns="${params.MACHINE_DNS}"
    //             cd ./integration-tests/nodejs-tests/Jest
    //             cp -r /nodeModules/node_modules .
    //             npm i jest-cli
    //             export NODE_DEBUG=sl
    //             export SL_TOKEN="${params.SL_TOKEN}"
    //             export SL_LABID="${params.SL_LABID}"
    //             npm install
    //             npx jest integration-tests/nodejs-tests/Jest/test.js --sl-testStage='Jest tests' --sl-token="${params.SL_TOKEN}" --sl-labId="${params.SL_LABID}"
    //             cd ../..
    //             """
    //     }
    //   }
    // }



    stage('Mocha framework'){
      steps{
        script{
          sh """
                    sleep ${env.wait_time}
                    echo 'Mocha framework starting ..... '
                    export machine_dns="${params.MACHINE_DNS}"
                    cd ./integration-tests/nodejs-tests/mocha
                    cp -r /nodeModules/node_modules .
                    npm install
                    npm install slnodejs
                    ./node_modules/.bin/slnodejs mocha --token "${params.SL_TOKEN}" --labid "${params.SL_LABID}" --teststage 'Mocha tests'  --useslnode2 -- ./test/test.js --recursive --no-timeouts
                    cd ../..
                    """
        }
      }
    }



    stage('Soap-UI framework'){
      steps{
        script{
          sh """
            sleep ${env.wait_time}
            echo 'Soap-UI framework starting ..... '
            wget https://dl.eviware.com/soapuios/5.7.1/SoapUI-5.7.1-mac-bin.zip
            unzip SoapUI-5.7.1-mac-bin.zip
            cp integration-tests/soapUI/test-soapui-project.xml SoapUI-5.7.1/bin
            cd SoapUI-5.7.1/bin
            echo 'Downloading Sealights Agents...'
            wget -nv https://agents.sealights.co/sealights-java/sealights-java-latest.zip
            unzip -o sealights-java-latest.zip
            echo "Sealights agent version used is:" `cat sealights-java-version.txt`
            export SL_TOKEN="${params.SL_TOKEN}"
            echo ${params.SL_TOKEN}>sltoken.txt
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
            sed -i "s#machine_dns#${params.MACHINE_DNS}#" test-soapui-project.xml
            sed "s#machine_dns#${params.MACHINE_DNS}#" test-soapui-project.xml
            export SL_JAVA_OPTS="-javaagent:sl-test-listener.jar -Dsl.token=${params.SL_TOKEN} -Dsl.labId=${params.SL_LABID} -Dsl.testStage=Soapui-Tests -Dsl.log.enabled=true -Dsl.log.level=debug -Dsl.log.toConsole=true"
            sed -i -r "s/(^\\S*java)(.*com.eviware.soapui.tools.SoapUITestCaseRunner)/\\1 \\\$SL_JAVA_OPTS \\2/g" testrunner.sh
            sh -x ./testrunner.sh -s "TestSuite 1" "test-soapui-project.xml"
            """
        }
      }
    }




    stage('Pytest framework'){
      steps{
        script{
          sh"""
                sleep ${env.wait_time}
                echo 'Pytest tests starting ..... '
                export machine_dns="${params.MACHINE_DNS}"
                cd ./integration-tests/python-tests
                pip install pytest
                pip install requests
                sl-python pytest --teststage "Pytest tests"  --labid ${params.SL_LABID} --token ${params.SL_TOKEN} python-tests.py
                cd ../..
                """
        }
      }
    }
  }
}
