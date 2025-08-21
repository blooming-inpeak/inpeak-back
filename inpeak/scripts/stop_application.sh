#!/bin/bash

echo "애플리케이션 중지 시작 (t2.micro 환경)..."

# 함수: 특정 PID의 프로세스 안전하게 종료
safe_kill_process() {
    local pid=$1
    local process_name=$2

    if ps -p $pid > /dev/null 2>&1; then
        echo "$process_name (PID: $pid) 종료 중..."
        kill -15 $pid

        # 프로세스가 종료될 때까지 대기 (최대 30초)
        for i in {1..30}; do
            if ! ps -p $pid > /dev/null 2>&1; then
                echo "$process_name이 성공적으로 종료되었습니다."
                return 0
            fi
            sleep 1
        done

        # 여전히 실행 중이면 강제 종료
        if ps -p $pid > /dev/null 2>&1; then
            echo "$process_name 강제 종료 중..."
            kill -9 $pid
            sleep 2

            if ! ps -p $pid > /dev/null 2>&1; then
                echo "$process_name이 강제 종료되었습니다."
                return 0
            else
                echo "경고: $process_name 종료에 실패했습니다."
                return 1
            fi
        fi
    else
        echo "$process_name이 이미 종료되어 있습니다."
        return 0
    fi
}

# 1. PID 파일을 통한 종료 시도
if [ -f /opt/inpeak/application.pid ]; then
    PID=$(cat /opt/inpeak/application.pid)
    echo "PID 파일에서 발견된 애플리케이션 PID: $PID"

    safe_kill_process $PID "애플리케이션"

    # PID 파일 제거
    rm -f /opt/inpeak/application.pid
    echo "PID 파일이 제거되었습니다."
else
    echo "PID 파일이 없습니다."
fi

# 2. 8080 포트를 사용하는 프로세스 확인 및 종료
echo "8080 포트를 사용하는 프로세스 확인 중..."
if command -v lsof >/dev/null 2>&1; then
    # lsof가 설치되어 있는 경우
    PORT_PIDS=$(lsof -ti:8080)
else
    # lsof가 없는 경우 netstat 사용
    PORT_PIDS=$(netstat -tlnp 2>/dev/null | grep ':8080 ' | awk '{print $7}' | cut -d'/' -f1)
fi

if [ ! -z "$PORT_PIDS" ]; then
    echo "8080 포트를 사용하는 프로세스 발견: $PORT_PIDS"
    for pid in $PORT_PIDS; do
        if [ "$pid" != "-" ] && [ ! -z "$pid" ]; then
            safe_kill_process $pid "포트 8080 사용 프로세스"
        fi
    done
else
    echo "8080 포트를 사용하는 프로세스가 없습니다."
fi

# 3. Java 프로세스 중에서 inpeak 관련 프로세스 찾아서 종료
echo "inpeak 관련 Java 프로세스 확인 중..."
JAVA_PIDS=$(pgrep -f "inpeak-backend.jar")

if [ ! -z "$JAVA_PIDS" ]; then
    echo "inpeak 관련 Java 프로세스 발견: $JAVA_PIDS"
    for pid in $JAVA_PIDS; do
        safe_kill_process $pid "inpeak Java 프로세스"
    done
else
    echo "inpeak 관련 Java 프로세스가 없습니다."
fi

# 4. 최종 확인
sleep 3
echo "최종 확인 중..."

# 포트 8080 사용 프로세스 재확인
if netstat -tuln 2>/dev/null | grep -q ":8080 "; then
    echo "경고: 여전히 8080 포트를 사용하는 프로세스가 있습니다."
    netstat -tuln | grep ":8080"
else
    echo "8080 포트가 해제되었습니다."
fi

# inpeak 관련 Java 프로세스 재확인
if pgrep -f "inpeak-backend.jar" >/dev/null; then
    echo "경고: 여전히 inpeak 관련 Java 프로세스가 실행 중입니다."
    ps aux | grep "inpeak-backend.jar" | grep -v grep
else
    echo "모든 inpeak 관련 프로세스가 종료되었습니다."
fi

# 메모리 정리 (t2.micro에서 중요)
echo "시스템 캐시 정리 중..."
sync
echo 1 > /proc/sys/vm/drop_caches 2>/dev/null || echo "캐시 정리 권한이 없습니다 (정상)"

echo "애플리케이션 중지 완료"
echo "현재 메모리 상태:"
free -h
