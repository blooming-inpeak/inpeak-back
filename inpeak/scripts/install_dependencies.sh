#!/bin/bash

echo "의존성 설치 시작 (Amazon Linux 2023 - t2.micro 최적화)..."

# 시스템 업데이트 (t2.micro에서는 리소스를 절약하기 위해 최소한으로)
echo "시스템 업데이트 중..."
dnf update -y --security  # 보안 업데이트만 적용

# 필수 도구들 설치
echo "필수 도구 설치 중..."
dnf install -y wget curl unzip lsof net-tools htop

# Java 21이 설치되어 있는지 확인
if ! java -version 2>&1 | grep -q "21"; then
    echo "Java 21 설치 중..."

    # Amazon Corretto 21 설치
    dnf install -y java-21-amazon-corretto java-21-amazon-corretto-devel

    # JAVA_HOME 설정
    echo 'export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto' >> /etc/environment
    echo 'export PATH=$JAVA_HOME/bin:$PATH' >> /etc/environment

    # 현재 세션에서도 적용
    export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
    export PATH=$JAVA_HOME/bin:$PATH

    echo "Java 21 설치 완료"
else
    echo "Java 21이 이미 설치되어 있습니다."
    # JAVA_HOME이 설정되어 있는지 확인
    if [ -z "$JAVA_HOME" ]; then
        echo "JAVA_HOME 환경변수 설정..."
        export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto
        echo 'export JAVA_HOME=/usr/lib/jvm/java-21-amazon-corretto' >> /etc/environment
        echo 'export PATH=$JAVA_HOME/bin:$PATH' >> /etc/environment
    fi
fi

# 애플리케이션 디렉토리 생성
mkdir -p /opt/inpeak
mkdir -p /opt/inpeak/logs

# t2.micro 환경을 위한 스왑 메모리 확인 및 설정
echo "스왑 메모리 상태 확인..."
if ! swapon --show | grep -q "/"; then
    echo "스왑 메모리가 설정되지 않았습니다. 2GB 스왑 파일을 생성합니다..."

    # 2GB 스왑 파일 생성
    dd if=/dev/zero of=/swapfile bs=1M count=2048
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile

    # 부팅 시 자동으로 스왑 활성화
    echo '/swapfile none swap sw 0 0' >> /etc/fstab

    echo "2GB 스왑 메모리가 설정되었습니다."
else
    echo "스왑 메모리가 이미 설정되어 있습니다:"
    swapon --show
fi

# t2.micro 최적화: 스왑 사용 빈도 조절
echo "vm.swappiness=10" >> /etc/sysctl.conf
sysctl vm.swappiness=10

# CodeDeploy 에이전트가 실행 중인지 확인
if ! systemctl is-active --quiet codedeploy-agent; then
    echo "CodeDeploy 에이전트 확인 중..."

    # CodeDeploy 에이전트가 설치되어 있지 않으면 설치
    if ! which codedeploy-agent >/dev/null 2>&1; then
        echo "CodeDeploy 에이전트 설치 중..."
        dnf install -y ruby
        cd /tmp
        wget https://aws-codedeploy-ap-northeast-2.s3.ap-northeast-2.amazonaws.com/latest/install
        chmod +x ./install
        ./install auto
    fi

    echo "CodeDeploy 에이전트 시작..."
    systemctl start codedeploy-agent
    systemctl enable codedeploy-agent
else
    echo "CodeDeploy 에이전트가 이미 실행 중입니다."
fi

# 시스템 메모리 상태 출력
echo "시스템 리소스 상태:"
echo "=== 메모리 정보 ==="
free -h
echo "=== 스왑 정보 ==="
swapon --show
echo "=== 디스크 사용량 ==="
df -h /

# 로그 로테이션 설정 (t2.micro에서 디스크 공간 절약)
echo "로그 로테이션 설정..."
cat > /etc/logrotate.d/inpeak << 'EOF'
/opt/inpeak/logs/*.log {
    daily
    rotate 7
    compress
    delaycompress
    missingok
    notifempty
    create 644 ec2-user ec2-user
    postrotate
        # 애플리케이션이 실행 중이면 로그 파일 재오픈 신호 전송
        if [ -f /opt/inpeak/application.pid ]; then
            kill -USR1 $(cat /opt/inpeak/application.pid) 2>/dev/null || true
        fi
    endscript
}
EOF

echo "의존성 설치 완료"
echo "최종 시스템 상태:"
free -h
