#!/bin/bash
REQUIRED_PER_RUN=8 # GB
MIN_FREE_GB=5

for config in $(ls configs); do
    AVAILABLE_GB=$(df -g / | awk 'NR==2 {print $4}')
    REQUIRED_THIS_RUN=$((REQUIRED_PER_RUN + MIN_FREE_GB))

    if (( AVAILABLE_GB < REQUIRED_THIS_RUN )); then
        echo "Run $config: Not enough free space. Available: ${AVAILABLE_GB} GB. Required: ${REQUIRED_THIS_RUN} GB. Skipping."
        continue
    fi

    echo "Run $config: Enough space available (${AVAILABLE_GB} GB). Running script..."
    python3 simulation.py configs/${config}
done
