#!/bin/bash

echo "파일 권한 설정 시작..."

# 애플리케이션 디렉토리 소유권 설정
chown -R ec2-user:ec2-user /opt/inpeak

# JAR 파일 실행 권한 부여
chmod +x /opt/inpeak/inpeak-backend.jar

# 로그 디렉토리 생성 및 권한 설정
mkdir -p /opt/inpeak/logs
chown ec2-user:ec2-user /opt/inpeak/logs
chmod 755 /opt/inpeak/logs

# 모든 스크립트 파일에 실행 권한 부여
find /opt/inpeak/scripts -name "*.sh" -exec chmod +x {} \;
chown -R ec2-user:ec2-user /opt/inpeak/scripts

echo "파일 권한 설정 완료"
