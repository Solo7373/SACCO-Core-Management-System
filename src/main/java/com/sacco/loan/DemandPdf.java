package com.sacco.loan;

/**
 * A rendered demand notice ready to be streamed to the browser.
 */
public record DemandPdf(String fileName, byte[] content) {
}
