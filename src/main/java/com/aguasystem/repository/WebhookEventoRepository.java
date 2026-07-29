package com.aguasystem.repository;

import com.aguasystem.entity.WebhookEvento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventoRepository extends JpaRepository<WebhookEvento, Long> {
    boolean existsByEventId(String eventId);
}
