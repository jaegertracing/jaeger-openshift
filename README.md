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
This template deploys the Collector, Query Service (with UI) and Cassandra storage (StatefulSet) as separate individually scalable services.

Install everything in `jaeger-infra` namespace:
```bash
oc new-project jaeger-infra
oc process -f https://raw.githubusercontent.com/jaegertracing/jaeger-openshift/master/production/jaeger-production-template.yml | oc create -n jaeger-infra -f -
```

Note that it's OK to have the Query and Collector pods to be in an error state for the first minute or so. This is
because these components attempt to connect to Cassandra right away and hard fail if they can't after N attempts.

### Deploying the agent as sidecar
The Jaeger Agent is designed to be deployed local to your service, so that it can receive traces via UDP keeping your
application's load minimal. As such, it's ideal to have the Agent to be deployed as a sidecar to your application's component,
just add it as a container within any struct that supports `spec.containers`, like a `Pod`, `Deployment` and so on.

For instance, assuming that your application is named `myapp` and the image is for it is `openshift/hello-openshift`, your
`Deployment` descriptor would be something like:

```yaml
- apiVersion: extensions/v1beta1
  kind: Deployment
  metadata:
    name: myapp
  spec:
    template:
      metadata:
        labels:
          app: myapp
      spec:
        containers:
        - image: openshift/hello-openshift
          name: myapp
          ports:
          - containerPort: 8080
        - image: jaegertracing/jaeger-agent
          name: jaeger-agent
          ports:
          - containerPort: 5775
            protocol: UDP
          - containerPort: 5778
          - containerPort: 6831
            protocol: UDP
          - containerPort: 6832
            protocol: UDP
          command:
          - "/go/bin/agent-linux"
          - "--collector.host-port=jaeger-collector.jaeger-infra.svc:14267"
```

The Jaeger Agent will then be available to your application at `localhost:5775`/`localhost:6831`/`localhost:6832`.
In most cases, you don't need to specify a hostname or port to your Jaeger Tracer, as it will default to the right
values already.

### Persistent storage
Even though this template uses a stateful Cassandra, backing storage is set to `emptyDir`. It's more
appropriate to create a `PersistentVolumeClaim`/`PersistentVolume` and use it instead. Note that this
Cassandra deployment does not support deleting pods or scaling down, as this might require
administrative tasks that are dependent on the final deployment architecture.

## Using a different version
The templates are using the `latest` version, which is what you probably want at this stage. If you need to
use a specific Docker image version, specify it via the template parameter `IMAGE_VERSION`, as follows:

```bash
oc process -f <path-to-template> -p IMAGE_VERSION=<sha> | oc create -n jaeger-infra -f -
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
