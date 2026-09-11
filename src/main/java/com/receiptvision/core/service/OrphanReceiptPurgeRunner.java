package com.receiptvision.core.service;

import com.receiptvision.core.repository.ReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Privacy migration: receipts created before login existed have {@code owner = NULL}
 * and therefore no verifiable owner. Under the strict privacy rule ("nobody except
 * the uploader") they must never be served to anyone, so they are hard-deleted once
 * at startup. The count is logged, never the content.
 */
@Component
public class OrphanReceiptPurgeRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrphanReceiptPurgeRunner.class);

    private final ReceiptRepository receipts;

    public OrphanReceiptPurgeRunner(ReceiptRepository receipts) {
        this.receipts = receipts;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int deleted = receipts.deleteOrphans();
        if (deleted > 0) {
            log.warn("Privacy purge: deleted {} ownerless legacy receipt(s). They had no verifiable owner.", deleted);
        } else {
            log.info("Privacy purge: no ownerless receipts found.");
        }
    }
}
