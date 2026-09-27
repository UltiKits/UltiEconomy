package com.ultikits.plugins.economy.service;

/**
 * What a transfer did: whether it happened, and if so how much the receiver was credited and how
 * much transaction tax was taken. The credited amount is computed once, by the transfer itself, so
 * every caller reports the figure that reached the receiver's wallet (UltiKits/UltiEconomy#18).
 */
public final class TransferReceipt {

    private static final TransferReceipt REFUSED = new TransferReceipt(false, 0.0, 0.0);

    private final boolean success;
    private final double credited;
    private final double tax;

    private TransferReceipt(boolean success, double credited, double tax) {
        this.success = success;
        this.credited = credited;
        this.tax = tax;
    }

    /** A transfer that did not happen: nothing was taken or credited. */
    public static TransferReceipt refused() {
        return REFUSED;
    }

    /**
     * A transfer that happened.
     *
     * @param credited what the receiver's wallet gained
     * @param tax      the transaction tax taken from the amount sent
     * @return the receipt
     */
    public static TransferReceipt completed(double credited, double tax) {
        return new TransferReceipt(true, credited, tax);
    }

    public boolean isSuccess() {
        return success;
    }

    /** What the receiver's wallet gained; 0 for a refused transfer. */
    public double getCredited() {
        return credited;
    }

    /** The transaction tax taken; 0 for a refused or untaxed transfer. */
    public double getTax() {
        return tax;
    }
}
