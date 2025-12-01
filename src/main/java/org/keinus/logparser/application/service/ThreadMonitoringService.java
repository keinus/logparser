package org.keinus.logparser.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keinus.logparser.infrastructure.persistence.entity.InputAdapterEntity;
import org.keinus.logparser.infrastructure.persistence.entity.OutputAdapterEntity;
import org.keinus.logparser.infrastructure.persistence.entity.ParserEntity;
import org.keinus.logparser.infrastructure.persistence.repository.InputAdapterRepository;
import org.keinus.logparser.infrastructure.persistence.repository.OutputAdapterRepository;
import org.keinus.logparser.infrastructure.persistence.repository.ParserRepository;
import org.keinus.logparser.infrastructure.util.ThreadManager;
import org.keinus.logparser.interfaces.rest.dto.response.ThreadDetailDto;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 스레드 모니터링 서비스
 * ThreadManager에서 스레드 정보를 가져와서 각 컴포넌트와 매핑합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThreadMonitoringService {

    private final ThreadManager threadManager;
    private final InputAdapterRepository inputAdapterRepository;
    private final OutputAdapterRepository outputAdapterRepository;
    private final ParserRepository parserRepository;

    // Input adapter 스레드 패턴: {AdapterType}InputAdapter-{number}
    // 예: HttpInputAdapter-1, KafkaInputAdapter-2
    private static final Pattern INPUT_ADAPTER_PATTERN = Pattern.compile("(.+InputAdapter)-(\\d+)");
    private static final Pattern PARSER_PATTERN = Pattern.compile("LogParser-(\\d+)");

    /**
     * 모든 활성 스레드의 상세 정보를 반환합니다.
     */
    public List<ThreadDetailDto> getAllThreadDetails() {
        List<ThreadDetailDto> details = new ArrayList<>();
        List<ThreadManager.ThreadInfo> threadInfos = threadManager.getAllThreadInfo();

        // 어댑터 및 파서 정보를 미리 로드
        Map<Long, InputAdapterEntity> inputAdaptersById = loadInputAdapters();
        Map<Long, OutputAdapterEntity> outputAdaptersById = loadOutputAdapters();
        List<ParserEntity> parsers = parserRepository.findAll();

        for (ThreadManager.ThreadInfo threadInfo : threadInfos) {
            ThreadDetailDto detail = mapThreadInfoToDetail(
                    threadInfo,
                    inputAdaptersById,
                    outputAdaptersById,
                    parsers
            );
            details.add(detail);
        }

        return details;
    }

    /**
     * ThreadInfo를 ThreadDetailDto로 매핑합니다.
     */
    private ThreadDetailDto mapThreadInfoToDetail(
            ThreadManager.ThreadInfo threadInfo,
            Map<Long, InputAdapterEntity> inputAdaptersById,
            Map<Long, OutputAdapterEntity> outputAdaptersById,
            List<ParserEntity> parsers
    ) {
        String threadName = threadInfo.getName();

        ThreadDetailDto.ThreadDetailDtoBuilder builder = ThreadDetailDto.builder()
                .name(threadName)
                .threadId(threadInfo.getId())
                .state(threadInfo.getState().toString())
                .alive(threadInfo.isAlive())
                .interrupted(threadInfo.isInterrupted());

        // 스레드 이름을 기반으로 컴포넌트 타입 결정
        if (INPUT_ADAPTER_PATTERN.matcher(threadName).matches()) {
            // {Type}InputAdapter-{number} 형식의 스레드
            mapInputAdapterThread(builder, threadName, inputAdaptersById);
        } else if (threadName.equals("processOutputAdapter")) {
            mapOutputAdapterThread(builder, outputAdaptersById);
        } else if (threadName.startsWith("LogParser-")) {
            mapParserThread(builder, threadName, parsers);
        } else if (threadName.equals("BatchFlushScheduler")) {
            mapBatchThread(builder);
        } else if (threadName.equals("QueueMonitor")) {
            mapMonitorThread(builder);
        } else if (threadName.equals("DeadLetterQueueFlusher")) {
            mapDLQFlushThread(builder);
        } else {
            // 알 수 없는 스레드
            builder.componentType("UNKNOWN");
        }

        return builder.build();
    }

    private void mapInputAdapterThread(
            ThreadDetailDto.ThreadDetailDtoBuilder builder,
            String threadName,
            Map<Long, InputAdapterEntity> inputAdaptersById
    ) {
        builder.componentType("INPUT");

        // 스레드 이름에서 adapter 타입과 카운터 추출
        // 예: HttpInputAdapter-1 -> group(1)="HttpInputAdapter", group(2)="1"
        Matcher matcher = INPUT_ADAPTER_PATTERN.matcher(threadName);
        if (matcher.matches()) {
            String adapterTypeName = matcher.group(1);  // 예: "HttpInputAdapter"
            String threadNumber = matcher.group(2);     // 예: "1"

            // 기본 컴포넌트 이름 설정 (어댑터 타입 + 스레드 번호)
            builder.componentName(adapterTypeName + " #" + threadNumber);

            // 어댑터 타입에서 실제 타입명 추출 (예: HttpInputAdapter -> HTTP)
            String adapterType = extractAdapterType(adapterTypeName);

            // 해당 타입의 어댑터 찾기
            InputAdapterEntity matchedAdapter = findMatchingInputAdapter(
                    inputAdaptersById,
                    adapterType
            );

            if (matchedAdapter != null) {
                builder.componentId(matchedAdapter.getId());
                builder.componentName(
                    matchedAdapter.getMessagetype() +
                    " (" + matchedAdapter.getType() + ") #" + threadNumber
                );
                builder.componentConfig(buildInputAdapterConfig(matchedAdapter));
            } else {
                // 매칭되는 어댑터를 찾지 못한 경우에도 기본 정보 표시
                Map<String, Object> basicConfig = new HashMap<>();
                basicConfig.put("type", adapterType);
                basicConfig.put("threadNumber", threadNumber);
                builder.componentConfig(basicConfig);
            }
        }
    }

    /**
     * 어댑터 클래스 이름에서 타입 추출
     * 예: HttpInputAdapter -> HTTP, KafkaInputAdapter -> KAFKA
     */
    private String extractAdapterType(String adapterClassName) {
        if (adapterClassName == null) {
            return "UNKNOWN";
        }
        // "InputAdapter" 제거
        String typeName = adapterClassName.replace("InputAdapter", "");
        // 대문자로 변환 (예: Http -> HTTP, Kafka -> KAFKA)
        return typeName.toUpperCase();
    }

    /**
     * 타입으로 Input Adapter 찾기
     */
    private InputAdapterEntity findMatchingInputAdapter(
            Map<Long, InputAdapterEntity> inputAdaptersById,
            String adapterType
    ) {
        for (InputAdapterEntity adapter : inputAdaptersById.values()) {
            if (adapter.getType() != null &&
                adapter.getType().equalsIgnoreCase(adapterType)) {
                return adapter;
            }
        }
        return null;
    }

    private void mapOutputAdapterThread(
            ThreadDetailDto.ThreadDetailDtoBuilder builder,
            Map<Long, OutputAdapterEntity> outputAdaptersById
    ) {
        builder.componentType("OUTPUT");
        builder.componentName("Output Message Processor");

        // Output adapter는 단일 스레드이므로 모든 어댑터 정보를 메타데이터에 추가
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("activeAdapters", outputAdaptersById.size());
        builder.metadata(metadata);
    }

    private void mapParserThread(
            ThreadDetailDto.ThreadDetailDtoBuilder builder,
            String threadName,
            List<ParserEntity> parsers
    ) {
        builder.componentType("PARSER");

        Matcher matcher = PARSER_PATTERN.matcher(threadName);
        if (matcher.matches()) {
            String threadNumber = matcher.group(1);
            builder.componentName("Parser Worker #" + threadNumber);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("threadNumber", threadNumber);
            metadata.put("totalParsers", parsers.size());
            builder.metadata(metadata);
        }
    }

    private void mapBatchThread(ThreadDetailDto.ThreadDetailDtoBuilder builder) {
        builder.componentType("BATCH");
        builder.componentName("Batch Flush Scheduler");
    }

    private void mapMonitorThread(ThreadDetailDto.ThreadDetailDtoBuilder builder) {
        builder.componentType("MONITOR");
        builder.componentName("Queue Monitor");
    }

    private void mapDLQFlushThread(ThreadDetailDto.ThreadDetailDtoBuilder builder) {
        builder.componentType("MONITOR");
        builder.componentName("Dead Letter Queue Flusher");
    }

    private Map<String, Object> buildInputAdapterConfig(InputAdapterEntity adapter) {
        Map<String, Object> config = new HashMap<>();
        config.put("type", adapter.getType());
        config.put("enabled", adapter.getEnabled());

        if (adapter.getPort() != null) {
            config.put("port", adapter.getPort());
        }
        if (adapter.getHost() != null) {
            config.put("host", adapter.getHost());
        }
        if (adapter.getTopicid() != null) {
            config.put("topic", adapter.getTopicid());
        }
        if (adapter.getPath() != null) {
            config.put("path", adapter.getPath());
        }
        if (adapter.getBootstrapservers() != null) {
            config.put("bootstrapServers", adapter.getBootstrapservers());
        }

        return config;
    }

    private Map<Long, InputAdapterEntity> loadInputAdapters() {
        List<InputAdapterEntity> adapters = inputAdapterRepository.findAll();
        Map<Long, InputAdapterEntity> map = new HashMap<>();
        for (InputAdapterEntity adapter : adapters) {
            map.put(adapter.getId(), adapter);
        }
        return map;
    }

    private Map<Long, OutputAdapterEntity> loadOutputAdapters() {
        List<OutputAdapterEntity> adapters = outputAdapterRepository.findAll();
        Map<Long, OutputAdapterEntity> map = new HashMap<>();
        for (OutputAdapterEntity adapter : adapters) {
            map.put(adapter.getId(), adapter);
        }
        return map;
    }
}
