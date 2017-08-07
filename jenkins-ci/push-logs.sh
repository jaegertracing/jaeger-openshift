#!/bin/sh
#
# Copyright 2017 The Jaeger Authors
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
# in compliance with the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# or implied. See the License for the specific language governing permissions and limitations under
# the License.
#

JOB_STATUS=$1
if [ -n "${CHANGE_URL}" ]; then
  # download jq file if it is not available
  if [ ! -e /tmp/jq-linux64 ]; then
    echo "jq file not found! downloading jq to '/tmp/jq-linux64'"
    curl -sL https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64 > /tmp/jq-linux64
    chmod +x /tmp/jq-linux64
  fi
  if [ ! -e jq ]; then
    echo "jq file 'soft link' not found! creating it on '$(pwd)/jq'"
    ln -s /tmp/jq-linux64 jq
  fi
  ./jq --version
  consoleUrl=$(echo "${BUILD_URL}consoleText")
  echo "URL:[console:${consoleUrl}]"
  consoleLogJson=$(curl -G ${consoleUrl} --insecure | ./jq --slurp --raw-input '.')
  echo "{\"title\": \"${JOB_NAME}:build#${BUILD_ID}\", \"contents\": $consoleLogJson}" > consolelog.json
  echo "Job status: ${JOB_STATUS}"
  fpasteResponse=$(curl -X POST -H "Content-Type:application/json" https://paste.fedoraproject.org/api/paste/submit -d @consolelog.json)
  FPASTE_URL=$(echo $fpasteResponse | ./jq '.url' -r)
  if [ "${JOB_STATUS}" = "FAILURE" ]; then
    BUILD_STATUS_ICON=":red_circle:"
  elif [ "${JOB_STATUS}" = "SUCCESS" ]; then
    BUILD_STATUS_ICON=":white_check_mark:"
  else
    BUILD_STATUS_ICON=":question:"
  fi
  echo "Console log on fpaste: ${FPASTE_URL}"
  curl -H "Content-Type: application/json" \
    -u $AUTHTOKEN \
    -X POST \
    -d "{\"body\":\"${BUILD_STATUS_ICON} Jenkins CI Build\`#${BUILD_ID}\`: Console log: [external](${FPASTE_URL}), [internal](${RUN_DISPLAY_URL})\"}" \
    "$(echo $CHANGE_URL | sed 's/github.com/api.github.com\/repos/g; s/pull\//issues\//g;')/comments"
  env
else
  echo "Looks like this not a PR request!"
fi
