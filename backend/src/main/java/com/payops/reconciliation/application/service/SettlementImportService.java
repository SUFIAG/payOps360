package com.payops.reconciliation.application.service;

import com.payops.audit.application.port.in.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.payment.domain.PaymentMethod;
import com.payops.reconciliation.application.port.in.BatchView;
import com.payops.reconciliation.application.port.in.FeeScheduleUseCase;
import com.payops.reconciliation.application.port.in.SettlementImportUseCase;
import com.payops.reconciliation.application.port.out.LedgerPort;
import com.payops.reconciliation.application.port.out.MatchingQueue;
import com.payops.reconciliation.application.port.out.ReconciliationRepository;
import com.payops.reconciliation.domain.FeeSchedule;
import com.payops.reconciliation.domain.MappingProfile;
import com.payops.reconciliation.domain.SettlementLineType;
import com.payops.reconciliation.domain.SettlementParser;
import com.payops.reconciliation.domain.SettlementRecord;
import com.payops.shared.application.Actor;
import com.payops.shared.application.RequestContext;
import com.payops.shared.domain.ConflictException;
import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.NotFoundException;
import com.payops.shared.domain.References;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Settlement intake. Files are parsed as a stream and inserted in chunks, so
 * memory use is independent of file size; the SHA-256 of the raw bytes is
 * computed on the fly and rejects re-uploads. Matching runs asynchronously
 * once the import transaction commits.
 */
@Service
@RequiredArgsConstructor
public class SettlementImportService implements SettlementImportUseCase, FeeScheduleUseCase {

    private static final int INSERT_CHUNK = 1_000;
    private static final int MAX_PARSE_ERRORS = 20;
    private static final int MAX_FEE_RULES = 50;
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final ReconciliationRepository repository;
    private final LedgerPort ledger;
    private final MatchingQueue matchingQueue;
    private final AuditTrail auditTrail;
    private final ReconciliationSettings settings;
    private final Clock clock;
    private final Random random = new SecureRandom();

    // ------------------------------------------------------------------ import

    @Override
    @Transactional
    public BatchView importSettlement(Actor actor, String providerCode, String fileName, InputStream content,
                                      RequestContext context) {
        long orgId = actor.organizationId();
        LedgerPort.ProviderRef provider = provider(orgId, providerCode);
        MappingProfile profile = profile(orgId, provider.id());
        Instant now = clock.instant();
        String reference = References.next("STL");
        long batchId = repository.createBatch(orgId, provider.id(), reference, safeFileName(fileName),
                "pending:" + reference, actor.email(), now);

        MessageDigest digest = sha256();
        int total = 0;
        int rejected = 0;
        List<String> errors = new ArrayList<>();
        List<SettlementRecord> chunk = new ArrayList<>(INSERT_CHUNK);
        try (Reader reader = new BufferedReader(new InputStreamReader(new DigestInputStream(content, digest),
                StandardCharsets.UTF_8))) {
            SettlementParser parser = new SettlementParser(profile, reader);
            for (SettlementParser.Row row = parser.next(); row != null; row = parser.next()) {
                if (++total > settings.maxLinesPerFile()) {
                    throw new DomainException("SETTLEMENT_FILE_TOO_LARGE",
                            "A settlement file may contain at most " + settings.maxLinesPerFile() + " lines");
                }
                if (row instanceof SettlementParser.Parsed parsed) {
                    chunk.add(parsed.record());
                    if (chunk.size() >= INSERT_CHUNK) {
                        repository.insertLines(orgId, batchId, chunk);
                        chunk.clear();
                    }
                } else if (row instanceof SettlementParser.Rejected rejectedRow) {
                    rejected++;
                    if (errors.size() < MAX_PARSE_ERRORS) {
                        errors.add("Line " + rejectedRow.lineNo() + ": " + rejectedRow.reason());
                    }
                }
            }
        } catch (IOException | UncheckedIOException e) {
            throw new DomainException("INVALID_SETTLEMENT_FILE", "The file could not be read");
        }
        if (!chunk.isEmpty()) {
            repository.insertLines(orgId, batchId, chunk);
        }
        if (total == 0) {
            throw new DomainException("INVALID_SETTLEMENT_FILE", "The file has a header but no settlement lines");
        }
        if (total == rejected) {
            throw new DomainException("INVALID_SETTLEMENT_FILE",
                    "No line could be parsed. " + (errors.isEmpty() ? "" : errors.get(0)));
        }
        String checksum = HexFormat.of().formatHex(digest.digest());
        if (repository.checksumExists(orgId, checksum)) {
            throw new ConflictException("DUPLICATE_SETTLEMENT_FILE", "This settlement file has already been imported");
        }
        repository.finishImport(batchId, checksum, total, rejected, errors);

        auditTrail.record(new AuditEntry(orgId, actor.userId(), actor.email(), AuditAction.SETTLEMENT_IMPORTED,
                "SETTLEMENT_BATCH", reference, AuditOutcome.SUCCESS, context.ipAddress(), context.userAgent(),
                Map.of("provider", provider.code(), "lines", String.valueOf(total), "rejected", String.valueOf(rejected),
                        "checksum", checksum), now));
        matchingQueue.submit(orgId, batchId);
        return repository.batch(orgId, batchId).orElseThrow();
    }

    // ------------------------------------------------------------------ sample generator

    @Override
    @Transactional(readOnly = true)
    public SampleFile sampleFile(Actor actor, String providerCode) {
        long orgId = actor.organizationId();
        LedgerPort.ProviderRef provider = provider(orgId, providerCode);
        MappingProfile profile = profile(orgId, provider.id());
        FeeSchedule fees = feeScheduleFor(orgId, provider);
        Instant now = clock.instant();
        List<LedgerPort.LedgerPayment> payments = new ArrayList<>(
                ledger.recentSettleable(orgId, provider.id(), now.minus(Duration.ofDays(7)), 150));
        if (payments.isEmpty()) {
            throw new DomainException("NO_PAYMENTS_TO_SETTLE",
                    "Provider " + provider.code() + " has no unsettled succeeded payments in the last 7 days");
        }
        payments.sort(Comparator.comparing(LedgerPort.LedgerPayment::createdAt));

        // Pick the deliberately broken rows from the middle, so the batch's coverage window spans them.
        List<Integer> middle = new ArrayList<>();
        for (int i = 1; i < payments.size() - 1; i++) {
            middle.add(i);
        }
        Collections.shuffle(middle, random);
        int cursor = 0;
        Set<Integer> missing = take(middle, cursor, payments.size() >= 12 ? 3 : 0);
        cursor += missing.size();
        Set<Integer> amountDiff = take(middle, cursor, payments.size() >= 6 ? 2 : 0);
        cursor += amountDiff.size();
        Set<Integer> feeOver = take(middle, cursor, payments.size() >= 8 ? 2 : 0);
        cursor += feeOver.size();
        Set<Integer> fuzzy = take(middle, cursor, payments.size() >= 10 ? 1 : 0);
        cursor += fuzzy.size();
        Set<Integer> duplicate = take(middle, cursor, payments.size() >= 4 ? 1 : 0);
        cursor += duplicate.size();
        Set<Integer> refund = take(middle, cursor, payments.size() >= 15 ? 1 : 0);

        List<Map<MappingProfile.Field, String>> rows = new ArrayList<>();
        for (int i = 0; i < payments.size(); i++) {
            if (missing.contains(i)) {
                continue;
            }
            LedgerPort.LedgerPayment p = payments.get(i);
            Instant settledAt = min(p.createdAt().plus(Duration.ofDays(1)), now);
            BigDecimal amount = money(p.amount());
            String reference = p.reference();
            if (amountDiff.contains(i)) {
                amount = money(amount.subtract(BigDecimal.valueOf(50 + random.nextInt(2450), 2)).max(BigDecimal.ONE));
            }
            BigDecimal fee = fees.expectedFee(p.method(), p.currency(), amount).orElse(BigDecimal.ZERO);
            if (feeOver.contains(i)) {
                fee = money(fee.multiply(new BigDecimal("1.4")).add(new BigDecimal("0.75")));
            }
            if (fuzzy.contains(i)) {
                reference = "TXN-" + (1_000_000_000L + Math.abs(random.nextLong() % 8_999_999_999L));
            }
            Map<MappingProfile.Field, String> row = row(SettlementLineType.SALE, reference, amount, p.currency(), fee,
                    settledAt, profile);
            rows.add(row);
            if (duplicate.contains(i)) {
                rows.add(row);
            }
            if (refund.contains(i)) {
                rows.add(row(SettlementLineType.REFUND, p.reference(),
                        money(amount.multiply(new BigDecimal("0.3")).negate()), p.currency(), BigDecimal.ZERO,
                        min(settledAt.plus(Duration.ofDays(1)), now), profile));
            }
        }
        String currency = payments.get(0).currency();
        for (int i = 0; i < 2; i++) {
            BigDecimal unknownAmount = BigDecimal.valueOf(5_000 + random.nextInt(90_000), 2);
            rows.add(row(SettlementLineType.SALE, References.next("PAY"), unknownAmount, currency,
                    fees.expectedFee(null, currency, unknownAmount).orElse(BigDecimal.ZERO), now.minus(Duration.ofHours(2)),
                    profile));
        }
        rows.add(row(SettlementLineType.FEE, "MONTHLY-FEE-" + FILE_STAMP.format(now).substring(0, 6),
                new BigDecimal("-25.00"), currency, new BigDecimal("25.00"), now, profile));
        Collections.shuffle(rows, random);

        StringBuilder csv = new StringBuilder();
        List<MappingProfile.Field> fields = profile.orderedFields();
        csv.append(String.join(String.valueOf(profile.delimiter()),
                fields.stream().map(f -> escape(profile.columns().get(f), profile.delimiter())).toList())).append("\r\n");
        for (Map<MappingProfile.Field, String> row : rows) {
            csv.append(String.join(String.valueOf(profile.delimiter()),
                    fields.stream().map(f -> escape(row.getOrDefault(f, ""), profile.delimiter())).toList())).append("\r\n");
        }
        return new SampleFile("settlement-" + provider.code() + "-" + FILE_STAMP.format(now) + ".csv", csv.toString());
    }

    private Map<MappingProfile.Field, String> row(SettlementLineType type, String reference, BigDecimal amount,
                                                  String currency, BigDecimal fee, Instant settledAt,
                                                  MappingProfile profile) {
        Map<MappingProfile.Field, String> row = new EnumMap<>(MappingProfile.Field.class);
        row.put(MappingProfile.Field.TYPE, type.name());
        row.put(MappingProfile.Field.REFERENCE, reference);
        row.put(MappingProfile.Field.AMOUNT, money(amount).toPlainString());
        row.put(MappingProfile.Field.CURRENCY, currency);
        row.put(MappingProfile.Field.FEE, money(fee).toPlainString());
        row.put(MappingProfile.Field.FX_RATE, "");
        row.put(MappingProfile.Field.SETTLED_AT, profile.dateFormat() == null ? settledAt.toString()
                : DateTimeFormatter.ofPattern(profile.dateFormat(), Locale.ROOT).withZone(ZoneOffset.UTC).format(settledAt));
        return row;
    }

    private static Set<Integer> take(List<Integer> source, int from, int count) {
        Set<Integer> out = new HashSet<>();
        for (int i = from; i < Math.min(source.size(), from + count); i++) {
            out.add(source.get(i));
        }
        return out;
    }

    private static String escape(String value, char delimiter) {
        if (value.indexOf(delimiter) >= 0 || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ mapping

    @Override
    @Transactional(readOnly = true)
    public MappingView mapping(long organizationId, String providerCode) {
        LedgerPort.ProviderRef provider = provider(organizationId, providerCode);
        return repository.mapping(organizationId, provider.id())
                .map(m -> mappingView(provider, m.profile(), false, m.updatedBy(), m.updatedAt()))
                .orElseGet(() -> mappingView(provider, MappingProfile.defaults(), true, null, null));
    }

    @Override
    @Transactional
    public MappingView updateMapping(Actor actor, String providerCode, MappingCommand command, RequestContext context) {
        LedgerPort.ProviderRef provider = provider(actor.organizationId(), providerCode);
        MappingProfile profile = new MappingProfile(delimiter(command.delimiter()), command.dateFormat(), command.columns());
        if (profile.dateFormat() != null) {
            try {
                DateTimeFormatter.ofPattern(profile.dateFormat(), Locale.ROOT);
            } catch (IllegalArgumentException e) {
                throw new DomainException("INVALID_MAPPING", "Invalid date format pattern");
            }
        }
        Instant now = clock.instant();
        repository.saveMapping(actor.organizationId(), provider.id(), profile, actor.email(), now);
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(),
                AuditAction.SETTLEMENT_MAPPING_CHANGED, "PROVIDER", provider.code(), AuditOutcome.SUCCESS,
                context.ipAddress(), context.userAgent(), Map.of("columns", profile.columns().toString()), now));
        return mappingView(provider, profile, false, actor.email(), now);
    }

    private static char delimiter(String raw) {
        if (raw == null || raw.isEmpty()) {
            return ',';
        }
        if (raw.equals("\\t") || raw.equalsIgnoreCase("tab") || raw.equals("\t")) {
            return '\t';
        }
        if (raw.length() != 1) {
            throw new DomainException("INVALID_MAPPING", "Delimiter must be a single character");
        }
        return raw.charAt(0);
    }

    private static MappingView mappingView(LedgerPort.ProviderRef provider, MappingProfile profile, boolean isDefault,
                                           String updatedBy, Instant updatedAt) {
        return new MappingView(provider.code(), profile.delimiter() == '\t' ? "\\t" : String.valueOf(profile.delimiter()),
                profile.dateFormat(), profile.columns(), isDefault, updatedBy, updatedAt);
    }

    private MappingProfile profile(long organizationId, long providerId) {
        return repository.mapping(organizationId, providerId).map(ReconciliationRepository.StoredMapping::profile)
                .orElseGet(MappingProfile::defaults);
    }

    // ------------------------------------------------------------------ fee schedule

    @Override
    @Transactional(readOnly = true)
    public FeeScheduleView feeSchedule(long organizationId, String providerCode) {
        LedgerPort.ProviderRef provider = provider(organizationId, providerCode);
        var stored = repository.feeSchedule(organizationId, provider.id());
        return new FeeScheduleView(provider.code(), provider.name(), fallbackPercentage(provider),
                stored.map(s -> s.rules().stream().map(SettlementImportService::ruleView).toList()).orElse(List.of()),
                stored.map(ReconciliationRepository.StoredFeeSchedule::updatedBy).orElse(null),
                stored.map(ReconciliationRepository.StoredFeeSchedule::updatedAt).orElse(null));
    }

    @Override
    @Transactional
    public FeeScheduleView replaceFeeSchedule(Actor actor, String providerCode, List<FeeRuleView> rules,
                                              RequestContext context) {
        LedgerPort.ProviderRef provider = provider(actor.organizationId(), providerCode);
        if (rules.size() > MAX_FEE_RULES) {
            throw new DomainException("INVALID_FEE_SCHEDULE", "At most " + MAX_FEE_RULES + " fee rules per provider");
        }
        List<FeeSchedule.Rule> domainRules = new ArrayList<>();
        try {
            for (FeeRuleView r : rules) {
                if (r.method() != null && !r.method().isBlank()) {
                    PaymentMethod.valueOf(r.method());
                }
                domainRules.add(new FeeSchedule.Rule(r.method(), r.currency(), r.percentage(), r.fixedFee()));
            }
            new FeeSchedule(domainRules, null);
        } catch (IllegalArgumentException e) {
            throw new DomainException("INVALID_FEE_SCHEDULE", e.getMessage() == null ? "Invalid fee rule" : e.getMessage());
        }
        Instant now = clock.instant();
        repository.replaceFeeSchedule(actor.organizationId(), provider.id(), domainRules, actor.email(), now);
        auditTrail.record(new AuditEntry(actor.organizationId(), actor.userId(), actor.email(),
                AuditAction.FEE_SCHEDULE_CHANGED, "PROVIDER", provider.code(), AuditOutcome.SUCCESS,
                context.ipAddress(), context.userAgent(), Map.of("rules", String.valueOf(domainRules.size())), now));
        return feeSchedule(actor.organizationId(), providerCode);
    }

    private static FeeRuleView ruleView(FeeSchedule.Rule r) {
        return new FeeRuleView(r.method(), r.currency(), r.percentage(), r.fixedFee());
    }

    FeeSchedule feeScheduleFor(long organizationId, LedgerPort.ProviderRef provider) {
        return new FeeSchedule(repository.feeSchedule(organizationId, provider.id())
                .map(ReconciliationRepository.StoredFeeSchedule::rules).orElse(List.of()), fallbackPercentage(provider));
    }

    static BigDecimal fallbackPercentage(LedgerPort.ProviderRef provider) {
        return provider.costBps() == null ? null
                : provider.costBps().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ helpers

    private LedgerPort.ProviderRef provider(long organizationId, String providerCode) {
        return ledger.provider(organizationId, providerCode).orElseThrow(() -> new NotFoundException("Provider", providerCode));
    }

    private static String safeFileName(String fileName) {
        String name = fileName == null || fileName.isBlank() ? "settlement.csv" : fileName;
        name = name.replaceAll(".*[/\\\\]", "").replaceAll("[^A-Za-z0-9._ -]", "_");
        return name.length() > 200 ? name.substring(0, 200) : name;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
