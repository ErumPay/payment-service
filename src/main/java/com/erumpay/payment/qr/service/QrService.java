package com.erumpay.payment.qr.service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.domain.entity.CoreEntity.PaymentStatus;
import com.erumpay.payment.core.domain.entity.EventEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.core.service.CorePgPaymentPersistenceService;
import com.erumpay.payment.merchant.client.dto.MerchantResponse;
import com.erumpay.payment.qr.dao.QrRepository;
import com.erumpay.payment.qr.domain.dto.QrRequest;
import com.erumpay.payment.qr.domain.dto.QrResponse;
import com.erumpay.payment.qr.domain.dto.QrValidateRequest;
import com.erumpay.payment.qr.domain.entity.QrEntity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QrService {
        private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
        private static final int ORDER_RANDOM_DIGITS = 10;

        private final QrRepository qrRepository;
        private final CoreRepository coreRepository;
        private final CorePgPaymentPersistenceService corePgPaymentPersistenceService;
        private final com.erumpay.payment.merchant.client.MerchantClient merchantClient;

        @Value("${spring.qr.baseUrl}")
        private String qrBaseUrl;

        public ResponseEntity<byte[]> createQR(
                        QrRequest request) throws Exception {

                log.info("/qr/request Service");
                log.debug("QR request: {}", request);
                validateQrCreateRequest(request);

                // order entity 생성
                LocalDateTime now = LocalDateTime.now();
                String orderNo = generateUniqueOrderNo(now);
                CoreEntity order = CoreEntity.toEntity(
                                orderNo,
                                request.getOrder_name(),
                                request.getAmount(),
                                request.getMerchant_id(),
                                request.getChannel_type(),
                                now);
                MerchantResponse merchant = merchantClient.merchantInfoRequest(request.getMerchant_id());
                validateMerchantInfo(merchant);
                order.updateMerchantInfo(
                                merchant.getMerchantName(),
                                merchant.getBusinessNumber(),
                                merchant.getOwnerName(),
                                merchant.getContactPhone(),
                                merchant.getBusinessAddress(),
                                merchant.getMccCode(),
                                now);
                CoreEntity savedOrder = coreRepository.save(order);

                corePgPaymentPersistenceService.saveCreatedEvent(savedOrder.getPaymentId(),
                                EventEntity.ActorType.SYSTEM);

                // 토큰 생성
                String random = UUID.randomUUID()
                                .toString()
                                .replace("-", "");

                String qrURL = qrBaseUrl + random;

                log.info(random);

                QRCodeWriter qrCodeWriter = new QRCodeWriter();

                BitMatrix bitMatrix = qrCodeWriter.encode(
                                qrURL,
                                BarcodeFormat.QR_CODE,
                                300,
                                300);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                MatrixToImageWriter.writeToStream(
                                bitMatrix,
                                "PNG",
                                outputStream);

                // qr entity 생성
                QrEntity qrEntity = QrEntity.toEntity(
                                savedOrder,
                                random,
                                now,
                                now.plusMinutes(10));
                qrRepository.save(qrEntity);

                return ResponseEntity
                                .ok()
                                .contentType(MediaType.IMAGE_PNG)
                                .body(outputStream.toByteArray());
        }

        public String generateUniqueOrderNo(LocalDateTime now) {
                String datePart = now.format(ORDER_DATE_FORMAT);
                String prefix = "ORD" + datePart + "EP";

                return Stream.generate(() -> ThreadLocalRandom.current().nextLong(10_000_000_000L))
                                .map(randomNumber -> prefix
                                                + String.format("%0" + ORDER_RANDOM_DIGITS + "d", randomNumber))
                                .filter(orderNo -> !coreRepository.existsByOrderNo(orderNo))
                                .findFirst()
                                .orElseThrow(() -> new CustomException(ErrorCode.QR_ORDER_NO_GENERATION_FAILED));
        }

        public ResponseEntity<QrResponse> validateQR(QrValidateRequest request) {
                log.info("/qr/validate Service");

                if (request == null) {
                        throw new CustomException(ErrorCode.QR_REQUEST_INVALID);
                }

                String token = request.getToken();
                log.info("token={}", token);

                if (token == null || token.isBlank()) {
                        throw new CustomException(ErrorCode.QR_TOKEN_REQUIRED);
                }

                QrEntity qr = qrRepository.findByToken(token)
                                .orElse(null);
                if (qr == null) {
                        throw new CustomException(ErrorCode.QR_NOT_FOUND);
                }

                LocalDateTime now = LocalDateTime.now();
                if (qr.is_used()) {
                        throw new CustomException(ErrorCode.QR_USED);
                }
                // [be] 조보름 260607 0338 | 이미 결제 처리된 QR 재스캔 시 프론트가 카드 선택 화면으로 진입하지 않도록 검증 단계에서 차단합니다.
                if (isProcessedPayment(qr.getOrder().getPayment_status())) {
                        throw new CustomException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
                }
                if (now.isAfter(qr.getExpired_at())) {
                        throw new CustomException(ErrorCode.QR_EXPIRED);
                }

                return ResponseEntity.ok(QrResponse.fromOrderEntity(qr.getOrder(), "VALID"));
        }

        // [be] 조보름 260607 0338 | QR 사용 여부와 별개로 결제 주문 상태가 완료/취소/실패/만료된 경우 재결제 플로우를 막기 위한 상태 판별 함수입니다.
        private boolean isProcessedPayment(PaymentStatus status) {
                return status == PaymentStatus.PAID
                                || status == PaymentStatus.AUTHORIZED
                                || status == PaymentStatus.VOIDED
                                || status == PaymentStatus.CANCELED
                                || status == PaymentStatus.FAILED
                                || status == PaymentStatus.EXPIRED;
        }

        private void validateQrCreateRequest(QrRequest request) {
                if (request == null
                                || request.getMerchant_id() == null
                                || request.getAmount() == null
                                || request.getAmount() <= 0
                                || request.getOrder_name() == null
                                || request.getOrder_name().isBlank()
                                || request.getChannel_type() == null
                                || request.getChannel_type().isBlank()) {
                        throw new CustomException(ErrorCode.QR_REQUEST_INVALID);
                }
        }

        private void validateMerchantInfo(MerchantResponse merchant) {
                if (merchant == null
                                || merchant.getMerchantName() == null
                                || merchant.getMerchantName().isBlank()
                                || merchant.getMccCode() == null
                                || merchant.getMccCode().isBlank()) {
                        throw new CustomException(ErrorCode.MERCHANT_AUTH_UNAVAILABLE);
                }
        }
}
