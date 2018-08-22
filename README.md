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

### Backing storage
The Jaeger Collector and Query require a backing storage to exist before being started up. As a starting point for your own 
templates, we provide a basic template to deploy Cassandra. It is not ready for production and should be adapted before any
real usage.

#### Cassandra
To use our Cassandra template:

    oc create -f production/cassandra.yml
    oc create -f production/configmap-cassandra.yml

The Cassandra template includes also a Kubernetes `Job` that creates the schema required by the Jaeger components. It's advisable
to wait for this job to finish before deploying the Jaeger components. To check the status of the job, run:

    oc get job jaeger-cassandra-schema-job

The job should have `1` in the `SUCCESSFUL` column.

#### Elasticsearch
To use our Elasticsearch template:

    oc create -f production/elasticsearch.yml
    oc create -f production/configmap-elasticsearch.yml

The Elasticsearch template in this repository deploys only one node and overall it's not production quality!
We encourage you to use other templates, for example [docker-rhel-elasticsearch](https://github.com/RHsyseng/docker-rhel-elasticsearch).
This Elasticsearch deployment is also used by integration tests for this repository.

### Jaeger configuration
The Jaeger Collector, Query and Agent require a `ConfigMap` to exist on the same namespace, named `jaeger-configuration`.
This `ConfigMap` is included in the storage templates, as each backing storage have their own specific configuration entries,
but in your environment, you'll probably manage it differently.

If changes are required for the configuration, the `edit` command can be used:

    oc edit configmap jaeger-configuration

### Jaeger components
The main production template deploys the Collector and the Query Service (with UI) as separate individually scalable services.

    oc process -f production/jaeger-production-template.yml | oc create -f -

If the backing storage is not ready by the time the Collector/Agent start, they will fail and Kubernetes will reschedule the
pod. It's advisable to either wait for the backing storage to estabilize, or to ignore such failures for the first few minutes.

Once everything is ready, the Jaeger Query service location can be discovered by running:

    oc get route jaeger-query

It should be similar to: https://jaeger-query-myproject.127.0.0.1.nip.io

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

## Exposing Jaeger-Collector outside of Cluster
Collector is by default accessible only to services running inside the cluster. 
The easiest approach to expose the collector outside of the cluster is via the `jaeger-collector-http`
HTTP port using an OpenShift Route:

```bash
oc create route edge --service=jaeger-collector --port jaeger-collector-http --insecure-policy=Allow
```

This allows clients to send data directly to Collector via HTTP senders. If you want to use the Agent then use
[ExternalIP or NodePort](https://docs.openshift.com/container-platform/3.3/dev_guide/getting_traffic_into_cluster.html)
to expose the Collector service.

Note that doing so will open the collector to be used by any external party, who will then 
be able to create arbitrary spans. 
It's advisable to put an OAuth Security Proxy in front of the collector and expose this proxy instead.

## Using a different version
The templates are using a specific version and `latest` in `all-in-one` template. If you need to
use a different Docker image version, specify it via the template parameter `IMAGE_VERSION`, as follows:

```bash
oc process -f <path-to-template> -p IMAGE_VERSION=<version> | oc create -n jaeger-infra -f -
```

A list of tags can be found here:
https://hub.docker.com/r/jaegertracing/

## Getting an OpenShift cluster running
As a developer looking to try this out locally, the easiest is to use the `oc cluster up` command. Getting
this command might be as easy as running `dnf install origin-clients` on a recent Fedora desktop. Refer to
the OpenShift [installation guide or quick start guide](https://install.openshift.com/) for more information.
Another alternative is to use [`minishift`](https://github.com/minishift/minishift).

## Uninstalling
If you need to remove the all Jaeger components created by this template, run:

```bash
oc delete all,template,configmap -l jaeger-infra
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

## License
[Apache-2.0 License](./LICENSE)
