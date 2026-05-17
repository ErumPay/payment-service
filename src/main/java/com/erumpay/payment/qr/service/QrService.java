package com.erumpay.payment.qr.service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.erumpay.payment.core.dao.OrderRepository;
import com.erumpay.payment.core.domain.entity.OrderEntity;
import com.erumpay.payment.qr.dao.QrRepository;
import com.erumpay.payment.qr.domain.dto.QrRequest;
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

        public ResponseEntity<byte[]> createQR(
                        QrRequest request) throws Exception {

                log.info("/qr/request Service");
                System.out.println(request);

                // order entity 생성
                LocalDateTime now = LocalDateTime.now();
                String orderNo = generateUniqueOrderNo(now);
                OrderEntity order = OrderEntity.orderCreate(
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

                String qrURL = "http://localhost:8083/qr?token=" + random;

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

}
