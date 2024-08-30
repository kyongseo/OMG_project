package com.example.omg_project.domain.notification.service.redis;

import com.example.omg_project.domain.notification.entity.Notification;
import com.example.omg_project.global.exception.CustomException;
import com.example.omg_project.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Redis에서 수신한 알림 메시지를 처리하고, WebSocket을 통해 알림을 전송하는 컴포넌트.
 * - Redis 메시지 리스너로 구현되어 있으며, Redis 채널로부터 메시지를 수신하여 처리합니다.
 */
@Component
@RequiredArgsConstructor
public class NotificationSubscriber implements MessageListener {

    // WebSocket 메시지를 전송하기 위한 SimpMessagingTemplate 인스턴스
    private final SimpMessagingTemplate messagingTemplate;

    // Redis 메시지를 Notification 객체로 변환하기 위한 ObjectMapper 인스턴스
    private final ObjectMapper objectMapper;

    /**
     * Redis로부터 수신한 메시지를 처리하는 메서드.
     * - 메시지를 Notification 객체로 변환한 후, WebSocket을 통해 해당 알림을 전송합니다.
     * - 메시지 변환이나 전송 중 오류가 발생하면 공통 예외를 발생시킵니다.
     *
     * @param message Redis에서 수신한 메시지 객체
     * @param pattern Redis 채널 패턴 (사용되지 않음)
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // Redis에서 받은 바이트 배열을 Notification 객체로 변환합니다.
            Notification notification = (Notification) deserialize(message.getBody());

            // 변환된 Notification 객체가 null이 아닌 경우에만 처리합니다.
            if (notification != null) {
                // 알림 타입을 기반으로 WebSocket 경로를 구성하고, 해당 경로로 알림을 전송합니다.
                String notificationType = notification.getNotificationType();
                messagingTemplate.convertAndSend("/topic/notifications/" + notificationType + "/" + notification.getUserId(), notification);
            } else {
                // Notification 객체가 null인 경우, 적절한 로깅 처리 및 후속 조치를 수행합니다.
                // 현재는 추가 로깅 및 알림 처리가 필요할 수 있습니다.
            }
        } catch (Exception e) {
            // 메시지 처리 중 오류가 발생하면 공통 예외를 발생시킵니다.
            throw new CustomException(ErrorCode.NOTIFICATION_PROCESSING_ERROR);
        }
    }

    /**
     * 바이트 배열을 Notification 객체로 변환하는 메서드.
     * - JSON 형식의 바이트 배열을 Notification 클래스의 인스턴스로 변환합니다.
     * - 변환 중 오류가 발생하면 CustomException을 발생시킵니다.
     *
     * @param bytes JSON 형식의 바이트 배열
     * @return 변환된 Notification 객체
     */
    private Object deserialize(byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, Notification.class);
        } catch (IOException e) {
            // JSON 파싱 중 오류가 발생하면 CustomException을 발생시킵니다.
            throw new CustomException(ErrorCode.DESERIALIZATION_ERROR);
        }
    }
}