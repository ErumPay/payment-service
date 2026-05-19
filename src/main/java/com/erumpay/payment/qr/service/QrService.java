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

import com.erumpay.payment.core.dao.OrderRepository;
import com.erumpay.payment.core.domain.entity.OrderEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
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
        private final OrderRepository orderRepository;

        @Value("${spring.qr.baseUrl}")
        private String qrBaseUrl;

        public ResponseEntity<byte[]> createQR(
                        QrRequest request) throws Exception {

                log.info("/qr/request Service");
                log.debug("QR request: {}", request);

                // order entity 생성
                LocalDateTime now = LocalDateTime.now();
                String orderNo = generateUniqueOrderNo(now);
                OrderEntity order = OrderEntity.toEntity(
                                orderNo,
                                request.getOrder_name(),
                                request.getAmount(),
                                request.getMerchant_id(),
                                request.getChannel_type(),
                                now);
                OrderEntity savedOrder = orderRepository.save(order);

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

        private String generateUniqueOrderNo(LocalDateTime now) {
                String datePart = now.format(ORDER_DATE_FORMAT);
                String prefix = "ORD" + datePart + "EP";

                return Stream.generate(() -> ThreadLocalRandom.current().nextLong(10_000_000_000L))
                                .map(randomNumber -> prefix
                                                + String.format("%0" + ORDER_RANDOM_DIGITS + "d", randomNumber))
                                .filter(orderNo -> !orderRepository.existsByOrderNo(orderNo))
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException("Failed to generate unique order_no"));
        }

        public ResponseEntity<QrResponse> validateQR(QrValidateRequest request) {
                log.info("/qr/validate Service");

                String token = request.getToken();
                log.info("token={}", token);

                if (token == null || token.isBlank()) {
                        throw new CustomException(ErrorCode.QR_INVALID);
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
                if (now.isAfter(qr.getExpired_at())) {
                        throw new CustomException(ErrorCode.QR_EXPIRED);
                }

                return ResponseEntity.ok(QrResponse.fromOrderEntity(qr.getOrder(), "VALID"));
        }
}
