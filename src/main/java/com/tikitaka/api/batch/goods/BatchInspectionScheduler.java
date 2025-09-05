package com.tikitaka.api.batch.goods;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class BatchInspectionScheduler {

    private final GoodsBatchService goodsBatchService;
    
    // 매일 0시0분 실행 (cron = "초 분 시 일 월 요일")
    @Scheduled(cron = "0 */2 * * * *")
    public void gatherS3Data() {
        log.info("========== S3 데이터 수집 스케줄러 시작 ==========");
        try {
        	goodsBatchService.gatherS3Data();
        } catch (Exception e) {
            log.error("S3 데이터 수집 중 오류 발생", e);
        } finally {
            log.info("========== S3 데이터 수집 스케줄러 종료 ==========");
        }
    }

    
    // 5분마다 실행 (cron = "초 분 시 일 월 요일")
	@Scheduled(cron = "0 */2 * * * *")
    public void triggerPendingBatchRequests() {
		goodsBatchService.processPendingBatchRequests(100);
    }
    
    

    // 매일 0시0분 실행 (cron = "초 분 시 일 월 요일")
    @Scheduled(cron = "0 0 0 * * *")
    public void removeOldRecord() {
    	goodsBatchService.removeOldRecord(3);
    }
    
}