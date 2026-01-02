package com.tikitaka.api.batch.goods;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class BatchInspectionScheduler {
	
    @Value("${batch.size-per-minute}")
    private String batchSizePerMinute;

    private final GoodsBatchService goodsBatchService;
    
    // 3시부터 배치서버 동작, 매일 3-5시 사이 15분마다 실행
    @Scheduled(cron = "0 */15 3-5 * * *")
    public void gatherS3Data() {
        log.info("========== S3 데이터 수집 스케줄러 시작 ==========");
        String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        try {
        	// 배치가 수행된이력이 있는지 확인 후, 수행되었다면 skip
        	String result = goodsBatchService.getBatchInStatus(todayDate);
        	if(result.equals("NONE")) {
        		goodsBatchService.recordBatchInStatus("PENDING");
        		int processedCount = goodsBatchService.gatherS3Data(todayDate);
        		
        		if (processedCount > 0) {
                    // 3-A. 처리된 파일이 있다면 SUCCESS로 확정
                    goodsBatchService.recordBatchInStatus("SUCCESS");
                    log.info("배치 처리 완료. 상태를 SUCCESS로 업데이트했습니다.");
                } else {
                    // 3-B. 처리된 파일이 없다면(아직 업로드가 안된 경우), PENDING 상태를 취소(삭제)
                    //      그래야 다음 15분 뒤 스케줄러가 다시 'NONE' 상태를 보고 진입할 수 있음
                    goodsBatchService.cancelBatchInStatus(todayDate);
                    log.info("처리할 파일이 없어 배치 상태를 초기화(삭제)했습니다. 다음 스케줄에 재시도합니다.");
                }
        	}
        } catch (Exception e) {
    		goodsBatchService.recordBatchInStatus("FAILED");
            log.error("S3 데이터 수집 중 오류 발생", e);
        } finally {
            log.info("========== S3 데이터 수집 스케줄러 종료 ==========");
        }
    }

    
    // 1분마다 실행 (cron = "초 분 시 일 월 요일")
	@Scheduled(cron = "0 */1 * * * *")
    public void triggerPendingBatchRequests() {
		int batchSize = 300;
		try {
			batchSize = Integer.parseInt(batchSizePerMinute);
		} catch(Exception e) {
			log.warn("분당 AI API호출 건수가 설정되어 있지 않습니다.");
		}
		
		startProcessing(batchSize);
    }

	@Async // 병렬 실행을 강제합니다.
	public void startProcessing(int batchCount) {
	    // 기존 로직을 여기에 넣습니다.
		goodsBatchService.processPendingBatchRequests(batchCount);
	}
    

    // 매일 0시0분 실행 (cron = "초 분 시 일 월 요일")
    @Scheduled(cron = "0 0 0 * * *")
    public void removeOldRecord() {
    	goodsBatchService.removeOldRecord(3);
    }

    // 1분마다 실행 (cron = "초 분 시 일 월 요일")
	@Scheduled(cron = "0 0 * * * *")
    public void triggerMonitoringEventsAlive() {
		goodsBatchService.sendMonitoringEventsAlive();
    }

}