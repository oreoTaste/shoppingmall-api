package com.tikitaka.api.batch.goods;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180Parser;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.tikitaka.api.batch.goods.dto.GoodsBatchDto;
import com.tikitaka.api.batch.goods.entity.GoodsBatchRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.BOMInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsBatchService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    private final GoodsBatchRequestRepository goodsBatchRequestRepository;

    @Async
    public void processGoodsInspectionBatch(MultipartFile zipFile) {
        String batchJobId = UUID.randomUUID().toString();
        Path permanentDir = Paths.get(uploadDir, "batch", batchJobId).toAbsolutePath().normalize();
        log.info("========== 상품 배치 처리 시작: Job ID {} ==========", batchJobId);

        try {
            Files.createDirectories(permanentDir);
            log.info("1. 영구 저장 디렉토리 생성 완료: {}", permanentDir);

            unzip(zipFile.getInputStream(), permanentDir);
            log.info("2. ZIP 파일 압축 해제 완료.");

            File csvFile = findCsvFile(permanentDir);
            log.info("3. CSV 파일 찾기 완료: {}", csvFile.getAbsolutePath());

            // [핵심] 가장 안정적인 RFC4180Parser를 사용하는 파싱 메소드 호출
            List<GoodsBatchDto> goodsDtoList = parseCsvToDto(csvFile);
            log.info("4. CSV 파싱 완료. 총 {}개의 상품 데이터 발견.", goodsDtoList.size());

            if (goodsDtoList.isEmpty()) {
                log.warn("경고: CSV 파일에서 상품 데이터를 읽어오지 못했습니다.");
                return;
            }

            List<GoodsBatchRequest> requestEntities = new ArrayList<>();
            for (GoodsBatchDto dto : goodsDtoList) {
                // [핵심] 안전한 숫자 변환 로직
                BigDecimal salesPrice = BigDecimal.ZERO;
                BigDecimal buyPrice = BigDecimal.ZERO;
                try {
                    if (StringUtils.hasText(dto.getSalesPrice())) {
                        salesPrice = new BigDecimal(dto.getSalesPrice());
                    }
                    if (StringUtils.hasText(dto.getBuyPrice())) {
                        buyPrice = new BigDecimal(dto.getBuyPrice());
                    }
                } catch (NumberFormatException e) {
                    log.error("숫자 변환 실패! 상품명: '{}', salesPrice: '{}', buyPrice: '{}'. 가격을 0으로 처리합니다.",
                            dto.getGoodsName(), dto.getSalesPrice(), dto.getBuyPrice());
                }
                
                String repFilePath = permanentDir.resolve("images/" + dto.getRepresentativeFile()).toString();

                String imageFilePaths = "";
                if (dto.getImageFiles() != null && !dto.getImageFiles().isEmpty()) {
                    imageFilePaths = Arrays.stream(dto.getImageFiles().split(","))
                            .map(fileName -> permanentDir.resolve("images/" + fileName.trim()).toString())
                            .collect(Collectors.joining(","));
                }

                GoodsBatchRequest requestEntity = GoodsBatchRequest.builder()
                        .batchJobId(batchJobId)
                        .goodsName(dto.getGoodsName())
                        .mobileGoodsName(dto.getMobileGoodsName())
                        .salesPrice(salesPrice)
                        .buyPrice(buyPrice)
                        .origin(dto.getOrigin())
                        .imageHtml(dto.getImageHtml())
                        .representativeFilePath(repFilePath)
                        .imageFilesPaths(imageFilePaths)
                        .lgroup(dto.getLgroup())
                        .lgroupName(dto.getLgroupName())
                        .mgroup(dto.getMgroup())
                        .mgroupName(dto.getMgroupName())
                        .sgroup(dto.getSgroup())
                        .sgroupName(dto.getSgroupName())
                        .dgroup(dto.getDgroup())
                        .dgroupName(dto.getDgroupName())
                        .build();

                requestEntities.add(requestEntity);
            }

            goodsBatchRequestRepository.saveAll(requestEntities);
            log.info("5. 총 {}건의 상품 검수 요청을 DB에 성공적으로 저장했습니다.", requestEntities.size());

        } catch (Exception e) {
            log.error("!! 상품 배치 처리 중 심각한 오류 발생 (Job ID: {}) !!", batchJobId, e);
        } finally {
            log.info("========== 상품 배치 처리 종료: Job ID {} ==========", batchJobId);
        }
    }

    /**
     * [최종 수정본] 이전에 성공했던 가장 안정적인 파싱 로직을 적용한 메소드
     */
    private List<GoodsBatchDto> parseCsvToDto(File csvFile) throws IOException {
        // 1. CSV 표준(RFC 4180)을 따르는 파서를 생성합니다.
        RFC4180Parser rfc4180Parser = new RFC4180ParserBuilder().build();

        // 2. 인코딩을 "MS949"로 지정하고 BOM을 처리하며 파일을 읽습니다.
        try (InputStreamReader reader = new InputStreamReader(
                new BOMInputStream(new FileInputStream(csvFile)), "MS949")) {

            // 3. 위에서 만든 표준 파서를 사용하여 CSV 리더(Reader)를 생성합니다.
            CSVReader csvReader = new CSVReaderBuilder(reader)
                    .withCSVParser(rfc4180Parser)
                    .build();

            // 4. DTO의 헤더 이름과 CSV의 헤더를 매핑하는 전략을 설정합니다.
            HeaderColumnNameMappingStrategy<GoodsBatchDto> strategy = new HeaderColumnNameMappingStrategy<>();
            strategy.setType(GoodsBatchDto.class);

            // 5. CsvToBeanBuilder에 직접 만든 CSV 리더와 매핑 전략을 제공하여 최종 변환합니다.
            return new CsvToBeanBuilder<GoodsBatchDto>(csvReader)
                    .withMappingStrategy(strategy)
                    .build()
                    .parse();
        }
    }

    private void unzip(InputStream inputStream, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(inputStream))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                Path newPath = targetDir.resolve(zipEntry.getName());
                if (!newPath.normalize().startsWith(targetDir.normalize())) {
                    throw new IOException("ZIP 파일에 올바르지 않은 경로가 포함되어 있습니다: " + zipEntry.getName());
                }
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    if (newPath.getParent() != null) {
                        Files.createDirectories(newPath.getParent());
                    }
                    Files.copy(zis, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private File findCsvFile(Path dir) throws FileNotFoundException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.csv")) {
            for (Path path : stream) {
                return path.toFile();
            }
        } catch (IOException e) {
            throw new FileNotFoundException("CSV 파일을 찾는 중 오류가 발생했습니다.");
        }
        throw new FileNotFoundException("디렉토리에서 CSV 파일을 찾을 수 없습니다: " + dir);
    }
}