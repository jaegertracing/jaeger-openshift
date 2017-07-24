# Jenkins CI
Jenkins CI deploys OpenShift templates and verifies. This CI also runs functional tests from [jaeger-java-test](https://github.com/Hawkular-QE/jaeger-java-test) repository.

## How to setup Jenkins CI

### Prerequirements
* Setup [openshift](https://github.com/openshift/origin) with the user `jenkinsci`

#### Setup environment for Jenkins
Jenkins needs set of permissions to deploy/un-deploy Jaeger on Openshift. The following commands satisfy those requirements.

Login as a `root` user on the OpenShift cluster machine and execute the following commands.

```bash
oc login -u jenkinsci
oc new-project jaeger-ci
oc login -u system:admin
oc create -f https://raw.githubusercontent.com/jaegertracing/jaeger-openshift/master/production/daemonset-admin.yml
oc adm policy add-role-to-user daemonset-admin jenkinsci -n jaeger-ci
```

#### Setup Jenkins instance on OpenShift
We have two flavors of Jenkins for Openshift. 1. `jenkins-persistent`, 2. `jenkins-ephemeral`. In this document, We choose the second option. To know more about Jenkins on Openshift read [this page](https://github.com/openshift/origin/tree/master/examples/jenkins)

We tried Jenkins with default `512Mi`. Works too slow. Hence we would recommend `Memory Limit` to `1024Mi` or above.

##### Installation steps
* Open OpenShift console [Example: https://localhost:8443]
* Login as `jenkinsci` user
* Select `jaeger-ci` namespace(project).
* Click on `Add to project` and follow the screens below.

![Continuous Integration & Deployment](docs/images/jenkins-install-1.png "Select Continuous Integration & Deployment")

![jenkins-ephemeral](docs/images/jenkins-install-2.png "jenkins-ephemeral")

![jenkins-configuration](docs/images/jenkins-install-3.png "jenkins-configuration")

![jenkins-deployed](docs/images/jenkins-install-4.png "jenkins-deployed")


**Important:** Jenkins server needs permission to access `daemonset` for the project `jaeger-ci`. Login as a `root` user on the OpenShift cluster machine and execute the following commands.
```bash
oc login -u system:admin
oc adm policy add-role-to-user daemonset-admin system:serviceaccount:jaeger-ci:jenkins -n jaeger-ci
```

##### Setup tools [`maven-3.5.0` and `jdk8`]
* Launch Jenkins server: https://jenkins-jaeger.<ip>.xip.io/ Login as `developer` user
* Go to `Manage Jenkins` >> `Global Tool Configuration`(https://jenkins-jaeger.<ip>.xip.io/configureTools/)
* Add `maven` and `jdk` tool. Which is used in [Jenkins pipeline file](/JenkinsfileOpenShift)
* JDK 8 should be used with the name of `jdk8`
* Maven 3.5.0 should be used in the name of `maven-3.5.0`

![jdk-tool](docs/images/jenkins-tools-jdk8.png "jdk tool")

![maven-tool](docs/images/jenkins-tools-maven-3_5_0.png "maven tool")


##### Create Jenkins job with existing pipeline scripts
Jenkins pipeline script is located [here](/jenkins-ci/Jenkinsfile)

* Login to Jenkins server [Example: https://jenkins-jaeger.<ip>.xip.io/] with the Openshift user `jenkinsci`
* Click on `New Item`
* Enter `Enter an item name` (example: jaegertracing-ci)
* Select project type as `GitHub Organization` and click `OK`
* Under the section `Projects` >> `GitHub Organization`
	* Set `Owner` as `jaegertracing`
	* Set `Repository name pattern` as `jaeger-openshift`
	* Set `Include branches` as `master`
	* Set `Project Recognizers` >> `Script Path` as `jenkins-ci/Jenkinsfile`
* Set `Scan Organization Triggers` >> `Periodically if not otherwise run` >> `Interval` as `1 minute`
* Save the project

All set ready :)