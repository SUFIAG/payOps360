package com.payops.shared.domain;

/**
 * Framework-free pagination request used by input and output ports.
 */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            page = 0;
        }
        if (size < 1) {
            size = 20;
        }
        size = Math.min(size, MAX_SIZE);
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size);
    }
}
