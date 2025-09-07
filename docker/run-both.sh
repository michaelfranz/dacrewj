#!/bin/sh
set -eu

# Validate JIRA token for agent
if [ "${JIRA_TOKEN:-}" = "" ] && [ ! -f "${JIRA_TOKEN_FILE:-}" ]; then
  echo "ERROR: JIRA_TOKEN not set and JIRA_TOKEN_FILE not provided. Set JIRA_TOKEN or JIRA_TOKEN_FILE." >&2
  exit 1
fi
if [ "${JIRA_TOKEN:-}" = "" ] && [ -f "${JIRA_TOKEN_FILE:-}" ]; then
  export JIRA_TOKEN="$(cat "${JIRA_TOKEN_FILE}")"
fi

# Setup logs directory
LOG_DIR=${DACREW_LOG_DIR:-/app/logs}
mkdir -p "$LOG_DIR"

# Start jira_ingester on 8081
INGESTER_PORT=${INGESTER_PORT:-8081}
INGESTER_CMD="java $JAVA_OPTS $INGESTER_JAVA_OPTS -Dserver.port=${INGESTER_PORT} -jar /app/ingester.jar"
echo "Starting jira_ingester on port ${INGESTER_PORT}..."
sh -c "$INGESTER_CMD" >"$LOG_DIR/ingester.log" 2>&1 &
INGESTER_PID=$!

# Start agent on 8080
AGENT_PORT=${AGENT_PORT:-8080}
AGENT_CMD="java $JAVA_OPTS $AGENT_JAVA_OPTS -Dserver.port=${AGENT_PORT} -jar /app/agent.jar"
echo "Starting agent on port ${AGENT_PORT}..."
sh -c "$AGENT_CMD" >"$LOG_DIR/agent.log" 2>&1 &
AGENT_PID=$!

# Trap and forward signals, wait on children
term_handler() {
  echo "Received termination signal. Stopping services..."
  kill "$INGESTER_PID" "$AGENT_PID" 2>/dev/null || true
}
trap term_handler TERM INT

# Tail logs in background for visibility
( tail -n +1 -F "$LOG_DIR/ingester.log" "$LOG_DIR/agent.log" & )

# Wait for processes
wait "$INGESTER_PID"
STATUS1=$?
wait "$AGENT_PID"
STATUS2=$?

if [ "$STATUS1" -ne 0 ] || [ "$STATUS2" -ne 0 ]; then
  echo "One or more services exited with a non-zero status: ingester=$STATUS1 agent=$STATUS2" >&2
  exit 1
fi
