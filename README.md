\# 🧠 OOMAgent – JVM OOM 실시간 모니터링 에이전트



\*\*OOMAgent\*\*는 Java 애플리케이션의 \*\*Heap 메모리 사용률\*\*을 실시간으로 감시하고,  

OOM(OutOfMemoryError) 위험을 예측하여 \*\*콘솔 / 로그 파일 / MySQL DB\*\* 에 기록하는 Java Agent입니다.  

애플리케이션 코드 수정 없이 JVM 옵션 하나로 쉽게 적용할 수 있습니다.



---



\## 🚀 주요 기능



| 기능 | 설명 |

|------|------|

| 🔍 \*\*실시간 힙 모니터링\*\* | JVM Heap 사용률, 남은 메모리, 예상 OOM 시간 측정 |

| 🧾 \*\*로그 파일 기록\*\* | `oom\_agent.log` 파일에 주기적으로 기록 |

| 💾 \*\*MySQL DB 저장\*\* | 위험 상태일 때 자동으로 DB에 저장 |

| ⚙️ \*\*설정 유연성\*\* | `agentArgs` 로 모니터링 주기, 임계치, 로그 파일 경로 등을 지정 |

| 🧩 \*\*독립형 실행\*\* | 기존 코드 변경 없이 `-javaagent` 옵션만 추가하면 동작 |



---



\## 🛠️ 프로젝트 구조



```

oom-agent/

&nbsp;├── src/main/java/com/oom/OOMAgent.java

&nbsp;├── pom.xml

&nbsp;├── README.md

&nbsp;└── target/oom-agent-1.0.0.jar

```



---



\## ⚙️ pom.xml (최종)



```xml

<project xmlns="http://maven.apache.org/POM/4.0.0"

&nbsp;        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"

&nbsp;        xsi:schemaLocation="http://maven.apache.org/POM/4.0.0

&nbsp;        http://maven.apache.org/xsd/maven-4.0.0.xsd">

&nbsp;   <modelVersion>4.0.0</modelVersion>

&nbsp;   <groupId>com.oom</groupId>

&nbsp;   <artifactId>oom-agent</artifactId>

&nbsp;   <version>1.0.0</version>

&nbsp;   <packaging>jar</packaging>

&nbsp;   <name>OOMAgent</name>



&nbsp;   <dependencies>

&nbsp;       <dependency>

&nbsp;           <groupId>mysql</groupId>

&nbsp;           <artifactId>mysql-connector-java</artifactId>

&nbsp;           <version>5.1.49</version>

&nbsp;       </dependency>

&nbsp;   </dependencies>



&nbsp;   <build>

&nbsp;       <plugins>

&nbsp;           <plugin>

&nbsp;               <groupId>org.apache.maven.plugins</groupId>

&nbsp;               <artifactId>maven-shade-plugin</artifactId>

&nbsp;               <version>3.4.1</version>

&nbsp;               <executions>

&nbsp;                   <execution>

&nbsp;                       <phase>package</phase>

&nbsp;                       <goals><goal>shade</goal></goals>

&nbsp;                       <configuration>

&nbsp;                           <createDependencyReducedPom>false</createDependencyReducedPom>

&nbsp;                           <transformers>

&nbsp;                               <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">

&nbsp;                                   <mainClass>com.oom.OOMAgent</mainClass>

&nbsp;                                   <manifestEntries>

&nbsp;                                       <Premain-Class>com.oom.OOMAgent</Premain-Class>

&nbsp;                                       <Can-Redefine-Classes>true</Can-Redefine-Classes>

&nbsp;                                       <Can-Retransform-Classes>true</Can-Retransform-Classes>

&nbsp;                                   </manifestEntries>

&nbsp;                               </transformer>

&nbsp;                           </transformers>

&nbsp;                       </configuration>

&nbsp;                   </execution>

&nbsp;               </executions>

&nbsp;           </plugin>

&nbsp;       </plugins>

&nbsp;   </build>

</project>

```



---



\## 🧩 빌드 방법



```bash

mvn clean package

```



\- 결과 파일: `target/oom-agent-1.0.0.jar`  

\- MySQL 드라이버 포함 (Fat JAR)



---



\## ⚡ 실행 방법



Java 애플리케이션 실행 시 `-javaagent` 옵션 추가:



```bash

java -javaagent:/path/to/oom-agent-1.0.0.jar=use\_db=true;db\_url=jdbc:mysql://localhost:3306/monitor;db\_user=user;db\_pass=password -jar myapp.jar

```



---



\## ⚙️ Agent 설정 인자



| 옵션 | 기본값 | 설명 |

|------|----------|------|

| `interval` | `1000` | 모니터링 주기 (ms) |

| `heap` | `80.0` | 힙 사용률 임계치 (%) |

| `oom` | `30000` | 예상 OOM 임계치 (ms) |

| `use\_db` | `true` | DB 기록 여부 |

| `use\_log` | `true` | 로그 파일 기록 여부 |

| `log` | `oom\_agent.log` | 로그 파일 경로 |

| `db\_url` | `jdbc:mysql://localhost:3306/monitor` | MySQL URL |

| `db\_user` | `user` | DB 사용자 |

| `db\_pass` | `password` | DB 비밀번호 |



\*\*예시\*\*

```bash

-javaagent:oom-agent.jar=interval=5000;heap=85;oom=60000;use\_log=true;use\_db=false

```



---



\## 🧾 MySQL DDL



```sql

CREATE TABLE IF NOT EXISTS oom\_logs (

&nbsp;   id BIGINT AUTO\_INCREMENT PRIMARY KEY COMMENT '고유 식별자',

&nbsp;   timestamp DATETIME NOT NULL COMMENT '로그 시각',

&nbsp;   max\_heap\_mb INT NOT NULL COMMENT '최대 힙 메모리 (MB)',

&nbsp;   used\_heap\_mb INT NOT NULL COMMENT '사용 중 힙 메모리 (MB)',

&nbsp;   remaining\_mb INT NOT NULL COMMENT '남은 힙 메모리 (MB)',

&nbsp;   usage\_percent DOUBLE NOT NULL COMMENT '힙 사용률 (%)',

&nbsp;   heap\_threshold DOUBLE NOT NULL COMMENT '힙 임계치 (%)',

&nbsp;   est\_oom VARCHAR(50) NOT NULL COMMENT '예상 OOM 시간',

&nbsp;   oom\_threshold VARCHAR(50) NOT NULL COMMENT 'OOM 기준 임계치',

&nbsp;   status VARCHAR(10) NOT NULL COMMENT '상태 (정상 / 위험)',

&nbsp;   created\_at DATETIME DEFAULT CURRENT\_TIMESTAMP COMMENT 'DB 기록 시각',

&nbsp;   INDEX idx\_timestamp (timestamp),

&nbsp;   INDEX idx\_status (status)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OOMAgent 힙 메모리 모니터링 로그';

```



---



\## 🧩 콘솔 출력 예시



```

\[OOMAgent] Agent started

Timestamp | MaxHeap(MB) | UsedHeap(MB) | Remaining(MB) | Usage(%) | HeapThreshold(%) | EstOOM | OOMThreshold | Status

\[OOMAgent] 2025-11-06 14:03:44 | 7104 | 320 | 6784 | 4% | 80% | ∞ (무한) | 30 sec (기준) | 정상

\[OOMAgent] 2025-11-06 14:05:44 | 7104 | 6030 | 1074 | 85% | 80% | 1 min | 30 sec (기준) | 위험

```



---



\## 📊 로그 파일 예시 (`oom\_agent.log`)



```

Timestamp | MaxHeap(MB) | UsedHeap(MB) | Remaining(MB) | Usage(%) | HeapThreshold(%) | EstOOM | OOMThreshold | Status

2025-11-06 14:03:44 | 7104 | 320 | 6784 | 4% | 80% | ∞ (무한) | 30 sec (기준) | 정상

2025-11-06 14:05:44 | 7104 | 6030 | 1074 | 85% | 80% | 1 min | 30 sec (기준) | 위험

```



---



\## 🧠 DB 저장 예시 (oom\_logs)



| id | timestamp | max\_heap\_mb | used\_heap\_mb | usage\_percent | est\_oom | status |

|----|------------|--------------|---------------|----------------|----------|----------|

| 1 | 2025-11-06 14:05:44 | 7104 | 6030 | 85.0 | 1 min | 위험 |

| 2 | 2025-11-06 14:06:44 | 7104 | 4000 | 56.0 | ∞ (무한) | 정상 |



---



\## ⚠️ 주의사항



\- 로그와 DB 저장은 \*\*별도 스레드\*\*로 동작하여 메인 애플리케이션에 영향 없음  

\- Logback, SLF4J 충돌 방지를 위해 `System.out` 출력만 사용  

\- DB 연결 실패 시 예외만 출력, 메인 서비스는 정상 동작  

\- JVM 종료 시 자동 종료됨



---



\## 🧑‍💻 개발 정보



| 항목 | 내용 |

|------|------|

| Language | Java 8 이상 |

| Build Tool | Maven |

| Database | MySQL 5.7 이상 |

| Main Class | `com.oom.OOMAgent` |

| JAR Type | Java Agent (Premain-Class) |

| License | MIT |



---



\## 📄 License



```

MIT License  

Copyright (c) 2025  

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files...

```



---



✅ \*\*한 줄 요약\*\*

> JVM 메모리를 실시간 감시하고, 위험 상태를 로그 및 DB에 기록하는 경량 Java Agent  

> 실행 시 `-javaagent:oom-agent.jar` 한 줄로 완벽히 적용 가능



