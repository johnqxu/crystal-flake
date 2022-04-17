package com.github.johnqxu.crystalflake;

/**
 * Runtime exception of {@link FlakeGenerator}
 *
 * @author 徐青
 */
public class FlakeGeneratorException extends RuntimeException {

    public FlakeGeneratorException(String message) {
        super(message);
    }

}
