#!/bin/bash

# Define variables for app name, artifact ID, and version
ONOS_APP_NAME="nycu.winlab.groupmeter"          # Replace with the actual ONOS app name
ARTIFACT_ID="groupmeter-1.0-SNAPSHOT"              # Replace with the actual artifact ID

# Run Maven build and check if it succeeds
if mvn clean install -DskipTests; then
    echo "Maven build succeeded, proceeding with ONOS commands."

    # Deactivate the ONOS app
    onos-app localhost deactivate "$ONOS_APP_NAME"
    # Uninstall the ONOS app
    onos-app localhost uninstall "$ONOS_APP_NAME"
    # Install the new version of the app
    onos-app localhost install! target/"$ARTIFACT_ID".oar
else
    echo "Maven build failed. Exiting script."
    exit 1
fi