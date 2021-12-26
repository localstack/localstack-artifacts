#!/bin/bash

# simple script to download public event schemas from AWS

names=$(aws schemas list-schemas --registry-name aws.events --query 'Schemas[] | sort_by(@, &SchemaName) | [][SchemaName,VersionCount]' | jq -r '.[] | join("_v")')

mkdir -p schemas/
for name in $names; do
  schemaName=$(echo "$name" | sed 's/_v.*//')
  schemaFile=schemas/$name.json
  if [ ! -e "$schemaFile" ]; then
    echo "Downloading schema $schemaName ..."
    aws schemas describe-schema --registry-name aws.events --schema-name "$schemaName" > "$schemaFile"
  fi
done

# create zip file
(cd schemas && zip schemas.zip *.json)
mv schemas/schemas.zip .
