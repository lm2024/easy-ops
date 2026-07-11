#!/bin/sh
D="$(cd "$(dirname "$0")" && pwd)"
"$D/stop.sh"
sleep 1
"$D/start.sh"
