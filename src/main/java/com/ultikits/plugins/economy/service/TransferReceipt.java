package com.ultikits.plugins.economy.service;

/**
 * What a transfer did: whether it happened, and if so how much the receiver was credited and how
 * much transaction tax was taken. The credited amount is computed once, by the transfer itself, so
 * every caller reports the figure that reached the receiver's wallet (UltiKits/UltiEconomy#18).
 */
public final class TransferReceipt {

    private static final TransferReceipt REFUSED = new TransferReceipt(false, 0.0, 0.0, false);
    private static final TransferReceipt CREDIT_FAILED = new TransferReceipt(false, 0.0, 0.0, true);

    private final boolean success;
    private final double credited;
    private final double tax;
    private final boolean creditFailed;

    private TransferReceipt(boolean success, double credited, double tax, boolean creditFailed) {
        this.success = success;
        this.credited = credited;
        this.tax = tax;
        this.creditFailed = creditFailed;
    }

    /** A transfer refused before the sender's debit was written. */
    public static TransferReceipt refused() {
        return REFUSED;
    }

    /** A receiver credit failed after the debit; compensation was attempted separately. */
    static TransferReceipt creditFailed() {
        return CREDIT_FAILED;
    }

    /**
     * Whether the receiver credit failed after the sender was debited. This does not assert whether
     * compensation succeeded or whether a failed storage acknowledgement concealed a committed write.
     * Callers must not describe this failure as insufficient funds.
     */
    public boolean isCreditFailed() {
        return creditFailed;
    }

    /**
     * A transfer that happened.
     *
     * @param credited what the receiver's wallet gained
     * @param tax      the transaction tax taken from the amount sent
     * @return the receipt
     */
    public static TransferReceipt completed(double credited, double tax) {
        return new TransferReceipt(true, credited, tax, false);
    }

    public boolean isSuccess() {
        return success;
    }

    /** What a successful transfer credited; 0 on failure, not proof of the storage outcome. */
    public double getCredited() {
        return credited;
    }

    /** The transaction tax taken; 0 for a refused or untaxed transfer. */
    public double getTax() {
        return tax;
    }
}
