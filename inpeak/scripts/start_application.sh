#!/bin/bash

echo "애플리케이션 시작 (Amazon Linux 2023 - t2.micro 최적화)..."

# 환경 변수 설정
export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
export PATH=$JAVA_HOME/bin:$PATH

echo "사용 중인 Java 버전:"
java -version

# 시스템 메모리 정보 확인
echo "시스템 메모리 정보:"
free -h

# 애플리케이션 디렉토리로 이동
cd /opt/inpeak

# 8080 포트 사용 중인지 확인 및 기존 프로세스 정리
if netstat -tuln | grep -q ":8080 "; then
    echo "경고: 포트 8080이 이미 사용 중입니다. 기존 프로세스를 확인합니다..."

    # 8080 포트를 사용하는 프로세스 찾기
    EXISTING_PID=$(lsof -ti:8080)
    if [ ! -z "$EXISTING_PID" ]; then
        echo "포트 8080을 사용하는 프로세스 PID: $EXISTING_PID"
        echo "기존 프로세스를 종료합니다..."
        kill -15 $EXISTING_PID
        sleep 10

        # 여전히 실행 중이면 강제 종료
        if kill -0 $EXISTING_PID 2>/dev/null; then
            echo "강제 종료 실행..."
            kill -9 $EXISTING_PID
            sleep 5
        fi
    fi
fi

# 기존 로그 파일 백업 (선택사항)
if [ -f logs/application.log ]; then
    mv logs/application.log logs/application.log.$(date +%Y%m%d_%H%M%S)
fi

# t2.micro 최적화 JVM 옵션 설정
# RAM 1GB 환경에서 안전한 메모리 설정 (시스템 메모리의 약 50-60% 사용)
JVM_OPTS="-Xms256m -Xmx512m"  # 힙 메모리를 512MB로 제한

# t2.micro 환경에 최적화된 GC 설정
JVM_OPTS="$JVM_OPTS -XX:+UseSerialGC"  # 단일 코어에 적합한 Serial GC 사용
JVM_OPTS="$JVM_OPTS -XX:MaxGCPauseMillis=100"  # GC 중단 시간 최소화

# 메모리 효율성 개선
JVM_OPTS="$JVM_OPTS -XX:+UseStringDeduplication"
JVM_OPTS="$JVM_OPTS -XX:+OptimizeStringConcat"

# 애플리케이션 설정
JVM_OPTS="$JVM_OPTS -Dspring.profiles.active=prod"
JVM_OPTS="$JVM_OPTS -Dlogging.file.path=/opt/inpeak/logs"
JVM_OPTS="$JVM_OPTS -Dserver.port=8080"
JVM_OPTS="$JVM_OPTS -Djava.awt.headless=true"

# 디버깅 정보 최소화 (메모리 절약)
JVM_OPTS="$JVM_OPTS -XX:+UseCompressedOops"
JVM_OPTS="$JVM_OPTS -XX:+UseCompressedClassPointers"

echo "JVM 옵션 (t2.micro 최적화): $JVM_OPTS"

# 스왑 사용량 확인
echo "현재 스왑 사용량:"
swapon --show

# 애플리케이션 시작
echo "애플리케이션 실행 중..."
nohup java $JVM_OPTS -jar inpeak-backend.jar > logs/application.log 2>&1 &

# PID 저장
echo $! > application.pid

# 애플리케이션이 시작될 때까지 대기 (t2.micro는 시작이 느릴 수 있음)
echo "애플리케이션 시작 확인 중 (t2.micro 환경에서는 시간이 좀 걸릴 수 있습니다)..."
sleep 20

# 애플리케이션이 정상적으로 시작되었는지 확인
PID=$(cat application.pid)
if ps -p $PID > /dev/null 2>&1; then
    echo "애플리케이션이 성공적으로 시작되었습니다. PID: $PID"

    # 메모리 사용량 확인
    echo "애플리케이션 시작 후 메모리 사용량:"
    free -h

    # Health Check (더 긴 타임아웃)
    echo "Health Check 수행 중..."
    for i in {1..180}; do  # 3분으로 연장
        # 포트가 열렸는지 확인
        if netstat -tuln | grep -q ":8080 "; then
            echo "포트 8080이 열렸습니다."

            # HTTP Health Check 시도 (더 긴 대기)
            sleep 10
            if curl -f -m 30 http://localhost:8080/healthcheck > /dev/null 2>&1; then
                echo "애플리케이션 Health Check 성공!"
                break
            elif [ $i -eq 180 ]; then
                echo "경고: HTTP Health Check가 2분 내에 성공하지 못했지만 포트는 열려있습니다."
                echo "t2.micro 환경에서는 애플리케이션 초기화가 오래 걸릴 수 있습니다."
                echo "로그 확인:"
                tail -10 logs/application.log
            fi
        elif [ $i -eq 180 ]; then
            echo "경고: 포트 8080이 3분 내에 열리지 않았습니다."
            echo "로그 확인:"
            tail -20 logs/application.log
        fi

        # 30초마다 진행 상황 출력
        if [ $((i % 30)) -eq 0 ]; then
            echo "Health Check 진행 중... ($i/120초)"
        fi

        sleep 1
    done
else
    echo "오류: 애플리케이션 시작에 실패했습니다."
    echo "로그 확인:"
    tail -20 logs/application.log
    echo "시스템 메모리 상태:"
    free -h
    exit 1
fi

echo "애플리케이션 시작 완료"
echo "최종 시스템 상태:"
free -h
echo "프로세스 정보:"
ps aux | grep java
