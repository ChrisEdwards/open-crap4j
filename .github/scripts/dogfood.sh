#!/usr/bin/env bash

set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$repo_root"

version=$(sed -n 's/^version=//p' gradle.properties)
cli_jar="cli/build/libs/crap4j-cli-${version}.jar"
threshold=${CRAP4J_THRESHOLD:-15.0}
complexity_cap=${CRAP4J_COMPLEXITY_CAP:-15}

first_module=true
for module in core cli gradle-plugin; do
    if [[ "$first_module" == false ]]; then
        printf '\n'
    fi
    first_module=false

    java -jar "$cli_jar" check \
        --report "$module/build/reports/jacoco/test/jacocoTestReport.xml" \
        --report-name "open-crap4j:$module" \
        --baseline "$module/crap4j-baseline.json" \
        --threshold "$threshold" \
        --complexity-cap "$complexity_cap"
done
