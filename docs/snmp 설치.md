# snmp 설치

Ubuntu에 snmp agent를 설치하는 방법을 설명한다.  

### 스텝 1: SNMP 패키지 설치

먼저 `snmpd`와 관련된 도구들이 이미 설치되어 있는지 확인합니다. 아직 설치하지 않았다면 다음 명령으로 설치할 수 있습니다.

```bash
sudo apt update
sudo apt install snmpd
```

### 스텝 2: SNMPv3 사용자 생성

SNMPv3 관련 구성을 위해 `/etc/snmp/snmpd.d/` 디렉토리에 새로운 설정 파일을 만들고, 그 안에 SNMPv3 사용자를 정의합니다.

1. **새롭게 v3용 설정 파일 생성**:

    ```bash
    sudo vi /etc/snmp/snmpd.d/snmpv3.conf
    ```

2. **SNMPv3 사용자 및 보안 정보 추가**: 아래와 같은 내용을 이 파일에 추가합니다.

    ```plaintext
    createUser myuser SHA "MySecurePassword" AES "AnotherSecurePass"
    rouser myuser
    ```

   - `myuser`: SNMPv3 사용자 이름.
   - `"MySecurePassword"`: 인증 비밀번호 (SHA 알고리즘).
   - `"AnotherSecurePass"`: 암호화 키 (AES 알고리즘).

### 스텝 3: snmpd.conf 파일 수정

기본 `snmpd.conf`를 SNMPv2c 및 기타 버전 사용을 비활성화하도록 설정해야 합니다.

1. **기본 설정 파일 열기**:

    ```bash
    sudo vi /etc/snmp/snmpd.conf
    ```

2. **SNMP 버전 설정 변경**: 다음 줄들을 수정하거나 추가하여 SNMPv3만 사용하도록 합니다.

   - `agentAddress udp:161` 이 부분이 있으면 주석 처리해두세요.

     ```plaintext
     # agentAddress udp:161
     ```

   - SNMP 버전 2c를 비활성화하는 옵션을 추가하세요. 다음과 같은 설정을 사용할 수 있습니다.

     ```plaintext
     # rocommunity public
     ```

     이는 SNMPv1 및 v2c의 공개 커뮤니티 이름 (`public`)을 삭제하거나 주석 처리하여 사용하지 않도록 하는 방법입니다. `public` 대신 실제로 사용되고 있던 커뮤니티 문자열을 주석 처리하세요.

   - 예시

     ```conf
     sysLocation    under desk
     sysContact     keinus <keinus01@gmail.com>

     sysServices    72

     master  agentx
     # agentaddress  0.0.0.0,[::1]

     # view   systemonly  included   .1.3.6.1.2.1.1
     # view   systemonly  included   .1.3.6.1.2.1.25.1

     # rocommunity  public default -V systemonly

     rouser authPrivUser authpriv -V systemonly


     includeDir /etc/snmp/snmpd.conf.d
     ```

### 스텝 4: SNMPd 서비스 재시작

모든 설정이 완료되었다면, 변경사항을 적용하기 위해 `snmpd`를 다시 시작합니다.

```bash
sudo systemctl restart snmpd
```

### 스텝 5: SNMPv3 설정 확인

SNMPv3가 제대로 설정된지 확인하기 위해 몇 가지 테스트 명령어를 실행할 수 있습니다. 예를 들면:

```bash
snmpget -v3 -u myuser -l authPriv -a SHA -x AES -A "MySecurePassword" -X "AnotherSecurePass" localhost iso.3.6.1.2.1.1.5.0
```
