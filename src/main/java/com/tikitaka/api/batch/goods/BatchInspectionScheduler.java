package com.tikitaka.api.batch.goods;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class BatchInspectionScheduler {

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
            	boolean gatherResult = goodsBatchService.gatherS3Data(todayDate);
        		goodsBatchService.recordBatchInStatus(gatherResult ? "SUCCESS" : "FAILED");
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
		startProcessing(300);
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