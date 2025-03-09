# Elasticsearch 설치

docker-compose로 3개의 클러스터를 설치

## .env 파일 작성

ELASTIC_PASSWORD=
KIBANA_PASSWORD=
STACK_VERSION=8.17.0
CLUSTER_NAME=docker-cluster
LICENSE=basic
ES_PORT=9200
KIBANA_PORT=5601
MEM_LIMIT=1073741824

## docker-compose 파일 다운로드

[docker-compose.yml](https://github.com/elastic/elasticsearch/blob/8.17/docs/reference/setup/install/docker/docker-compose.yml)

### container_name 설정

각 서비스 별 container_name을 추가해야함(기본값은 안 쓰여 있음)

## os 설정

아래 내용 참고하여 262144로 설정되어 있는지 확인.


```bash
grep vm.max_map_count /etc/sysctl.conf
vm.max_map_count=262144
```

안되어 있으면 아래 명령 실행.

```bash
sysctl -w vm.max_map_count=262144
```

## 사용자 권한

es는 기본적으로 1000:0 으로 실행함.  
로컬 볼륨 사용 시 해당 디렉토리에 권한을 아래와 같이 설정해야 함. 

```bash
mkdir esdata01
chmod g+rwx esdata01
sudo chgrp 0 esdata01
mkdir esdata02
chmod g+rwx esdata02
sudo chgrp 0 esdata02
mkdir esdata03
chmod g+rwx esdata03
sudo chgrp 0 esdata03
mkdir kibanadata
chmod g+rwx kibanadata
sudo chgrp 0 kibanadata
mkdir certs
chmod g+rwx certs
sudo chgrp 0 certs

mkdir config
chmod g+rwx config
sudo chgrp 0 config
mkdir conf.d
chmod g+rwx conf.d
sudo chgrp 0 conf.d
mkdir patterns
chmod g+rwx patterns
sudo chgrp 0 patterns
mkdir databases
chmod g+rwx databases
sudo chgrp 0 databases
```

## 자료 다운로드

```bash
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/conf.d/01-inputs.pfelk -P ./conf.d/
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/conf.d/02-firewall.pfelk -P ./conf.d/
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/conf.d/05-apps.pfelk -P ./conf.d/
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/conf.d/30-geoip.pfelk -P ./conf.d/
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/conf.d/49-cleanup.pfelk -P ./conf.d/
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/conf.d/50-outputs.pfelk -P ./conf.d/

sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/conf.d/20-interfaces.pfelk -P ./conf.d/
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/conf.d/35-rules-desc.pfelk -P ./conf.d/
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/conf.d/36-ports-desc.pfelk -P ./conf.d/
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/conf.d/37-enhanced_user_agent.pfelk -P ./conf.d/
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/conf.d/38-enhanced_url.pfelk -P ./conf.d/
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/conf.d/45-enhanced_private.pfelk -P ./conf.d/

sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/patterns/pfelk.grok -P ./patterns/
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/patterns/openvpn.grok -P ./patterns/

sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/databases/private-hostnames.csv -P ./databases/
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/databases/rule-names.csv -P ./databases/
sudo wget https://raw.githubusercontent.com/pfelk/pfelk/main/etc/pfelk/databases/service-names-port-numbers.csv -P ./databases/
```
