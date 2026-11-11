package com.payops.reconciliation.application.port.input;

import com.payops.reconciliation.domain.MappingProfile;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;

/**
 * Settlement file intake: streaming CSV import, per-provider column mapping
 * and a sample-file generator for trying the flow without real files.
 */
public interface SettlementImportUseCase {

    /**
     * Streams and stores the file's lines, then queues matching asynchronously.
     * Rejects a file whose checksum was already imported by the organization.
     */
    BatchView importSettlement(Actor actor, String providerCode, String fileName, InputStream content,
                               RequestContext context);

    /** CSV built from recent payments of the provider, with deliberate mismatches. */
    SampleFile sampleFile(Actor actor, String providerCode);

    MappingView mapping(long organizationId, String providerCode);

    MappingView updateMapping(Actor actor, String providerCode, MappingCommand command, RequestContext context);

    record SampleFile(String fileName, String content) {
    }

    record MappingView(String providerCode, String delimiter, String dateFormat,
                       Map<MappingProfile.Field, String> columns, boolean defaultProfile, String updatedBy,
                       Instant updatedAt) {
    }

    record MappingCommand(String delimiter, String dateFormat, Map<MappingProfile.Field, String> columns) {
    }
}
