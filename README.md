# Jaeger OpenShift Templates

## Development setup
This template uses an in-memory storage with a limited functionality for local testing and development.
Do not use this template in production environments.

Install everything in the current namespace:
```bash
oc process -f https://raw.githubusercontent.com/jaegertracing/jaeger-openshift/master/all-in-one/jaeger-all-in-one-template.yml | oc create -f -
```

Once everything is ready, `oc status` tells you where to find Jaeger URL.

## Production setup
This template deploys all Jaeger components as a separate services: StatefulSet Cassandra storage, agent as DaemonSet,
collector and query service with UI. Each one of those can be managed and scaled individually. Because this template
deploys the agent as a DaemonSet it requires system:admin permissions, therefore cannot be used in OpenShift online.

Install everything in `jaeger` namespace:
```bash
oc login -u system:admin
oc new-project jaeger
oc process -f https://raw.githubusercontent.com/jaegertracing/jaeger-openshift/master/production/jaeger-production-template.yml | oc create -n jaeger -f -
```

Give user `developer` permissions to access `jaeger` namespace:
```bash
oc policy add-role-to-user cluster-admin developer -n jaeger
```

Note that it's OK to have the Query and Collector pods to be in an error state for the first minute or so. This is
because these components attempt to connect to Cassandra right away and hard fail if they can't after N attempts.

Your Agent hostname is `jaeger-agent.${NAMESPACE}.svc.cluster.local`

### Install Jaeger as `developer` user

Jaeger can also be installed as `developer` (or any other user). As the DaemonSet runs at the node level,
however, your user need to have such permission. It can be achieved by running this:

```bash
oc login -u developer
oc new-project jaeger

oc login -u system:admin
oc create -f https://raw.githubusercontent.com/jaegertracing/jaeger-openshift/master/production/daemonset-admin.yml
oc adm policy add-role-to-user daemonset-admin developer -n jaeger // jaeger namespace has been already created and it is accessible by developer user
```

Once that is ready, it's only a matter of creating the components from the template:
```bash
oc login -u developer
oc process -f https://raw.githubusercontent.com/jaegertracing/jaeger-openshift/master/production/jaeger-production-template.yml | oc create -n jaeger -f -
```

### Persistent storage
Even though this template uses a stateful Cassandra, backing storage is set to `emptyDir`. It's more
appropriate to create a `PersistentVolumeClaim`/`PersistentVolume` and use it instead.

## Using a different version

The templates are using the `latest` version, which is what you probably want at this stage. If you need to
use a specific Docker image version, specify it via the template parameter `IMAGE_VERSION`, as follows:

```bash
oc process -f <path-to-template> -p IMAGE_VERSION=<sha> | oc create -n jaeger -f -
```

A list of tags can be found here:
https://hub.docker.com/r/jaegertracing/all-in-one/tags/

Note that the Docker image tags are related to the git commit SHAs:
`IMAGE_VERSION` 6942fec0 is the Docker image for https://github.com/uber/jaeger/commit/6942fec
`IMAGE_VERSION` latest is the Docker image for `master`

## Getting an OpenShift cluster running

As a developer looking to try this out locally, the easiest is to use the `oc cluster up` command. Getting
this command might be as easy as running `dnf install origin-clients` on a recent Fedora desktop. Refer to
the OpenShift [installation guide or quick start guide](https://install.openshift.com/) for more information.
Another alternative is to use [`minishift`](https://github.com/minishift/minishift).

## Uninstalling

If you need to remove the Jaeger components created by this template, run:

```bash
oc delete all,template,daemonset -l jaeger-infra
```

## Testing
Tests are based on [Arquillian Cube](http://arquillian.org/arquillian-cube/) which require an active connection to
openshift cluster (via `oc`). Currently all templates are tested on minishift or local all-in-one cluster (`oc cluster
up`).

```bash
minishift start // or oc cluster up
mvn clean verify -Pe2e
```

## Troubleshooting
Tracer may sometimes fail to resolve Jaeger's address. In such case run the following command and restart the affected applications.:
```bash
sudo iptables -F
```
