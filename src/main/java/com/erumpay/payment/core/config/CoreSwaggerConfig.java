package com.erumpay.payment.core.config;

import java.util.List;
import java.util.Objects;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class CoreSwaggerConfig {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Value("${app.swagger.gateway-url:http://localhost:8080/payment-service}")
    private String gatewayUrl;

    @Value("${app.swagger.direct-url:http://localhost:8083}")
    private String directUrl;

    @Bean
    public OpenAPI coreOpenAPI() {
        String schemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("ERUM Pay Core API")
                        .description("결제 Core API 문서")
                        .version("v1"))
                .servers(List.of(
                        new Server().url(gatewayUrl).description("통합 UI(:8080)에서 사용"),
                        new Server().url(directUrl).description("단독 UI(:8083)에서 사용")
                ))
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }

    @Bean
    public GroupedOpenApi coreGroupedOpenApi() {
        return GroupedOpenApi.builder()
                .group("core-payment")
                .pathsToMatch("/api/v1/payment/**", "/internal/v1/payments/**")
                .addOpenApiCustomizer(openApi -> {
                    if (openApi.getPaths() == null) {
                        return;
                    }

                    openApi.getPaths().forEach((path, pathItem) -> {
                        boolean isCorePaymentApi = path.startsWith("/api/v1/payment/");
                        boolean isQrApi = path.startsWith("/api/v1/payment/qr/");
                        pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                            if (isCorePaymentApi && !isQrApi) {
                                addUserIdHeader(operation);

                                if (PathItem.HttpMethod.POST.equals(httpMethod)) {
                                    addIdempotencyKeyHeader(operation);
                                }
                            }
                        });
                    });
                })
                .build();
    }

    private void addUserIdHeader(io.swagger.v3.oas.models.Operation operation) {
        boolean alreadyExists = operation.getParameters() != null
                && operation.getParameters().stream()
                        .anyMatch(parameter -> USER_ID_HEADER.equalsIgnoreCase(parameter.getName()));
        if (alreadyExists) {
            return;
        }

        operation.addParametersItem(new Parameter()
                .in("header")
                .name(USER_ID_HEADER)
                .required(true)
                .description("요청 사용자 ID")
                .schema(new IntegerSchema().format("int64")));
    }

    private void addIdempotencyKeyHeader(io.swagger.v3.oas.models.Operation operation) {
        boolean alreadyExists = operation.getParameters() != null
                && operation.getParameters().stream()
                        .filter(Objects::nonNull)
                        .anyMatch(parameter -> IDEMPOTENCY_KEY_HEADER.equalsIgnoreCase(parameter.getName()));
        if (alreadyExists) {
            return;
        }

        operation.addParametersItem(new Parameter()
                .in("header")
                .name(IDEMPOTENCY_KEY_HEADER)
                .required(true)
                .description("멱등성 키")
                .schema(new StringSchema()));
    }
}
