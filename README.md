# Jaeger OpenShift Templates

Support for deploying Jaeger into OpenShift.

## Install OpenShift
If you are new to OpenShift these links help you to get it running:
* https://install.openshift.com/
* https://github.com/minishift/minishift

## All-in-one template
Template with in-memory storage with a limited functionality for local testing and development. 
Do not use this template in production environments.

To directly install everything you need:
```bash
oc process -f https://raw.githubusercontent.com/jaegertracing/jaeger-openshift/<version>/all-in-one/jaeger-all-in-one-template.yml | oc create -f -
oc delete all,template -l jaeger-infra    # to remove everything
```

## Troubleshooting
Tracer sometimes fails to resolve Jaeger's address. In such a case run:
```bash
sudo iptables -F
```
and restart affected applications.
