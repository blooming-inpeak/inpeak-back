#!/bin/bash

ROOT_PATH="/home/ec2-user/inpeak-back/inpeak"
JAR="$ROOT_PATH/application.jar"
STOP_LOG="$ROOT_PATH/stop.log"
SERVICE_PID=$(pgrep -f $JAR)

NOW=$(date +%c)

if [ -z "$SERVICE_PID" ]; then
  echo "[$NOW] 서비스 NotFound" >> $STOP_LOG
else
  echo "[$NOW] 서비스 종료 PID: $SERVICE_PID" >> $STOP_LOG
  kill "$SERVICE_PID"
  
  # 종료 확인 (최대 30초 대기)
  for i in {1..30}; do
    if ! kill -0 "$SERVICE_PID" 2>/dev/null; then
      echo "[$NOW] 서비스 정상 종료됨" >> $STOP_LOG
      break
    fi
    sleep 1
  done
fi
